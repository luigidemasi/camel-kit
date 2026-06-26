package io.github.luigidemasi.camelkit.service;

import java.util.Objects;

/**
 * Structured diagnostic produced by {@link DoctorService}.
 */
public record DoctorFinding(
        Status status,
        String category,
        String path,
        String message,
        String remediation) {

    public DoctorFinding {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(remediation, "remediation");
    }

    public static DoctorFinding pass(String category, String path, String message, String remediation) {
        return new DoctorFinding(Status.PASS, category, path, message, remediation);
    }

    public static DoctorFinding warn(String category, String path, String message, String remediation) {
        return new DoctorFinding(Status.WARN, category, path, message, remediation);
    }

    public static DoctorFinding fail(String category, String path, String message, String remediation) {
        return new DoctorFinding(Status.FAIL, category, path, message, remediation);
    }

    public enum Status {
        PASS,
        WARN,
        FAIL
    }
}
