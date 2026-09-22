# io.casehub.work.api.WorkItem

**Package:** `io.casehub.work.api`

**Kind:** `record`

## Fields

### `accumulatedUnclaimedSeconds` (`long`)

### `assignedAt` (`java.time.Instant`)

### `assigneeId` (`java.lang.String`)

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

### `escalationDeadline` (`java.lang.String`)

### `escalationGenerateSummary` (`java.lang.Boolean`)

### `escalationOnClaimDeadline` (`java.lang.String`)

### `escalationOnExpiry` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `followUpDate` (`java.time.Instant`)

### `formKey` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `inputDataSchema` (`java.lang.String`)

### `labels` (`java.util.List<io.casehub.work.api.WorkItemLabel>`)

### `lastReturnedToPoolAt` (`java.time.Instant`)

### `originRef` (`java.lang.String`)

### `originServiceId` (`java.lang.String`)

### `originVersion` (`java.lang.Long`)

### `originWorkItemId` (`java.util.UUID`)

### `outcome` (`java.lang.String`)

### `outputDataSchema` (`java.lang.String`)

### `owner` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

### `payload` (`java.lang.String`)

### `payloadTypeName` (`java.lang.String`)

### `permittedOutcomes` (`java.lang.String`)

### `priorStatus` (`io.casehub.work.api.WorkItemStatus`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `requiredCapabilities` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `resolutionTypeName` (`java.lang.String`)

### `routingExperiences` (`java.lang.String`)

### `scope` (`java.lang.String`)

### `startedAt` (`java.time.Instant`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `suspendedAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

### `tenancyId` (`java.lang.String`)

### `title` (`java.lang.String`)

### `types` (`java.util.Set<java.lang.String>`)

### `updatedAt` (`java.time.Instant`)

### `version` (`java.lang.Long`)

## Record Components

### `accumulatedUnclaimedSeconds` (`long`)

### `assignedAt` (`java.time.Instant`)

### `assigneeId` (`java.lang.String`)

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

### `escalationDeadline` (`java.lang.String`)

### `escalationGenerateSummary` (`java.lang.Boolean`)

### `escalationOnClaimDeadline` (`java.lang.String`)

### `escalationOnExpiry` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `followUpDate` (`java.time.Instant`)

### `formKey` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `inputDataSchema` (`java.lang.String`)

### `labels` (`java.util.List<io.casehub.work.api.WorkItemLabel>`)

### `lastReturnedToPoolAt` (`java.time.Instant`)

### `originRef` (`java.lang.String`)

### `originServiceId` (`java.lang.String`)

### `originVersion` (`java.lang.Long`)

### `originWorkItemId` (`java.util.UUID`)

### `outcome` (`java.lang.String`)

### `outputDataSchema` (`java.lang.String`)

### `owner` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

### `payload` (`java.lang.String`)

### `payloadTypeName` (`java.lang.String`)

### `permittedOutcomes` (`java.lang.String`)

### `priorStatus` (`io.casehub.work.api.WorkItemStatus`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `requiredCapabilities` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `resolutionTypeName` (`java.lang.String`)

### `routingExperiences` (`java.lang.String`)

### `scope` (`java.lang.String`)

### `startedAt` (`java.time.Instant`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `suspendedAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

### `tenancyId` (`java.lang.String`)

### `title` (`java.lang.String`)

### `types` (`java.util.Set<java.lang.String>`)

### `updatedAt` (`java.time.Instant`)

### `version` (`java.lang.Long`)

## Constructors

### `public WorkItem(java.util.UUID id, java.lang.String tenancyId, java.lang.String title, java.lang.String description, java.lang.String formKey, io.casehub.work.api.WorkItemStatus status, io.casehub.work.api.WorkItemPriority priority, java.lang.String assigneeId, java.lang.String owner, java.lang.String candidateGroups, java.lang.String candidateUsers, java.lang.String requiredCapabilities, java.lang.String createdBy, java.lang.String delegationChain, io.casehub.work.api.DeclineTarget delegationDeclineTarget, io.casehub.work.api.WorkItemStatus priorStatus, java.lang.String payload, java.lang.String resolution, java.time.Instant claimDeadline, java.time.Instant expiresAt, java.time.Instant followUpDate, java.time.Instant createdAt, java.time.Instant updatedAt, java.time.Instant assignedAt, java.time.Instant startedAt, java.time.Instant completedAt, java.time.Instant suspendedAt, long accumulatedUnclaimedSeconds, java.time.Instant lastReturnedToPoolAt, java.util.List<io.casehub.work.api.WorkItemLabel> labels, java.util.Set<java.lang.String> types, java.lang.Double confidenceScore, java.lang.String callerRef, java.util.UUID parentId, java.lang.String scope, java.util.UUID templateId, java.lang.Long templateVersion, java.lang.String permittedOutcomes, java.lang.String excludedUsers, java.lang.String outcome, java.lang.String inputDataSchema, java.lang.String outputDataSchema, java.lang.String payloadTypeName, java.lang.String resolutionTypeName, java.lang.String candidateScores, java.lang.String routingExperiences, java.lang.Long version, java.lang.String originServiceId, java.util.UUID originWorkItemId, java.lang.Long originVersion, java.lang.String escalationOnExpiry, java.lang.String escalationOnClaimDeadline, java.lang.String escalationDeadline, java.lang.Boolean escalationGenerateSummary, io.casehub.work.api.CompensationStatus compensationStatus, java.util.UUID compensatesWorkItemId, java.lang.String originRef)`

#### Parameters

- `id` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
- `title` (`java.lang.String`)
- `description` (`java.lang.String`)
- `formKey` (`java.lang.String`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `priority` (`io.casehub.work.api.WorkItemPriority`)
- `assigneeId` (`java.lang.String`)
- `owner` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `candidateUsers` (`java.lang.String`)
- `requiredCapabilities` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `delegationChain` (`java.lang.String`)
- `delegationDeclineTarget` (`io.casehub.work.api.DeclineTarget`)
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
- `accumulatedUnclaimedSeconds` (`long`)
- `lastReturnedToPoolAt` (`java.time.Instant`)
- `labels` (`java.util.List<io.casehub.work.api.WorkItemLabel>`)
- `types` (`java.util.Set<java.lang.String>`)
- `confidenceScore` (`java.lang.Double`)
- `callerRef` (`java.lang.String`)
- `parentId` (`java.util.UUID`)
- `scope` (`java.lang.String`)
- `templateId` (`java.util.UUID`)
- `templateVersion` (`java.lang.Long`)
- `permittedOutcomes` (`java.lang.String`)
- `excludedUsers` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `inputDataSchema` (`java.lang.String`)
- `outputDataSchema` (`java.lang.String`)
- `payloadTypeName` (`java.lang.String`)
- `resolutionTypeName` (`java.lang.String`)
- `candidateScores` (`java.lang.String`)
- `routingExperiences` (`java.lang.String`)
- `version` (`java.lang.Long`)
- `originServiceId` (`java.lang.String`)
- `originWorkItemId` (`java.util.UUID`)
- `originVersion` (`java.lang.Long`)
- `escalationOnExpiry` (`java.lang.String`)
- `escalationOnClaimDeadline` (`java.lang.String`)
- `escalationDeadline` (`java.lang.String`)
- `escalationGenerateSummary` (`java.lang.Boolean`)
- `compensationStatus` (`io.casehub.work.api.CompensationStatus`)
- `compensatesWorkItemId` (`java.util.UUID`)
- `originRef` (`java.lang.String`)

## Methods

### `public long accumulatedUnclaimedSeconds()`

### `public java.time.Instant assignedAt()`

### `public java.lang.String assigneeId()`

### `public static io.casehub.work.api.WorkItem.Builder builder()`

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

### `public java.lang.String escalationDeadline()`

### `public java.lang.Boolean escalationGenerateSummary()`

### `public java.lang.String escalationOnClaimDeadline()`

### `public java.lang.String escalationOnExpiry()`

### `public java.lang.String excludedUsers()`

### `public java.time.Instant expiresAt()`

### `public java.time.Instant followUpDate()`

### `public java.lang.String formKey()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String inputDataSchema()`

### `public java.util.List<io.casehub.work.api.WorkItemLabel> labels()`

### `public java.time.Instant lastReturnedToPoolAt()`

### `public java.lang.String originRef()`

### `public java.lang.String originServiceId()`

### `public java.lang.Long originVersion()`

### `public java.util.UUID originWorkItemId()`

### `public java.lang.String outcome()`

### `public java.lang.String outputDataSchema()`

### `public java.lang.String owner()`

### `public java.util.UUID parentId()`

### `public java.lang.String payload()`

### `public java.lang.String payloadTypeName()`

### `public java.lang.String permittedOutcomes()`

### `public io.casehub.work.api.WorkItemStatus priorStatus()`

### `public io.casehub.work.api.WorkItemPriority priority()`

### `public java.lang.String requiredCapabilities()`

### `public java.lang.String resolution()`

### `public java.lang.String resolutionTypeName()`

### `public java.lang.String routingExperiences()`

### `public java.lang.String scope()`

### `public java.time.Instant startedAt()`

### `public io.casehub.work.api.WorkItemStatus status()`

### `public java.time.Instant suspendedAt()`

### `public java.util.UUID templateId()`

### `public java.lang.Long templateVersion()`

### `public java.lang.String tenancyId()`

### `public java.lang.String title()`

### `public io.casehub.work.api.WorkItem.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.util.Set<java.lang.String> types()`

### `public java.time.Instant updatedAt()`

### `public java.lang.Long version()`
