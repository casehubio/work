package io.casehub.work.progress;

public enum ProgressChangeType {
    CREATED,
    STATE_UPDATED,
    CHILD_ATTACHED,
    COMPLETED,
    FAILED,
    REACTIVATED,
    ROLLED_BACK
}
