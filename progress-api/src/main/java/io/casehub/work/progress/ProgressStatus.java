package io.casehub.work.progress;

public enum ProgressStatus {
    PENDING, ACTIVE, COMPLETED, FAILED;

    public boolean isTerminal() {
        return false;
    }

    public boolean isQuiescent() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
