package io.github.luigidemasi.camelkit.service;

/**
 * Receives initialization task lifecycle events without coupling services to a specific UI.
 */
public interface InitProgress {

    void startTask(String icon, String label);

    void finishTask();

    static InitProgress noop() {
        return new InitProgress() {
            @Override
            public void startTask(String icon, String label) {
            }

            @Override
            public void finishTask() {
            }
        };
    }
}
