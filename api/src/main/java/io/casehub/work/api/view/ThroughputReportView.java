package io.casehub.work.api.view;

import java.time.Instant;
import java.util.List;

public record ThroughputReportView(Instant from, Instant to, String groupBy,
                                    List<ThroughputBucketView> buckets) {
}
