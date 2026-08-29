package io.casehub.work.rest;

import java.time.Instant;
import java.util.List;

import io.casehub.work.api.WorkItemLabelRequest;
import io.casehub.work.api.WorkItemPriority;

public record CreateWorkItemRequest(
        String title,
        String description,
        List<String> types,
        String formKey,
        WorkItemPriority priority,
        String assigneeId,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        String createdBy,
        String payload,
        Instant claimDeadline,
        Instant expiresAt,
        Instant followUpDate,
        List<WorkItemLabelRequest> labels,
        Double confidenceScore,
        String callerRef,
        Integer claimDeadlineBusinessHours,
        Integer expiresAtBusinessHours,
        /** Comma-separated user IDs excluded from claiming this WorkItem; null = no exclusion. */
        String excludedUsers,
        /** Hierarchical scope path e.g. {@code "casehubio/devtown/pr-review"}; null = root scope. */
        String scope,
        String escalationOnExpiry,
        String escalationOnClaimDeadline,
        String escalationDeadline,
        Boolean escalationGenerateSummary) {

    public CreateWorkItemRequest {
        if (escalationDeadline != null && !escalationDeadline.isEmpty()) {
            try {
                java.time.Duration d = java.time.Duration.parse(escalationDeadline);
                if (d.isZero() || d.isNegative()) {
                    throw new IllegalArgumentException(
                        "escalationDeadline must be positive, was: " + escalationDeadline);
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                    "escalationDeadline is not a valid ISO-8601 duration: " + escalationDeadline, e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String description;
        private List<String> types;
        private String formKey;
        private WorkItemPriority priority;
        private String assigneeId;
        private String candidateGroups;
        private String candidateUsers;
        private String requiredCapabilities;
        private String createdBy;
        private String payload;
        private Instant claimDeadline;
        private Instant expiresAt;
        private Instant followUpDate;
        private List<WorkItemLabelRequest> labels;
        private Double confidenceScore;
        private String callerRef;
        private Integer claimDeadlineBusinessHours;
        private Integer expiresAtBusinessHours;
        private String excludedUsers;
        private String scope;
        private String escalationOnExpiry;
        private String escalationOnClaimDeadline;
        private String escalationDeadline;
        private Boolean escalationGenerateSummary;

        public Builder title(final String v)                          { this.title = v; return this; }
        public Builder description(final String v)                    { this.description = v; return this; }
        public Builder types(final List<String> v)                    { this.types = v; return this; }
        public Builder formKey(final String v)                        { this.formKey = v; return this; }
        public Builder priority(final WorkItemPriority v)             { this.priority = v; return this; }
        public Builder assigneeId(final String v)                     { this.assigneeId = v; return this; }
        public Builder candidateGroups(final String v)                { this.candidateGroups = v; return this; }
        public Builder candidateUsers(final String v)                 { this.candidateUsers = v; return this; }
        public Builder requiredCapabilities(final String v)           { this.requiredCapabilities = v; return this; }
        public Builder createdBy(final String v)                      { this.createdBy = v; return this; }
        public Builder payload(final String v)                        { this.payload = v; return this; }
        public Builder claimDeadline(final Instant v)                 { this.claimDeadline = v; return this; }
        public Builder expiresAt(final Instant v)                     { this.expiresAt = v; return this; }
        public Builder followUpDate(final Instant v)                  { this.followUpDate = v; return this; }
        public Builder labels(final List<WorkItemLabelRequest> v)    { this.labels = v; return this; }
        public Builder confidenceScore(final Double v)                { this.confidenceScore = v; return this; }
        public Builder callerRef(final String v)                      { this.callerRef = v; return this; }
        public Builder claimDeadlineBusinessHours(final Integer v)    { this.claimDeadlineBusinessHours = v; return this; }
        public Builder expiresAtBusinessHours(final Integer v)        { this.expiresAtBusinessHours = v; return this; }
        public Builder excludedUsers(final String v)                  { this.excludedUsers = v; return this; }
        public Builder scope(final String v)                          { this.scope = v; return this; }
        public Builder escalationOnExpiry(final String v)              { this.escalationOnExpiry = v; return this; }
        public Builder escalationOnClaimDeadline(final String v)       { this.escalationOnClaimDeadline = v; return this; }
        public Builder escalationDeadline(final String v)              { this.escalationDeadline = v; return this; }
        public Builder escalationGenerateSummary(final Boolean v)      { this.escalationGenerateSummary = v; return this; }

        public CreateWorkItemRequest build() {
            return new CreateWorkItemRequest(title, description, types, formKey,
                    priority, assigneeId, candidateGroups, candidateUsers,
                    requiredCapabilities, createdBy, payload, claimDeadline,
                    expiresAt, followUpDate, labels, confidenceScore, callerRef,
                    claimDeadlineBusinessHours, expiresAtBusinessHours, excludedUsers, scope,
                    escalationOnExpiry, escalationOnClaimDeadline,
                    escalationDeadline, escalationGenerateSummary);
        }
    }
}
