package io.casehub.work.api.view;

import java.util.List;
import java.util.UUID;

public record QueueTrendView(UUID queueViewId, String queueName, String period,
                             List<QueueTrendDataPoint> dataPoints) {
}
