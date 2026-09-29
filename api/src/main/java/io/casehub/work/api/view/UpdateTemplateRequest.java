package io.casehub.work.api.view;

import java.util.List;

import io.casehub.work.api.Outcome;

public record UpdateTemplateRequest(
        String name,
        String description,
        String typePaths,
        String priority,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        Integer defaultExpiryHours,
        Integer defaultClaimHours,
        Integer defaultExpiryBusinessHours,
        Integer defaultClaimBusinessHours,
        String defaultPayload,
        String labelPaths,
        Integer instanceCount,
        Integer requiredCount,
        String parentRole,
        String assignmentStrategy,
        String onThresholdReached,
        Boolean allowSameAssignee,
        List<Outcome> outcomes,
        String inputDataSchema,
        String outputDataSchema,
        String excludedUsers,
        String excludedGroups,
        String scope) {
}
