package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for {@link WorkItemTimerService} — verifies that Quartz timers can be
 * scheduled, cancelled, and rescheduled programmatically.
 *
 * <p>These are infrastructure tests that exercise the Quartz scheduler integration.
 * They do not fire the timers — only verify that job/trigger metadata is created
 * and removed correctly.
 */
@QuarkusTest
class WorkItemTimerServiceTest {

    @Inject
    WorkItemTimerService timerService;

    // ── Expiry timers ────────────────────────────────────────────────────────

    @Test
    void scheduleExpiry_createsTimer() {
        UUID id = UUID.randomUUID();
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(3600));
        assertThat(timerService.hasExpiryTimer(id)).isTrue();
    }

    @Test
    void cancelExpiry_removesTimer() {
        UUID id = UUID.randomUUID();
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(3600));
        timerService.cancelExpiry(id);
        assertThat(timerService.hasExpiryTimer(id)).isFalse();
    }

    @Test
    void cancelExpiry_idempotent() {
        // cancelling a non-existent timer should not throw
        timerService.cancelExpiry(UUID.randomUUID());
    }

    @Test
    void scheduleExpiry_replacesExistingTimer() {
        UUID id = UUID.randomUUID();
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(3600));
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(7200));
        assertThat(timerService.hasExpiryTimer(id)).isTrue();
    }

    @Test
    void rescheduleExpiry_updatesFireTime() {
        UUID id = UUID.randomUUID();
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(3600));
        timerService.rescheduleExpiry(id, Instant.now().plusSeconds(7200));
        assertThat(timerService.hasExpiryTimer(id)).isTrue();
    }

    // ── Claim deadline timers ────────────────────────────────────────────────

    @Test
    void scheduleClaimDeadline_createsTimer() {
        UUID id = UUID.randomUUID();
        timerService.scheduleClaimDeadline(id, "test-tenant", Instant.now().plusSeconds(3600));
        assertThat(timerService.hasClaimDeadlineTimer(id)).isTrue();
    }

    @Test
    void cancelClaimDeadline_removesTimer() {
        UUID id = UUID.randomUUID();
        timerService.scheduleClaimDeadline(id, "test-tenant", Instant.now().plusSeconds(3600));
        timerService.cancelClaimDeadline(id);
        assertThat(timerService.hasClaimDeadlineTimer(id)).isFalse();
    }

    @Test
    void cancelClaimDeadline_idempotent() {
        timerService.cancelClaimDeadline(UUID.randomUUID());
    }

    // ── Cross-group isolation ────────────────────────────────────────────────

    @Test
    void expiryAndClaimDeadline_areIndependent() {
        UUID id = UUID.randomUUID();
        timerService.scheduleExpiry(id, "test-tenant", Instant.now().plusSeconds(3600));
        timerService.scheduleClaimDeadline(id, "test-tenant", Instant.now().plusSeconds(1800));

        assertThat(timerService.hasExpiryTimer(id)).isTrue();
        assertThat(timerService.hasClaimDeadlineTimer(id)).isTrue();

        timerService.cancelExpiry(id);
        assertThat(timerService.hasExpiryTimer(id)).isFalse();
        assertThat(timerService.hasClaimDeadlineTimer(id)).isTrue();
    }
}
