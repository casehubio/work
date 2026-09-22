package io.casehub.work.queues.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.platform.view.SubjectViewOrchestrator;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.spi.WorkItemQueueApi;
import io.casehub.work.api.view.CreateQueueRequest;
import io.casehub.work.api.view.CreateQueueResult;
import io.casehub.work.api.view.QueueHealthMetricView;
import io.casehub.work.api.view.QueueSummaryView;
import io.casehub.work.api.view.QueueTrendDataPoint;
import io.casehub.work.api.view.QueueTrendView;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.queues.repository.QueueSnapshotStore;
import io.casehub.work.rest.service.ViewMapper;

@ApplicationScoped
public class DefaultWorkItemQueueApi implements WorkItemQueueApi {

    @Inject
    SubjectViewStore viewStore;

    @Inject
    SubjectViewOrchestrator orchestrator;

    @Inject
    QueueMembershipService membershipService;

    @Inject
    QueueSnapshotStore snapshotStore;

    @Override
    public List<QueueSummaryView> list(String tenancyId) {
        final Instant now = Instant.now();
        return viewStore.findByTenancy(tenancyId).stream()
                .map(q -> {
                    final var summary = membershipService.summarize(q, now);
                    return new QueueSummaryView(q.id(), q.name(), q.labelPattern(),
                            q.scope() != null ? q.scope().value() : "/", summary);
                })
                .toList();
    }

    @Override
    public List<QueueHealthMetricView> health(String tenancyId) {
        final Instant now = Instant.now();
        final var queues = viewStore.findByTenancy(tenancyId);

        long total = 0;
        long pending = 0;
        long active = 0;
        long overdue = 0;
        long breached = 0;

        for (final var queue : queues) {
            final var summary = membershipService.summarize(queue, now);
            total += summary.total();
            pending += summary.byStatus().getOrDefault("PENDING", 0L);
            active += summary.byStatus().getOrDefault("IN_PROGRESS", 0L)
                    + summary.byStatus().getOrDefault("ASSIGNED", 0L);
            overdue += summary.overdue();
            breached += summary.claimDeadlineBreached();
        }

        return List.of(
                new QueueHealthMetricView("total", total, "Total", "neutral"),
                new QueueHealthMetricView("pending", pending, "Pending",
                        pending > 0 ? "warning" : "neutral"),
                new QueueHealthMetricView("active", active, "Active", "neutral"),
                new QueueHealthMetricView("overdue", overdue, "Overdue",
                        overdue > 0 ? "critical" : "neutral"),
                new QueueHealthMetricView("breached", breached, "Claim SLA",
                        breached > 0 ? "critical" : "neutral"));
    }

    @Override
    public CreateQueueResult create(CreateQueueRequest request, String tenancyId) {
        if (request.labelPattern() == null || request.labelPattern().isBlank()) {
            throw new IllegalArgumentException("labelPattern is required");
        }
        final io.casehub.platform.api.path.Path scopePath;
        try {
            scopePath = (request.scope() == null || request.scope().isBlank())
                    ? io.casehub.platform.api.path.Path.root()
                    : io.casehub.platform.api.path.Path.parse(request.scope());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid scope: " + e.getMessage());
        }
        var spec = new SubjectViewSpec(
                UUID.randomUUID(), request.name(), tenancyId,
                request.labelPattern(), scopePath,
                request.sortField() != null ? request.sortField() : "createdAt",
                request.sortDirection() != null ? request.sortDirection() : "ASC",
                request.additionalConditions(), Instant.now());
        var saved = orchestrator.saveView(spec);
        return new CreateQueueResult(saved.id(), saved.name(), saved.labelPattern());
    }

    @Override
    public List<WorkItemView> query(UUID queueId, String tenancyId) {
        var spec = viewStore.findById(queueId).orElse(null);
        if (spec == null) {
            return null;
        }
        return membershipService.evaluateMembers(spec).stream()
                .map(ViewMapper::toView)
                .toList();
    }

    @Override
    public void delete(UUID queueId, String tenancyId) {
        if (viewStore.findById(queueId).isEmpty()) {
            throw new IllegalArgumentException("Queue view not found");
        }
        orchestrator.deleteView(queueId);
    }

    @Override
    public WorkItemSummary summary(UUID queueId, String tenancyId) {
        var spec = viewStore.findById(queueId).orElse(null);
        if (spec == null) {
            return null;
        }
        return membershipService.summarize(spec, Instant.now());
    }

    @Override
    public QueueTrendView trend(UUID queueId, String period, String tenancyId) {
        var spec = viewStore.findById(queueId).orElse(null);
        if (spec == null) {
            return null;
        }
        final Duration dur;
        try {
            dur = parsePeriod(period != null ? period : "24h");
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid period: " + period);
        }
        final Instant now = Instant.now();
        final Instant from = now.minus(dur);
        final var snapshots = snapshotStore.findByQueueAndPeriod(spec.id(), from, now);
        final var dataPoints = snapshots.stream()
                .map(s -> new QueueTrendDataPoint(s.snapshotAt, s.memberCount))
                .toList();
        return new QueueTrendView(spec.id(), spec.name(), dur.toString(), dataPoints);
    }

    static Duration parsePeriod(final String input) {
        if (input.startsWith("P") || input.startsWith("p")) {
            return Duration.parse(input);
        }
        final String normalized = input.toLowerCase();
        if (normalized.endsWith("h")) {
            return Duration.ofHours(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(
                    Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse("PT" + input);
    }
}
