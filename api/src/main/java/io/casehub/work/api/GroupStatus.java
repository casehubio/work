package io.casehub.work.api;

/**
 * Aggregate completion status of a multi-instance WorkItem group.
 */
public enum GroupStatus {
    /**
     * Group is still accepting completions — threshold not yet reached.
     */
    IN_PROGRESS,
    /**
     * Threshold reached with majority approval — group completed successfully.
     */
    COMPLETED,
    /**
     * Threshold reached but with majority rejection or escalation — group rejected.
     */
    REJECTED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}
