package io.casehub.work.reports.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.spi.WorkItemReportApi;
import io.casehub.work.api.view.ActorReportView;
import io.casehub.work.api.view.QueueHealthReportView;
import io.casehub.work.api.view.SlaBreachItemView;
import io.casehub.work.api.view.SlaBreachReportView;
import io.casehub.work.api.view.SlaSummaryView;
import io.casehub.work.api.view.ThroughputBucketView;
import io.casehub.work.api.view.ThroughputReportView;

@ApplicationScoped
public class DefaultWorkItemReportApi implements WorkItemReportApi {

    @Inject
    ReportService reportService;

    @Override
    public SlaBreachReportView slaBreaches(String from, String to, String type, String priority,
                                            String tenancyId) {
        final SlaBreachReport report = reportService.slaBreaches(
                parseInstant(from, "from"), parseInstant(to, "to"),
                type, parsePriority(priority));
        return new SlaBreachReportView(
                report.items().stream()
                        .map(i -> new SlaBreachItemView(i.workItemId(), i.types(), i.priority(),
                                i.expiresAt(), i.completedAt(), i.status(), i.breachDurationMinutes()))
                        .toList(),
                new SlaSummaryView(report.summary().totalBreached(),
                        report.summary().avgBreachDurationMinutes(),
                        report.summary().byType()));
    }

    @Override
    public ActorReportView actorPerformance(String actorId, String from, String to, String type,
                                             String tenancyId) {
        final ActorReport report = reportService.actorPerformance(
                actorId, parseInstant(from, "from"), parseInstant(to, "to"), type);
        return new ActorReportView(report.actorId(), report.totalAssigned(),
                report.totalCompleted(), report.totalRejected(),
                report.avgCompletionMinutes(), report.byType());
    }

    @Override
    public ThroughputReportView throughput(String from, String to, String groupBy, String tenancyId) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' are required for throughput reports");
        }
        final String effectiveGroupBy = groupBy != null ? groupBy : "day";
        if (!effectiveGroupBy.equals("day") && !effectiveGroupBy.equals("week")
                && !effectiveGroupBy.equals("month")) {
            throw new IllegalArgumentException(
                    "Invalid groupBy '" + effectiveGroupBy + "': must be day, week, or month");
        }
        final ThroughputReport report = reportService.throughput(
                parseInstant(from, "from"), parseInstant(to, "to"), effectiveGroupBy);
        return new ThroughputReportView(report.from(), report.to(), report.groupBy(),
                report.buckets().stream()
                        .map(b -> new ThroughputBucketView(b.period(), b.created(), b.completed()))
                        .toList());
    }

    @Override
    public QueueHealthReportView queueHealth(String type, String priority, String tenancyId) {
        final QueueHealthReport report = reportService.queueHealth(type, parsePriority(priority));
        return new QueueHealthReportView(report.timestamp(), report.overdueCount(),
                report.pendingCount(), report.avgPendingAgeSeconds(),
                report.oldestUnclaimedCreatedAt(), report.criticalOverdueCount());
    }

    private static WorkItemPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return WorkItemPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + priority);
        }
    }

    private static Instant parseInstant(String value, String paramName) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid ISO 8601 timestamp for '" + paramName + "': " + value);
        }
    }
}
