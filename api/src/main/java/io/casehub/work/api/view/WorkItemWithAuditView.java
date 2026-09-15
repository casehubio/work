package io.casehub.work.api.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.casehub.work.api.CompensationStatus;
import io.casehub.work.api.DeclineTarget;
import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;

public record WorkItemWithAuditView(
        UUID id,
        String title,
        String description,
        List<String> types,
        String formKey,
        WorkItemStatus status,
        WorkItemPriority priority,
        String assigneeId,
        String owner,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        String createdBy,
        DeclineTarget delegationDeclineTarget,
        String delegationChain,
        WorkItemStatus priorStatus,
        String payload,
        String resolution,
        Instant claimDeadline,
        Instant expiresAt,
        Instant followUpDate,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        Instant suspendedAt,
        List<WorkItemLabelView> labels,
        List<AuditEntryView> auditTrail,
        Double confidenceScore,
        String callerRef,
        Long version,
        UUID templateId,
        Long templateVersion,
        String outcome,
        List<Outcome> permittedOutcomes,
        String inputDataSchema,
        String outputDataSchema,
        String excludedUsers,
        String scope,
        String candidateScores,
        String routingExperiences,
        CompensationStatus compensationStatus,
        UUID compensatesWorkItemId) {
}
