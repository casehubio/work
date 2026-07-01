package io.casehub.work.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupStatusTest {

    @Test
    void inProgress_isActive_notTerminal() {
        assertTrue(GroupStatus.IN_PROGRESS.isActive());
        assertFalse(GroupStatus.IN_PROGRESS.isTerminal());
    }

    @Test
    void completed_isTerminal_notActive() {
        assertTrue(GroupStatus.COMPLETED.isTerminal());
        assertFalse(GroupStatus.COMPLETED.isActive());
    }

    @Test
    void rejected_isTerminal_notActive() {
        assertTrue(GroupStatus.REJECTED.isTerminal());
        assertFalse(GroupStatus.REJECTED.isActive());
    }

    @Test
    void isTerminal_and_isActive_are_mutually_exclusive_and_exhaustive() {
        for (final GroupStatus status : GroupStatus.values()) {
            assertNotEquals(status.isTerminal(), status.isActive(),
                    status + " must be exactly one of terminal or active");
        }
    }
}
