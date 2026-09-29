package io.casehub.work.api.view;

import java.time.Instant;

public record QueueTrendDataPoint(Instant snapshotAt, long memberCount) {
}
