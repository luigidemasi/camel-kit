package io.github.luigidemasi.camelkit.util;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.image.ImageData;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import dev.tamboui.image.protocol.ImageProtocol;
import dev.tamboui.layout.Rect;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Renders the Camel-Kit logo using a native terminal image protocol
 * (Kitty, iTerm2, or Sixel) when supported.
 *
 * <p>Returns {@code false} if the current terminal does not support any native
 * image protocol, so the caller can fall back to ASCII art.
 */
public final class LogoRenderer {

    private static final String LOGO_RESOURCE = "img/logo.png";

    // logo.png: 690 × 517 px
    // Terminal cells are approximately 8 px wide × 16 px tall.
    // height/width corrected for cell aspect ratio → rows = cols * (517/690) * (8/16)
    private static final double HEIGHT_OVER_WIDTH = 517.0 / 690.0;
    private static final double CELL_ASPECT_CORRECTION = 8.0 / 16.0;

    /** ANSI: clear entire screen and move cursor to top-left (1;1). */
    private static final byte[] CLEAR_SCREEN = "\033[2J\033[H".getBytes(StandardCharsets.UTF_8);


    private LogoRenderer() {
    }

    /**
     * Attempts to render the logo to {@code rawOutput} using the best available
     * native terminal image protocol.
     *
     * <p>Clears the screen first, then centers the image horizontally. The image
     * is sized to fit within the given terminal dimensions while preserving the
     * pixel aspect ratio of the source PNG.
     *
     * @param rawOutput      raw output stream (e.g. {@code System.out})
     * @param terminalWidth  terminal width in columns
     * @param terminalHeight terminal height in rows
     * @return {@code true} if the image was rendered, {@code false} if no native
     *         protocol is available and the caller should use ASCII art instead
     */
    public static boolean tryRender(OutputStream rawOutput, int terminalWidth, int terminalHeight) {
        try {
            TerminalImageCapabilities caps = TerminalImageCapabilities.detect();
            if (!caps.supportsNativeImages()) {
                return false;
            }

            byte[] imageBytes;
            try (InputStream in = LogoRenderer.class.getClassLoader().getResourceAsStream(LOGO_RESOURCE)) {
                if (in == null) {
                    return false;
                }
                imageBytes = in.readAllBytes();
            }

            ImageData image = ImageData.fromBytes(imageBytes);
            ImageProtocol protocol = caps.bestProtocol();

            // Derive max cols from terminal width (3/8 of width, 15–45 cols).
            int colsFromWidth = Math.min(45, Math.max(15, terminalWidth * 3 / 8));

            // Derive max cols from terminal height (leave 4 rows for tagline).
            // rows = cols * HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION
            // → cols = (terminalHeight - 4) / (HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION)
            int usableRows = Math.max(5, terminalHeight - 4);
            int colsFromHeight = (int) (usableRows / (HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION));

            // Use the smaller of the two to ensure it fits both dimensions.
            int cols = Math.min(colsFromWidth, colsFromHeight);
            int rows = (int) Math.max(5, cols * HEIGHT_OVER_WIDTH * CELL_ASPECT_CORRECTION);

            int leftPad = Math.max(0, (terminalWidth - cols) / 2);

            // Clear the screen so no stale characters bleed through the image.
            rawOutput.write(CLEAR_SCREEN);

            // KittyProtocol (and the other native protocols) emit their own
            // "\033[row;colH" cursor-positioning escape from rect.x()/rect.y(),
            // so centering is done by setting x = leftPad on the Rect.
            Rect area = new Rect(leftPad, 0, cols, rows);
            Buffer buffer = Buffer.empty(area);

            protocol.render(image, area, buffer, rawOutput);
            rawOutput.flush();
            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
