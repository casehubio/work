package io.casehub.work.queues.api;

public record QueueHealthMetric(String key, long value, String label, String status) {}
