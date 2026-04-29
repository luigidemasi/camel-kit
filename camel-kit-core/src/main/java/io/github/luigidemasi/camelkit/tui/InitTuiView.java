package io.github.luigidemasi.camelkit.tui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.luigidemasi.camelkit.output.Printer;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.image.Image;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.ImageScaling;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.spinner.SpinnerStyle;

/**
 * Full-screen TUI for the init command.
 *
 * <p>
 * Left panel: Camel-Kit logo image (fixed, bordered). Right panel: task list with spinners/ticks and scrolling log
 * (bordered).
 */
public final class InitTuiView {

    private static final String LOGO_RESOURCE = "img/logo.png";

    /** Amber/gold — matches the tagline and the logo palette. */
    private static final Color AMBER = Color.rgb(0xF4, 0xAF, 0x23);

    /** Dots spinner frames (⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏). */
    private static final String[] SPINNER_FRAMES = SpinnerStyle.DOTS.frames();

    // logo.png: 690 × 517 px. Cell aspect ratio: 8 px wide × 16 px tall.
    private static final double HEIGHT_OVER_WIDTH = 517.0 / 690.0;
    private static final double CELL_ASPECT_CORRECTION = 8.0 / 16.0;

    // ── state shared between worker and render threads ──────────────────────

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final List<TaskEntry> tasks = new CopyOnWriteArrayList<>();
    private final StringBuilder pending = new StringBuilder();
    private final AtomicLong tick = new AtomicLong(0);

    private record TaskEntry(String emoji, String label, boolean done) {
    }

    // ── public factory methods ───────────────────────────────────────────────

    /**
     * Returns a {@link Printer} that routes all output to the right-panel log. ANSI escape codes are stripped — TamboUI
     * handles its own styling.
     */
    public Printer createPrinter() {
        return new Printer() {
            @Override
            public void println() {
                lines.add(stripAnsi(pending.toString()));
                pending.setLength(0);
            }

            @Override
            public void println(String line) {
                if (pending.length() > 0) {
                    lines.add(stripAnsi(pending + line));
                    pending.setLength(0);
                } else {
                    lines.add(stripAnsi(line));
                }
            }

            @Override
            public void print(String output) {
                pending.append(stripAnsi(output));
            }
        };
    }

    /**
     * Returns a {@link TaskTracker} that drives the spinner/tick list in the right panel.
     */
    public TaskTracker createTaskTracker() {
        return new TaskTracker() {
            @Override
            public void startTask(String emoji, String label) {
                tasks.add(new TaskEntry(emoji, label, false));
            }

            @Override
            public void finishTask() {
                int last = tasks.size() - 1;
                if (last >= 0) {
                    TaskEntry t = tasks.get(last);
                    tasks.set(last, new TaskEntry(t.emoji(), t.label(), true));
                }
            }
        };
    }

    // ── main entry point ─────────────────────────────────────────────────────

    /**
     * Start the TUI. Runs {@code work} in a background thread while the render loop draws both panels. Returns the exit
     * code from {@code work} when the user dismisses the view.
     *
     * <p>
     * {@link dev.tamboui.terminal.BackendException} propagates before any work starts so the caller can fall back to
     * normal mode safely.
     */
    public int run(Callable<Integer> work) throws Exception {
        ImageData imageData = loadImage();
        Image imageWidget = Image.builder()
                .data(imageData)
                .scaling(ImageScaling.FIT)
                .build();

        AtomicBoolean workDone = new AtomicBoolean(false);
        AtomicInteger exitCode = new AtomicInteger(0);
        AtomicReference<java.util.concurrent.Future<Integer>> futureRef = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "camel-kit-init-worker");
            t.setDaemon(true);
            return t;
        });

        Block leftBorder = Block.builder()
                .borders(Borders.ALL)
                .borderColor(AMBER)
                .build();

        Block rightBorder = Block.builder()
                .title(" Camel Kit ")
                .borders(Borders.ALL)
                .borderColor(AMBER)
                .build();

        // Explicitly instantiate JLineBackend to bypass ServiceLoader — in the
        // Camel JBang plugin context the classloader is isolated and ServiceLoader
        // cannot discover JLineBackendProvider via META-INF/services.
        // BackendException propagates here, before work starts, so the caller can
        // fall back to normal mode safely.
        TuiConfig config = TuiConfig.builder()
                .alternateScreen(false)
                .tickRate(java.time.Duration.ofMillis(100))
                .backend(new JLineBackend())
                .build();

        try (TuiRunner runner = TuiRunner.create(config)) {
            futureRef.set(executor.submit(work));

            try {
                System.out.write("\033[2J\033[H".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.flush();

                // Own event loop using pollEvent + draw so we can break out directly
                // when work is done — no dependency on runner.quit() unblocking read().
                int[] lastHeight = {24};
                int ticksAfterDone = 0;
                while (true) {
                    // Poll for up to 50ms; returns null on timeout (acts as our tick)
                    Event event = runner.pollEvent(java.time.Duration.ofMillis(50));
                    if (event instanceof KeyEvent k && k.isCtrlC())
                        break;
                    if (event instanceof ResizeEvent) {
                        try {
                            System.out.write("\033[2J\033[H".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            System.out.flush();
                        } catch (Exception ignored) {
                        }
                    }

                    tick.incrementAndGet();

                    // Capture future result
                    java.util.concurrent.Future<Integer> f = futureRef.get();
                    if (f != null && !workDone.get() && f.isDone()) {
                        workDone.set(true);
                        try {
                            exitCode.set(f.get());
                        } catch (Exception e) {
                            exitCode.set(1);
                        }
                    }

                    // Render current state
                    runner.draw(frame -> {
                        lastHeight[0] = frame.height();
                        frame.buffer().clear();
                        int w = frame.width();
                        int h = frame.height();

                        // Panels are 37.5% of terminal width, 85% of terminal height,
                        // centred both horizontally and vertically.
                        // Minimums ensure content stays inside the borders even on small terminals.
                        int panelWidth = Math.max(30, w * 3 / 8);
                        int panelHeight = Math.max(15, h * 17 / 20);
                        int topOffset = (h - panelHeight) / 2;
                        int gap = 1;
                        int leftStart = (w - panelWidth * 2 - gap) / 2;
                        int rightStart = leftStart + panelWidth + gap;

                        // Left panel: border + correctly-proportioned image
                        Rect leftOuter = new Rect(leftStart, topOffset, panelWidth, panelHeight);
                        frame.renderWidget(leftBorder, leftOuter);
                        Rect inner = leftBorder.inner(leftOuter);
                        int maxCols = inner.width();
                        // Reserve 2 extra rows at the top as padding inside the left panel
                        int topPad = 2;
                        int maxRows = Math.max(1, inner.height() - topPad);
                        int imgCols = Math.min(maxCols,
                                (int) Math.round(maxRows / (HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION)));
                        int imgRows = (int) Math.round(imgCols * HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION);
                        imgCols = Math.min(imgCols, maxCols);
                        imgRows = Math.min(imgRows, maxRows);
                        int imgX = inner.x() + (maxCols - imgCols) / 2;
                        int imgY = inner.y() + topPad + (maxRows - imgRows) / 2;
                        frame.renderWidget(imageWidget, new Rect(imgX, imgY, imgCols, imgRows));

                        // Right panel: border + task/log content
                        Rect rightOuter = new Rect(rightStart, topOffset, panelWidth, panelHeight);
                        frame.renderWidget(rightBorder, rightOuter);
                        renderRight(frame.buffer(), rightBorder.inner(rightOuter), workDone.get());
                    });

                    // Exit after work done + 2 extra frames so the user sees all ticks
                    if (workDone.get() && ++ticksAfterDone > 2)
                        break;
                }
                // Move cursor below the panels so the shell prompt appears cleanly.
                System.out.write(("\033[" + (lastHeight[0] + 1) + ";1H")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.flush();

            } finally {
                executor.shutdownNow();
            }
        }

        return exitCode.get();
    }

    // ── rendering ────────────────────────────────────────────────────────────

    private void renderRight(Buffer buffer, Rect area, boolean done) {
        int row = area.y();
        int spinnerIdx = (int) (tick.get() / 2 % SPINNER_FRAMES.length);

        // ── Task list ──────────────────────────────────────────────────────
        List<TaskEntry> snapshot = new ArrayList<>(tasks);
        for (int i = 0; i < snapshot.size() && row < area.y() + area.height(); i++) {
            TaskEntry task = snapshot.get(i);
            boolean isRunning = !task.done() && i == snapshot.size() - 1;

            Span statusSpan;
            Span labelSpan;

            if (task.done()) {
                statusSpan = Span.styled(" \u2713 ", Style.create().fg(Color.GREEN));
                labelSpan = Span.styled(task.emoji() + "  " + task.label(),
                        Style.create().fg(Color.GRAY));
            } else if (isRunning) {
                statusSpan = Span.styled(" " + SPINNER_FRAMES[spinnerIdx] + " ",
                        Style.create().fg(AMBER));
                labelSpan = Span.styled(task.emoji() + "  " + task.label(),
                        Style.create().fg(AMBER));
            } else {
                statusSpan = Span.styled(" \u25cb ", Style.create().fg(Color.DARK_GRAY));
                labelSpan = Span.styled(task.emoji() + "  " + task.label(),
                        Style.create().fg(Color.DARK_GRAY));
            }

            // Line.from(Span...) lets TamboUI handle wide-character widths correctly
            buffer.setLine(area.x(), row, Line.from(statusSpan, labelSpan));
            row++;
        }

        // blank separator between tasks and log
        if (!snapshot.isEmpty() && row < area.y() + area.height()) {
            row++;
        }

        // ── Log messages (word-wrapped to panel width) ────────────────────
        List<String> logSnapshot = new ArrayList<>(lines);
        // Pre-wrap all lines so we know the total rendered row count
        List<String> wrapped = new ArrayList<>();
        for (String line : logSnapshot) {
            wrapped.addAll(wordWrap(line, area.width()));
        }
        int remaining = (area.y() + area.height()) - row;
        int start = Math.max(0, wrapped.size() - remaining);
        for (int i = start; i < wrapped.size() && row < area.y() + area.height(); i++) {
            String line = wrapped.get(i);
            buffer.setLine(area.x(), row++, Line.from(Span.styled(line, styleForLog(line))));
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Word-wraps {@code line} to fit within {@code width} columns. Preserves leading whitespace on the first segment;
     * continuation lines are indented by 2 extra spaces to visually signal the wrap.
     */
    private static List<String> wordWrap(String line, int width) {
        List<String> result = new ArrayList<>();
        if (width <= 0 || line.isEmpty()) {
            result.add(line);
            return result;
        }
        if (line.length() <= width) {
            result.add(line);
            return result;
        }
        // Detect leading whitespace so continuation lines align neatly
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ')
            indent++;
        String contPrefix = " ".repeat(Math.min(indent + 2, width / 2));

        String[] words = line.split(" ", -1);
        StringBuilder current = new StringBuilder();
        boolean first = true;
        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                result.add(current.toString());
                current = new StringBuilder(first ? contPrefix : "").append(word);
                first = false;
            }
        }
        if (current.length() > 0)
            result.add(current.toString());
        return result;
    }

    /** Applies a colour style to a log line based on its content. */
    private static Style styleForLog(String line) {
        String t = line.trim();
        if (t.startsWith("✓") || t.startsWith("\u2713"))
            return Style.create().fg(Color.GREEN);
        if (t.startsWith("Warning") || t.contains("Warning:"))
            return Style.create().fg(Color.YELLOW);
        if (t.startsWith("Next steps"))
            return Style.create().fg(AMBER).bold();
        if (t.startsWith("/camel-"))
            return Style.create().fg(Color.CYAN);
        if (t.startsWith("\u2500"))
            return Style.create().fg(Color.DARK_GRAY); // ── divider
        if (t.isEmpty())
            return Style.EMPTY;
        // Next-steps numbered lines: "1  ...", "2  ..."
        if (t.length() > 1 && Character.isDigit(t.charAt(0)))
            return Style.create().fg(Color.WHITE);
        return Style.create().fg(Color.GRAY);
    }

    private static ImageData loadImage() throws Exception {
        try (InputStream in = InitTuiView.class.getClassLoader().getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Logo resource not found: " + LOGO_RESOURCE);
            }
            return ImageData.fromBytes(in.readAllBytes());
        }
    }

    private static String stripAnsi(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.replaceAll("\033\\[[^a-zA-Z]*[a-zA-Z]", "");
    }
}
