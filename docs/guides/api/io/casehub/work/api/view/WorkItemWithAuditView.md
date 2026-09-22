# io.casehub.work.api.view.WorkItemWithAuditView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `assignedAt` (`java.time.Instant`)

### `assigneeId` (`java.lang.String`)

### `auditTrail` (`java.util.List<io.casehub.work.api.view.AuditEntryView>`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `candidateScores` (`java.lang.String`)

### `candidateUsers` (`java.lang.String`)

### `claimDeadline` (`java.time.Instant`)

### `compensatesWorkItemId` (`java.util.UUID`)

### `compensationStatus` (`io.casehub.work.api.CompensationStatus`)

### `completedAt` (`java.time.Instant`)

### `confidenceScore` (`java.lang.Double`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `delegationChain` (`java.lang.String`)

### `delegationDeclineTarget` (`io.casehub.work.api.DeclineTarget`)

### `description` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `followUpDate` (`java.time.Instant`)

### `formKey` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `inputDataSchema` (`java.lang.String`)

### `labels` (`java.util.List<io.casehub.work.api.view.WorkItemLabelView>`)

### `outcome` (`java.lang.String`)

### `outputDataSchema` (`java.lang.String`)

### `owner` (`java.lang.String`)

### `payload` (`java.lang.String`)

### `permittedOutcomes` (`java.util.List<io.casehub.work.api.Outcome>`)

### `priorStatus` (`io.casehub.work.api.WorkItemStatus`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `requiredCapabilities` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `routingExperiences` (`java.lang.String`)

### `scope` (`java.lang.String`)

### `startedAt` (`java.time.Instant`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `suspendedAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

### `title` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

### `updatedAt` (`java.time.Instant`)

### `version` (`java.lang.Long`)

## Record Components

### `assignedAt` (`java.time.Instant`)

### `assigneeId` (`java.lang.String`)

### `auditTrail` (`java.util.List<io.casehub.work.api.view.AuditEntryView>`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `candidateScores` (`java.lang.String`)

### `candidateUsers` (`java.lang.String`)

### `claimDeadline` (`java.time.Instant`)

### `compensatesWorkItemId` (`java.util.UUID`)

### `compensationStatus` (`io.casehub.work.api.CompensationStatus`)

### `completedAt` (`java.time.Instant`)

### `confidenceScore` (`java.lang.Double`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `delegationChain` (`java.lang.String`)

### `delegationDeclineTarget` (`io.casehub.work.api.DeclineTarget`)

### `description` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `followUpDate` (`java.time.Instant`)

### `formKey` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `inputDataSchema` (`java.lang.String`)

### `labels` (`java.util.List<io.casehub.work.api.view.WorkItemLabelView>`)

### `outcome` (`java.lang.String`)

### `outputDataSchema` (`java.lang.String`)

### `owner` (`java.lang.String`)

### `payload` (`java.lang.String`)

### `permittedOutcomes` (`java.util.List<io.casehub.work.api.Outcome>`)

### `priorStatus` (`io.casehub.work.api.WorkItemStatus`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `requiredCapabilities` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `routingExperiences` (`java.lang.String`)

### `scope` (`java.lang.String`)

### `startedAt` (`java.time.Instant`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `suspendedAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

### `title` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

### `updatedAt` (`java.time.Instant`)

### `version` (`java.lang.Long`)

## Constructors

### `public WorkItemWithAuditView(java.util.UUID id, java.lang.String title, java.lang.String description, java.util.List<java.lang.String> types, java.lang.String formKey, io.casehub.work.api.WorkItemStatus status, io.casehub.work.api.WorkItemPriority priority, java.lang.String assigneeId, java.lang.String owner, java.lang.String candidateGroups, java.lang.String candidateUsers, java.lang.String requiredCapabilities, java.lang.String createdBy, io.casehub.work.api.DeclineTarget delegationDeclineTarget, java.lang.String delegationChain, io.casehub.work.api.WorkItemStatus priorStatus, java.lang.String payload, java.lang.String resolution, java.time.Instant claimDeadline, java.time.Instant expiresAt, java.time.Instant followUpDate, java.time.Instant createdAt, java.time.Instant updatedAt, java.time.Instant assignedAt, java.time.Instant startedAt, java.time.Instant completedAt, java.time.Instant suspendedAt, java.util.List<io.casehub.work.api.view.WorkItemLabelView> labels, java.util.List<io.casehub.work.api.view.AuditEntryView> auditTrail, java.lang.Double confidenceScore, java.lang.String callerRef, java.lang.Long version, java.util.UUID templateId, java.lang.Long templateVersion, java.lang.String outcome, java.util.List<io.casehub.work.api.Outcome> permittedOutcomes, java.lang.String inputDataSchema, java.lang.String outputDataSchema, java.lang.String excludedUsers, java.lang.String scope, java.lang.String candidateScores, java.lang.String routingExperiences, io.casehub.work.api.CompensationStatus compensationStatus, java.util.UUID compensatesWorkItemId)`

#### Parameters

- `id` (`java.util.UUID`)
- `title` (`java.lang.String`)
- `description` (`java.lang.String`)
- `types` (`java.util.List<java.lang.String>`)
- `formKey` (`java.lang.String`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `priority` (`io.casehub.work.api.WorkItemPriority`)
- `assigneeId` (`java.lang.String`)
- `owner` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `candidateUsers` (`java.lang.String`)
- `requiredCapabilities` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `delegationDeclineTarget` (`io.casehub.work.api.DeclineTarget`)
- `delegationChain` (`java.lang.String`)
- `priorStatus` (`io.casehub.work.api.WorkItemStatus`)
- `payload` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `claimDeadline` (`java.time.Instant`)
- `expiresAt` (`java.time.Instant`)
- `followUpDate` (`java.time.Instant`)
- `createdAt` (`java.time.Instant`)
- `updatedAt` (`java.time.Instant`)
- `assignedAt` (`java.time.Instant`)
- `startedAt` (`java.time.Instant`)
- `completedAt` (`java.time.Instant`)
- `suspendedAt` (`java.time.Instant`)
- `labels` (`java.util.List<io.casehub.work.api.view.WorkItemLabelView>`)
- `auditTrail` (`java.util.List<io.casehub.work.api.view.AuditEntryView>`)
- `confidenceScore` (`java.lang.Double`)
- `callerRef` (`java.lang.String`)
- `version` (`java.lang.Long`)
- `templateId` (`java.util.UUID`)
- `templateVersion` (`java.lang.Long`)
- `outcome` (`java.lang.String`)
- `permittedOutcomes` (`java.util.List<io.casehub.work.api.Outcome>`)
- `inputDataSchema` (`java.lang.String`)
- `outputDataSchema` (`java.lang.String`)
- `excludedUsers` (`java.lang.String`)
- `scope` (`java.lang.String`)
- `candidateScores` (`java.lang.String`)
- `routingExperiences` (`java.lang.String`)
- `compensationStatus` (`io.casehub.work.api.CompensationStatus`)
- `compensatesWorkItemId` (`java.util.UUID`)

## Methods

### `public java.time.Instant assignedAt()`

### `public java.lang.String assigneeId()`

### `public java.util.List<io.casehub.work.api.view.AuditEntryView> auditTrail()`

### `public java.lang.String callerRef()`

### `public java.lang.String candidateGroups()`

### `public java.lang.String candidateScores()`

### `public java.lang.String candidateUsers()`

### `public java.time.Instant claimDeadline()`

### `public java.util.UUID compensatesWorkItemId()`

### `public io.casehub.work.api.CompensationStatus compensationStatus()`

### `public java.time.Instant completedAt()`

### `public java.lang.Double confidenceScore()`

### `public java.time.Instant createdAt()`

### `public java.lang.String createdBy()`

### `public java.lang.String delegationChain()`

### `public io.casehub.work.api.DeclineTarget delegationDeclineTarget()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String excludedUsers()`

### `public java.time.Instant expiresAt()`

### `public java.time.Instant followUpDate()`

### `public java.lang.String formKey()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String inputDataSchema()`

### `public java.util.List<io.casehub.work.api.view.WorkItemLabelView> labels()`

### `public java.lang.String outcome()`

### `public java.lang.String outputDataSchema()`

### `public java.lang.String owner()`

### `public java.lang.String payload()`

### `public java.util.List<io.casehub.work.api.Outcome> permittedOutcomes()`

### `public io.casehub.work.api.WorkItemStatus priorStatus()`

### `public io.casehub.work.api.WorkItemPriority priority()`

### `public java.lang.String requiredCapabilities()`

### `public java.lang.String resolution()`

### `public java.lang.String routingExperiences()`

### `public java.lang.String scope()`

### `public java.time.Instant startedAt()`

### `public io.casehub.work.api.WorkItemStatus status()`

### `public java.time.Instant suspendedAt()`

### `public java.util.UUID templateId()`

### `public java.lang.Long templateVersion()`

### `public java.lang.String title()`

### `public final java.lang.String toString()`

### `public java.util.List<java.lang.String> types()`

### `public java.time.Instant updatedAt()`

### `public java.lang.Long version()`
