package io.github.luigidemasi.camelkit.dispatch.model;

import java.util.List;

public record TaskSpec(
        String task,
        String mode,
        String approvalMode,
        int timeoutSeconds,
        List<String> filesContext) {

    public TaskSpec {
        if (approvalMode == null) {
            approvalMode = "auto_edit";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 300;
        }
        if (filesContext == null) {
            filesContext = List.of();
        }
    }
}
