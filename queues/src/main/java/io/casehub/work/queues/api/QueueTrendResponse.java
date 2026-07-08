package io.casehub.work.queues.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueTrendResponse(
        UUID queueViewId,
        String queueName,
        String period,
        List<DataPoint> dataPoints) {

    public record DataPoint(Instant snapshotAt, long memberCount) {}
}
