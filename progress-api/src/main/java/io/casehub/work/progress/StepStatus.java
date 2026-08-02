package io.casehub.work.progress;

public enum StepStatus {
    PENDING, ACTIVE, COMPLETED, SKIPPED, FAILED;

    public boolean isTerminal() {
        return this == SKIPPED;
    }

    public boolean isDone() {
        return this == COMPLETED || this == SKIPPED;
    }

    public boolean isQuiescent() {
        return this == COMPLETED || this == SKIPPED || this == FAILED;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
