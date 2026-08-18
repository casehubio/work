package io.casehub.work.runtime.service;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.ClaimSlaPolicy;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEmitter;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles expiry evaluation, SLA breach policy dispatch, and claim deadline breach processing.
 * Called by {@link ExpiryTimerJob} and {@link ClaimDeadlineTimerJob} (per-item Quartz timers),
 * and by the batch {@link #checkExpired()} / {@link #checkClaimDeadlines()} methods (retained
 * for startup recovery and tests). Also provides {@link #computeNewClaimDeadline} for use
 * by lifecycle transitions that return a WorkItem to the pool (release, delegate).
 */
@ApplicationScoped
public class ExpiryLifecycleService {

    private static final Logger LOG = Logger.getLogger(ExpiryLifecycleService.class);

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    @Inject
    StrategyResolver strategyResolver;

    SlaBreachPolicy slaBreachPolicy;

    @Inject
    PreferenceProvider preferenceProvider;

    @Inject
    WorkItemLifecycleEmitter lifecycleEmitter;

    @Inject
    Event<SlaBreachEvent> slaBreachEventBus;

    ClaimSlaPolicy claimSlaPolicy;

    @Inject
    WorkItemsConfig config;

    @jakarta.annotation.PostConstruct
    void init() {
        this.slaBreachPolicy = strategyResolver.resolve(SlaBreachPolicy.class, config.sla().breachPolicy());
        this.claimSlaPolicy = strategyResolver.resolve(ClaimSlaPolicy.class, config.sla().claimPolicy());
    }

    @Inject
    WorkItemAssignmentService assignmentService;

    @Inject
    WorkItemTimerService timerService;

    @Transactional
    public void checkExpired() {
        final Instant now = Instant.now();
        for (final io.casehub.work.api.WorkItem item : workItemStore.scan(WorkItemQuery.expired(now))) {
            try {
                final SlaBreachContext ctx  = buildBreachContext(item, BreachType.COMPLETION_EXPIRED, now);
                final BreachDecision   leaf = executeBreachDecision(item, slaBreachPolicy.onBreach(ctx), ctx, now);
                slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf, item.tenancyId()));
            } catch (final BreachExecutionFailed e) {
                LOG.errorf("SLA breach policy misconfigured for WorkItem %s — skipping this tick: %s",
                           item.id(), e.getMessage());
                writeAudit(item, "BREACH_POLICY_MISCONFIGURED", e.getMessage(), now);
            }
        }
    }

    @Transactional
    public void checkClaimDeadlines() {
        final Instant now = Instant.now();
        for (final io.casehub.work.api.WorkItem item : workItemStore.scan(WorkItemQuery.claimExpired(now))) {
            try {
                var updated = item;
                if (item.lastReturnedToPoolAt() != null) {
                    updated = updated.toBuilder()
                                     .accumulatedUnclaimedSeconds(item.accumulatedUnclaimedSeconds() + Duration.between(item.lastReturnedToPoolAt(), now).toSeconds())
                                     .lastReturnedToPoolAt(now)
                                     .build();
                } else {
                    updated = updated.toBuilder().lastReturnedToPoolAt(now).build();
                }
                fireLifecycleEvent("CLAIM_EXPIRED", updated);
                final SlaBreachContext ctx  = buildBreachContext(updated, BreachType.CLAIM_EXPIRED, now);
                final BreachDecision   leaf = executeBreachDecision(updated, slaBreachPolicy.onBreach(ctx), ctx, now);
                slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf, updated.tenancyId()));
            } catch (final BreachExecutionFailed e) {
                LOG.errorf("SLA breach policy misconfigured for WorkItem %s (claim) — skipping: %s",
                           item.id(), e.getMessage());
                writeAudit(item, "BREACH_POLICY_MISCONFIGURED", e.getMessage(), now);
            }
        }
    }

    public Instant computeNewClaimDeadline(final io.casehub.work.api.WorkItem item, final Instant now) {
        return claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
    }

    // ── Per-item methods (called by Quartz timer jobs) ─────────────────────

    @Transactional
    public void expireItem(final UUID workItemId) {
        workItemStore.get(workItemId).ifPresent(item -> {
            final Instant now = Instant.now();
            if (!item.status().isTerminal() && item.expiresAt() != null && !item.expiresAt().isAfter(now)) {
                try {
                    final SlaBreachContext ctx  = buildBreachContext(item, BreachType.COMPLETION_EXPIRED, now);
                    final BreachDecision   leaf = executeBreachDecision(item, slaBreachPolicy.onBreach(ctx), ctx, now);
                    slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf, item.tenancyId()));
                } catch (final BreachExecutionFailed e) {
                    LOG.errorf("SLA breach policy misconfigured for WorkItem %s — skipping: %s",
                               item.id(), e.getMessage());
                    writeAudit(item, "BREACH_POLICY_MISCONFIGURED", e.getMessage(), now);
                }
            }
        });
    }

    @Transactional
    public void processClaimDeadline(final UUID workItemId) {
        workItemStore.get(workItemId).ifPresent(item -> {
            final Instant now = Instant.now();
            if (!item.status().isTerminal() && item.claimDeadline() != null && !item.claimDeadline().isAfter(now)) {
                try {
                    var updated = item;
                    if (item.lastReturnedToPoolAt() != null) {
                        updated = updated.toBuilder()
                                         .accumulatedUnclaimedSeconds(item.accumulatedUnclaimedSeconds() + Duration.between(item.lastReturnedToPoolAt(), now).toSeconds())
                                         .lastReturnedToPoolAt(now)
                                         .build();
                    } else {
                        updated = updated.toBuilder().lastReturnedToPoolAt(now).build();
                    }
                    fireLifecycleEvent("CLAIM_EXPIRED", updated);
                    final SlaBreachContext ctx  = buildBreachContext(updated, BreachType.CLAIM_EXPIRED, now);
                    final BreachDecision   leaf = executeBreachDecision(updated, slaBreachPolicy.onBreach(ctx), ctx, now);
                    slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf, updated.tenancyId()));
                } catch (final BreachExecutionFailed e) {
                    LOG.errorf("SLA breach policy misconfigured for WorkItem %s (claim) — skipping: %s",
                               item.id(), e.getMessage());
                    writeAudit(item, "BREACH_POLICY_MISCONFIGURED", e.getMessage(), now);
                }
            }
        });
    }

    // ── Decision execution ───────────────────────────────────────────────────

    private BreachDecision executeBreachDecision(
            final io.casehub.work.api.WorkItem item, final BreachDecision decision,
            final SlaBreachContext ctx, final Instant now) {
        return switch (decision) {
            case BreachDecision.Fail fail -> executeFail(item, fail, now);
            case BreachDecision.EscalateTo escalate -> executeEscalateTo(item, escalate, ctx, now);
            case BreachDecision.Extend extend -> executeExtend(item, extend, ctx, now);
            case BreachDecision.Chained chained -> {
                try {
                    yield executeBreachDecision(item, chained.primary(), ctx, now);
                } catch (final BreachExecutionFailed e) {
                    try {
                        yield executeBreachDecision(item, chained.fallback(), ctx, now);
                    } catch (final BreachExecutionFailed e2) {
                        yield executeExhausted(item, "policy-exhausted", now);
                    }
                }
            }
            case BreachDecision.Exhausted exhausted -> executeExhausted(item, exhausted.reason(), now);
        };
    }

    private BreachDecision.Fail executeFail(final io.casehub.work.api.WorkItem item, final BreachDecision.Fail fail, final Instant now) {
        final io.casehub.work.api.WorkItem updated = item.toBuilder()
                                                         .status(WorkItemStatus.EXPIRED)
                                                         .completedAt(now)
                                                         .resolution(fail.reason())
                                                         .build();
        workItemStore.put(updated);
        timerService.cancelClaimDeadline(item.id());
        writeAudit(updated, "EXPIRED", fail.reason(), now);
        fireLifecycleEvent("EXPIRED", updated);
        return fail;
    }

    private BreachDecision.EscalateTo executeEscalateTo(
            final io.casehub.work.api.WorkItem item, final BreachDecision.EscalateTo escalate,
            final SlaBreachContext ctx, final Instant now) {
        if (escalate.groups().isEmpty()) {
            LOG.errorf("SlaBreachPolicy EscalateTo has empty groups for WorkItem %s — treating as policy failure",
                       item.id());
            throw new BreachExecutionFailed("EscalateTo returned empty groups");
        }
        var builder = item.toBuilder()
                          .candidateGroups(String.join(",", escalate.groups()))
                          .assigneeId(null)
                          .status(WorkItemStatus.PENDING);

        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            final Duration window = escalate.deadline() != null
                                    ? escalate.deadline()
                                    : Duration.ofHours(config.defaultExpiryHours());
            builder.expiresAt(now.plus(window));
        }
        io.casehub.work.api.WorkItem updated = builder.build();

        if (ctx.breachType() != BreachType.COMPLETION_EXPIRED) {
            updated = updated.toBuilder().claimDeadline(computeNewClaimDeadline(updated, now)).build();
        }

        updated = assignmentService.assign(updated, AssignmentTrigger.SLA_ESCALATED);
        workItemStore.put(updated);

        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            timerService.rescheduleExpiry(updated.id(), updated.expiresAt());
            if (updated.claimDeadline() != null) {
                timerService.scheduleClaimDeadline(updated.id(), updated.tenancyId(), updated.claimDeadline());
            }
        } else {
            timerService.rescheduleClaimDeadline(updated.id(), updated.claimDeadline());
        }
        writeAudit(updated, "SLA_REASSIGNED", null, now);
        fireLifecycleEvent("SLA_REASSIGNED", updated);
        return escalate;
    }

    private BreachDecision.Exhausted executeExhausted(final io.casehub.work.api.WorkItem item, final String reason, final Instant now) {
        final io.casehub.work.api.WorkItem updated = item.toBuilder()
                                                         .status(WorkItemStatus.ESCALATED)
                                                         .completedAt(now)
                                                         .build();
        workItemStore.put(updated);
        timerService.cancelClaimDeadline(item.id());
        writeAudit(updated, "ESCALATED", reason, now);
        fireLifecycleEvent("ESCALATED", updated);
        return new BreachDecision.Exhausted(reason);
    }

    private BreachDecision.Extend executeExtend(
            final io.casehub.work.api.WorkItem item, final BreachDecision.Extend extend,
            final SlaBreachContext ctx, final Instant now) {
        io.casehub.work.api.WorkItem updated;
        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            updated = item.toBuilder().expiresAt(now.plus(extend.by())).build();
        } else {
            updated = item.toBuilder().claimDeadline(now.plus(extend.by())).build();
        }
        workItemStore.put(updated);
        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            timerService.rescheduleExpiry(updated.id(), updated.expiresAt());
        } else {
            timerService.rescheduleClaimDeadline(updated.id(), updated.claimDeadline());
        }
        writeAudit(updated, "SLA_EXTENDED", null, now);
        fireLifecycleEvent("SLA_EXTENDED", updated);
        return extend;
    }

    // ── Context construction ──────────────────────────────────────────────────

    private SlaBreachContext buildBreachContext(final io.casehub.work.api.WorkItem item, final BreachType type, final Instant now) {
        final Path         scope  = item.scope() != null ? Path.parse(item.scope()) : Path.root();
        final var          prefs  = preferenceProvider.resolve(new SettingsScope(item.tenancyId(), scope, now));
        final Set<String>  groups = parseCandidateGroups(item.candidateGroups());
        final BreachedTask task   = new BreachedTask(item.id(), item.callerRef(), item.title(), groups);
        return new SlaBreachContext(type, task, scope, prefs);
    }

    private ClaimSlaContext buildClaimSlaContext(final io.casehub.work.api.WorkItem item, final Instant now) {
        final Duration totalPoolSla = config.defaultClaimHours() > 0
                                      ? Duration.ofHours(config.defaultClaimHours())
                                      : Duration.ofHours(24);
        final Duration accumulated = Duration.ofSeconds(item.accumulatedUnclaimedSeconds());
        final Instant  submitted   = item.createdAt() != null ? item.createdAt() : now;
        return new ClaimSlaContext(submitted, totalPoolSla, accumulated, now);
    }

    private static Set<String> parseCandidateGroups(final String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // ── Audit and events ──────────────────────────────────────────────────────

    private void writeAudit(final io.casehub.work.api.WorkItem item, final String event, final String detail, final Instant now) {
        final AuditEntry entry = new AuditEntry();
        entry.workItemId = item.id();
        entry.event      = event;
        entry.actor      = "system";
        entry.detail     = detail;
        entry.occurredAt = now;
        auditStore.append(entry);
    }

    private void fireLifecycleEvent(final String event, final io.casehub.work.api.WorkItem item) {
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of(event, item, "system", null));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Thrown when an {@link BreachDecision.EscalateTo} cannot execute; caught by Chained handler only. */
    private static final class BreachExecutionFailed extends RuntimeException {
        BreachExecutionFailed(final String msg) { super(msg, null, true, false); }
    }
}
