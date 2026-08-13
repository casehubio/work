package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.casehub.work.api.WorkItem;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.api.spi.WorkItemStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for the lifecycle engine.
 *
 * <p>
 * The Quarkus scheduler is disabled in test application.properties so that jobs do not
 * fire automatically. Tests invoke {@link ExpiryLifecycleService#checkExpired()} and
 * {@link ExpiryLifecycleService#checkClaimDeadlines()} directly.
 */
@QuarkusTest
@TestTransaction
class LifecycleEngineTest {

    @Inject
    ExpiryLifecycleService expiryLifecycleService;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Persists a WorkItem directly with {@code expiresAt} set 2 hours in the past,
     * bypassing the service layer so any active status can be combined with a
     * past expiry timestamp.
     */
    private WorkItem createExpiredItem(WorkItemStatus status) {
        WorkItem wi = WorkItem.builder()
                .title("Expiry test")
                .status(status)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
        return workItemStore.put(wi);
    }

    /**
     * Persists a PENDING WorkItem with {@code claimDeadline} set 1 hour in the past.
     */
    private WorkItem createPastClaimDeadlineItem() {
        WorkItem wi = WorkItem.builder()
                .title("Claim deadline test")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .claimDeadline(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        return workItemStore.put(wi);
    }

    // -------------------------------------------------------------------------
    // Expiry — active statuses all transition to EXPIRED
    // -------------------------------------------------------------------------

    @Test
    void expiry_pendingItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.PENDING);
        expiryLifecycleService.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_assignedItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.ASSIGNED);
        expiryLifecycleService.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_inProgressItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.IN_PROGRESS);
        expiryLifecycleService.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_suspendedItemPastDeadline_transitionsToExpired() {
        WorkItem wi = createExpiredItem(WorkItemStatus.SUSPENDED);
        expiryLifecycleService.checkExpired();
        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void expiry_completedItemNotAffected() {
        WorkItem completed = workItemStore.put(WorkItem.builder()
                .title("Completed")
                .status(WorkItemStatus.COMPLETED)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .completedAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build());

        expiryLifecycleService.checkExpired();

        WorkItem reloaded = workItemStore.get(completed.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.COMPLETED);
    }

    @Test
    void expiry_futureDeadlineNotTriggered() {
        WorkItem wi = workItemStore.put(WorkItem.builder()
                .title("Future expiry")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build());

        expiryLifecycleService.checkExpired();

        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void expiry_writesExpiredAuditEntry() {
        WorkItem wi = createExpiredItem(WorkItemStatus.PENDING);
        expiryLifecycleService.checkExpired();
        List<AuditEntry> trail = auditStore.findByWorkItemId(wi.id());
        assertThat(trail).anyMatch(e -> "EXPIRED".equals(e.event) && "system".equals(e.actor));
    }

    @Test
    void expiry_multipleItemsAllProcessed() {
        WorkItem wi1 = createExpiredItem(WorkItemStatus.PENDING);
        WorkItem wi2 = createExpiredItem(WorkItemStatus.ASSIGNED);
        WorkItem wi3 = createExpiredItem(WorkItemStatus.IN_PROGRESS);

        expiryLifecycleService.checkExpired();

        assertThat(workItemStore.get(wi1.id()).orElseThrow().status()).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(wi2.id()).orElseThrow().status()).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(wi3.id()).orElseThrow().status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    // -------------------------------------------------------------------------
    // Claim deadline
    // -------------------------------------------------------------------------

    @Test
    void claimDeadline_pendingItemPastDeadline_jobRuns() {
        WorkItem wi = createPastClaimDeadlineItem();
        // NoOpSlaBreachPolicy returns Fail — item transitions to EXPIRED when claim deadline passes.
        // Applications configure SlaBreachPolicy to keep items active (EscalateTo, Extend).
        assertThatCode(() -> expiryLifecycleService.checkClaimDeadlines()).doesNotThrowAnyException();
        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void claimDeadline_assignedItemNotAffected() {
        WorkItem wi = workItemStore.put(WorkItem.builder()
                .title("Assigned past claim deadline")
                .status(WorkItemStatus.ASSIGNED)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .claimDeadline(Instant.now().minus(1, ChronoUnit.HOURS))
                .assigneeId("alice")
                .build());

        expiryLifecycleService.checkClaimDeadlines();

        WorkItem reloaded = workItemStore.get(wi.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorkItemStatus.ASSIGNED);
    }

    // -------------------------------------------------------------------------
    // Mixed past/future — only past expiry processed
    // -------------------------------------------------------------------------

    @Test
    void expiry_onlyPastExpiryProcessed() {
        WorkItem past = createExpiredItem(WorkItemStatus.PENDING);

        WorkItem future = workItemStore.put(WorkItem.builder()
                .title("Future expiry")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .build());

        expiryLifecycleService.checkExpired();

        assertThat(workItemStore.get(past.id()).orElseThrow().status()).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(workItemStore.get(future.id()).orElseThrow().status()).isEqualTo(WorkItemStatus.PENDING);
    }
}
