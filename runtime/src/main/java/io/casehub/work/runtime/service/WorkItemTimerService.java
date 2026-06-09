package io.casehub.work.runtime.service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

/**
 * Manages per-WorkItem Quartz timers for expiry and claim-deadline events.
 *
 * <p>Replaces the polling approach (batch scan every N seconds) with precise
 * per-item timers that fire exactly when a deadline passes. Each WorkItem gets
 * at most one expiry timer and one claim-deadline timer, keyed by its UUID.
 *
 * <p>Timer management is idempotent: scheduling an already-existing timer replaces
 * it, and cancelling a non-existent timer is a silent no-op.
 *
 * <p>When a timer fires, the corresponding {@link org.quartz.Job} (
 * {@link ExpiryTimerJob} or {@link ClaimDeadlineTimerJob}) runs the breach
 * decision logic inside a tenant-scoped request context via {@link TenantContextRunner}.
 *
 * @see ExpiryTimerJob
 * @see ClaimDeadlineTimerJob
 */
@ApplicationScoped
public class WorkItemTimerService {

    private static final Logger LOG = Logger.getLogger(WorkItemTimerService.class);

    @Inject
    Scheduler scheduler;

    // ── Expiry timers ────────────────────────────────────────────────────────

    /** Schedule (or replace) the expiry timer for a WorkItem. */
    public void scheduleExpiry(UUID workItemId, String tenancyId, Instant fireAt) {
        scheduleTimer("expiry", ExpiryTimerJob.class, workItemId, tenancyId, fireAt);
    }

    /** Cancel the expiry timer for a WorkItem. Idempotent. */
    public void cancelExpiry(UUID workItemId) {
        cancelTimer("expiry", workItemId);
    }

    /** Replace the fire time of an existing expiry timer. */
    public void rescheduleExpiry(UUID workItemId, Instant newFireAt) {
        rescheduleTimer("expiry", workItemId, newFireAt);
    }

    // ── Claim deadline timers ────────────────────────────────────────────────

    /** Schedule (or replace) the claim-deadline timer for a WorkItem. */
    public void scheduleClaimDeadline(UUID workItemId, String tenancyId, Instant fireAt) {
        scheduleTimer("claim-deadline", ClaimDeadlineTimerJob.class, workItemId, tenancyId, fireAt);
    }

    /** Cancel the claim-deadline timer for a WorkItem. Idempotent. */
    public void cancelClaimDeadline(UUID workItemId) {
        cancelTimer("claim-deadline", workItemId);
    }

    /** Replace the fire time of an existing claim-deadline timer. */
    public void rescheduleClaimDeadline(UUID workItemId, Instant newFireAt) {
        rescheduleTimer("claim-deadline", workItemId, newFireAt);
    }

    // ── Query methods (primarily for tests) ──────────────────────────────────

    /** Returns {@code true} if an expiry timer exists for the given WorkItem. */
    public boolean hasExpiryTimer(UUID workItemId) {
        return hasTimer("expiry", workItemId);
    }

    /** Returns {@code true} if a claim-deadline timer exists for the given WorkItem. */
    public boolean hasClaimDeadlineTimer(UUID workItemId) {
        return hasTimer("claim-deadline", workItemId);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void scheduleTimer(String group, Class<? extends org.quartz.Job> jobClass,
                               UUID workItemId, String tenancyId, Instant fireAt) {
        JobKey jobKey = jobKey(group, workItemId);
        TriggerKey triggerKey = triggerKey(group, workItemId);

        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(jobKey)
                .usingJobData("workItemId", workItemId.toString())
                .usingJobData("tenancyId", tenancyId)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(Date.from(fireAt))
                .build();

        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule " + group + " timer for " + workItemId, e);
        }
    }

    private void cancelTimer(String group, UUID workItemId) {
        try {
            scheduler.deleteJob(jobKey(group, workItemId));
        } catch (SchedulerException e) {
            // Idempotent — job may not exist
            LOG.debugf("Ignoring cancel for non-existent %s timer: %s", group, workItemId);
        }
    }

    private void rescheduleTimer(String group, UUID workItemId, Instant newFireAt) {
        TriggerKey triggerKey = triggerKey(group, workItemId);
        try {
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(Date.from(newFireAt))
                    .build();
            scheduler.rescheduleJob(triggerKey, newTrigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to reschedule " + group + " timer for " + workItemId, e);
        }
    }

    private boolean hasTimer(String group, UUID workItemId) {
        try {
            return scheduler.checkExists(jobKey(group, workItemId));
        } catch (SchedulerException e) {
            return false;
        }
    }

    private static JobKey jobKey(String group, UUID workItemId) {
        return JobKey.jobKey(group + "-" + workItemId, "work-" + group);
    }

    private static TriggerKey triggerKey(String group, UUID workItemId) {
        return TriggerKey.triggerKey(group + "-trigger-" + workItemId, "work-" + group);
    }
}
