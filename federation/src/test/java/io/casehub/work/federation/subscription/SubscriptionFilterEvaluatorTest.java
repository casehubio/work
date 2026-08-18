package io.casehub.work.federation.subscription;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionFilterEvaluatorTest {

    private WorkItem item(String candidateGroups, String candidateUsers, String tenancyId) {
        return WorkItem.builder()
                .id(UUID.randomUUID()).title("test").createdBy("sys")
                .status(WorkItemStatus.PENDING).priority(WorkItemPriority.MEDIUM)
                .candidateGroups(candidateGroups).candidateUsers(candidateUsers)
                .tenancyId(tenancyId)
                .build();
    }

    @Test
    void matchesCandidateGroupIntersection() {
        var filter = new SubscriptionFilter(List.of("legal", "compliance"), List.of(), "tenant-1");
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item("legal,finance", null, "tenant-1")));
    }

    @Test
    void matchesCandidateUserIntersection() {
        var filter = new SubscriptionFilter(List.of(), List.of("alice", "bob"), "tenant-1");
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item(null, "bob,charlie", "tenant-1")));
    }

    @Test
    void groupAndUserCombineWithOr() {
        var filter = new SubscriptionFilter(List.of("legal"), List.of("alice"), "tenant-1");
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item(null, "alice", "tenant-1")));
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item("legal", null, "tenant-1")));
    }

    @Test
    void rejectsTenancyMismatch() {
        var filter = new SubscriptionFilter(List.of("legal"), List.of(), "tenant-1");
        assertFalse(SubscriptionFilterEvaluator.matches(filter, item("legal", null, "tenant-2")));
    }

    @Test
    void rejectsNoIntersection() {
        var filter = new SubscriptionFilter(List.of("legal"), List.of(), "tenant-1");
        assertFalse(SubscriptionFilterEvaluator.matches(filter, item("finance,hr", null, "tenant-1")));
    }

    @Test
    void emptyFilterMatchesAllInTenant() {
        var filter = new SubscriptionFilter(List.of(), List.of(), "tenant-1");
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item("any-group", null, "tenant-1")));
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item(null, null, "tenant-1")));
    }

    @Test
    void handlesNullCandidateFieldsOnWorkItem() {
        var filter = new SubscriptionFilter(List.of("legal"), List.of(), "tenant-1");
        assertFalse(SubscriptionFilterEvaluator.matches(filter, item(null, null, "tenant-1")));
    }

    @Test
    void handlesWhitespaceInCsv() {
        var filter = new SubscriptionFilter(List.of("legal"), List.of(), "tenant-1");
        assertTrue(SubscriptionFilterEvaluator.matches(filter, item(" legal , finance ", null, "tenant-1")));
    }
}
