package io.casehub.work.api.view;

public record CreateScheduleRequest(
        String name,
        String templateId,
        String cronExpression,
        String createdBy) {
}
