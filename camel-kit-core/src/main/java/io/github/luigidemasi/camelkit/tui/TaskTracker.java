package io.github.luigidemasi.camelkit.tui;

/**
 * Receives task lifecycle events so the UI can render spinners and ticks.
 */
public interface TaskTracker {

    void startTask(String emoji, String label);

    void finishTask();

    /** No-op implementation used in normal (non-TUI) mode. */
    static TaskTracker noop() {
        return new TaskTracker() {
            @Override
            public void startTask(String emoji, String label) {
            }

            @Override
            public void finishTask() {
            }
        };
    }
}
