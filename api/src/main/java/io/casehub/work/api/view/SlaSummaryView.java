package io.casehub.work.api.view;

import java.util.Map;

public record SlaSummaryView(long totalBreached, double avgBreachDurationMinutes, Map<String, Long> byType) {
}
