package io.casehub.work.api.view;

public record QueueHealthMetricView(String key, long value, String label, String status) {
}
