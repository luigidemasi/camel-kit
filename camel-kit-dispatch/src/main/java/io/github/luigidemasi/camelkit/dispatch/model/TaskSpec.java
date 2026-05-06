package io.github.luigidemasi.camelkit.dispatch.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record TaskSpec(
        String task,
        String mode,
        String approvalMode,
        int timeoutSeconds,
        List<String> filesContext) {

    private static final Set<String> VALID_APPROVAL_MODES = Set.of("auto_edit", "yolo", "read_only");

    public TaskSpec {
        if (approvalMode == null) {
            approvalMode = "auto_edit";
        } else {
            approvalMode = approvalMode.toLowerCase(Locale.ROOT);
            if (!VALID_APPROVAL_MODES.contains(approvalMode)) {
                throw new IllegalArgumentException(
                        "Invalid approvalMode '" + approvalMode + "'. Must be one of: " + VALID_APPROVAL_MODES);
            }
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 300;
        }
        if (filesContext == null) {
            filesContext = List.of();
        }
    }
}
