package io.github.luigidemasi.camelkit.service;

/**
 * Receives user-facing initialization events for command-layer rendering.
 */
public interface InitReporter {

    void mavenWrapperCreated(String mavenVersion);

    void graphBuilt(InitGraphSummary graph);

    void graphSkipped();

    void warning(InitWarning warning);

    static InitReporter noop() {
        return new InitReporter() {
            @Override
            public void mavenWrapperCreated(String mavenVersion) {
            }

            @Override
            public void graphBuilt(InitGraphSummary graph) {
            }

            @Override
            public void graphSkipped() {
            }

            @Override
            public void warning(InitWarning warning) {
            }
        };
    }
}
