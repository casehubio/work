# casehub-work — Consumer Guide

> Human task lifecycle management — WorkItem inbox with status lifecycle, SLA, delegation, escalation, spawn, M-of-N group completion, conflict-of-interest exclusion, conditional named outcomes, progress tracking, and audit trail.

**GitHub:** [casehubio/work](https://github.com/casehubio/work)
**Tier:** Foundation

---

## What This Module Does

Provides a human task inbox (`WorkItem`) with an 11-status lifecycle, SLA breach policies, delegation with accept/decline, M-of-N parallel group completion, conflict-of-interest exclusion, conditional named outcomes, and hierarchical progress tracking. Usable standalone or integrated with CaseHub engine, Quarkus-Flow, and Qhorus.

A `WorkItem` is deliberately NOT called `Task` — CNCF Serverless Workflow and CaseHub both have `Task` concepts with different semantics.

## Modules to Depend On

| Module | Artifact | Scope | What you get |
|--------|----------|-------|-------------|
| `api/` | `casehub-work-api` | compile | All SPIs (17 interfaces), WorkItem types, event types, `WorkItemCreateRequest`, `WorkItemRef`, `SelectionContext`, `BreachDecision` sealed hierarchy. Pure Java — no Quarkus. |
| `core/` | `casehub-work-core` | compile | `WorkBroker` + 3 selection strategies (LeastLoaded, ClaimFirst, RoundRobin), 4 claim SLA policies, `CapabilityValidator`, `RoutingCursorStore`. No JPA, no Quarkus extension. |
| `runtime/` | `casehub-work` | compile | Full WorkItem JPA entity, `WorkItemService`, `WorkItemTemplateService`, filter engine (`LabelRuleEngine`), CDI event emission, `WorkPreferenceRegistrar`, timer jobs, business calendar. Requires datasource. |
| `rest/` | `casehub-work-rest` | compile (opt-in) | JAX-RS resources — WorkItem CRUD, lifecycle transitions, templates, labels, audit, spawn groups, schedules, relations, bulk operations, vocabulary, label rules, AsyncAPI spec. |
| `ledger/` | `casehub-work-ledger` | compile (opt-in) | Attaches casehub-ledger for immutable audit entries, trust scoring, provenance, and attestation. |
| `queues/` | `casehub-work-queues` | compile (opt-in) | Label-based queue views with LabelRule filter expressions, queue state tracking, trend snapshots (`QueueSnapshotJob`), and queue membership. |
| `queues-dashboard/` | `casehub-work-queues-dashboard` | compile (opt-in) | SSE-driven queue dashboard with TUI rendering. |
| `ai/` | `casehub-work-ai` | compile (opt-in) | Semantic worker selection (`SemanticWorkerSelectionStrategy`), embedding-based skill matching, escalation summaries, resolution suggestions, low-confidence queue filtering, worker skill profiles. |
| `notifications/` | `casehub-work-notifications` | compile (opt-in) | Slack, Teams, and HTTP webhook lifecycle notifications with configurable rules. |
| `reports/` | `casehub-work-reports` | compile (opt-in) | SLA compliance reporting — breach reports, throughput buckets, actor reports, queue health. |
| `issue-tracker/` | `casehub-work-issue-tracker` | compile (opt-in) | GitHub and Jira issue linking — webhook-based bidirectional sync, `IssueTrackerProvider` SPI, `WorkItemIssueLink` entity, `NormativeResolution` vocabulary. |
| `flow/` | `casehub-work-flow` | compile (opt-in) | Quarkus-Flow bridge — `WorkItemsFlow` base class with `workItem()` DSL for workflow definitions, `HumanTaskFlowBridge` for programmatic human-in-the-loop suspension. |
| `engine-adapter/` | `casehub-work-engine-adapter` | compile (opt-in) | Two-way bridge to CaseHub engine PlanItem transitions — `HumanTaskScheduleHandler`, `WorkItemLifecycleAdapter`, action gate handlers, recovery service. |
| `progress-api/` | `casehub-work-progress-api` | compile (opt-in) | Progress model API — `ProgressInstance`, `StepDefinition`, `RollupStrategy` SPI, `ProgressUpdatedEvent`, event/instance stores. Pure Java. |
| `progress-core/` | `casehub-work-progress-core` | compile (opt-in) | Rollup strategies (AveragePercentage, CountCompleted, WeightedPercentage), shape validators, rollback detection. |
| `progress-runtime/` | `casehub-work-progress-runtime` | compile (opt-in) | `ProgressService`, JPA entities (`ProgressInstanceEntity`, `ProgressEventEntity`), SSE broadcasting, rollup observer. Requires datasource. |
| `progress-rest/` | `casehub-work-progress-rest` | compile (opt-in) | REST endpoints for progress tracking — create, update state, complete, fail, reactivate, attach children, step lifecycle, SSE streaming, tree queries. |
| `persistence-mongodb/` | `casehub-work-persistence-mongodb` | compile (opt-in) | MongoDB store implementations for all runtime repository interfaces. Tenant-scoped. |
| `persistence-memory/` | `casehub-work-persistence-memory` | test | In-memory stores for `@QuarkusTest` isolation. ConcurrentHashMap-backed, thread-safe, zero-datasource. |
| `progress-memory/` | `casehub-work-progress-memory` | test | In-memory stores for progress model testing (`InMemoryProgressInstanceStore`, `InMemoryProgressEventStore`). |
| `postgres-broadcaster/` | — | compile (opt-in) | Distributed SSE for WorkItem lifecycle events via PostgreSQL LISTEN/NOTIFY. |
| `queues-postgres-broadcaster/` | — | compile (opt-in) | Distributed SSE for queue events via PostgreSQL LISTEN/NOTIFY. |

## WorkItem Lifecycle

11 statuses in `WorkItemStatus`:

| Status | Terminal? | Active? | Description |
|--------|-----------|---------|-------------|
| `PENDING` | no | yes | Created but not yet assigned |
| `ASSIGNED` | no | yes | Assigned to a specific actor; work not started |
| `IN_PROGRESS` | no | yes | Actively being worked |
| `DELEGATED` | no | yes | Forwarded to named actor for acceptance (pre-acceptance hold) |
| `SUSPENDED` | no | yes | Temporarily suspended; resumes to `priorStatus` |
| `COMPLETED` | yes | no | Completed successfully |
| `REJECTED` | yes | no | Rejected by assignee or reviewer |
| `FAULTED` | yes | no | System/infrastructure failure (not a human decision) |
| `CANCELLED` | yes | no | Cancelled before completion |
| `EXPIRED` | yes | no | Deadline passed without resolution |
| `ESCALATED` | yes | no | Escalated due to expiry or policy breach exhaustion |
| `OBSOLETE` | yes | no | Superseded by context change (distinct from CANCELLED) |

Always use `isTerminal()` / `isActive()` — never enumerate statuses in consumer code. The static field `WorkItemStatus.TERMINAL_STATUSES` provides a derived list for queries.

## Key Fields on WorkItem

| Field | Type | Purpose |
|-------|------|---------|
| `scope` | `String` (slash-separated path) | Hierarchical scope for SLA preference resolution. Null = org root. |
| `types` | `Set<WorkItemType>` | Hierarchical type classification paths (e.g. `"approval"`, `"compliance/audit"`). REST queries accept `type` param for filtering. |
| `callerRef` | `String` (max 512) | Opaque caller-supplied routing key. Convention: `case:{caseId}/pi:{planItemId}` for engine-linked items. Stored and echoed — never interpreted. |
| `parentId` | `UUID` | Parent WorkItem for M-of-N groups. Null for standalone items. |
| `labels` | `List<WorkItemLabel>` | Labels with `LabelPersistence` (MANUAL or INFERRED by filter engine). |
| `confidenceScore` | `Double` (0.0-1.0) | AI confidence metadata. Null for human-created items. |
| `delegationChain` | `String` (CSV) | Comma-separated actor IDs of delegation history (most recent last). |
| `delegationDeclineTarget` | `DeclineTarget` | Instance-level override for decline routing (POOL or DELEGATOR). |
| `priorStatus` | `WorkItemStatus` | Status snapshot before suspend; restored on resume. |
| `permittedOutcomes` | `String` (JSON array) | Snapshotted from template at instantiation. Null = any outcome accepted. |
| `outcome` | `String` | Named outcome recorded at completion. Validated against `permittedOutcomes`. |
| `inputDataSchema` / `outputDataSchema` | `String` (JSON Schema draft-07) | Payload and resolution validation schemas from template. |
| `payloadTypeName` / `resolutionTypeName` | `String` | Type discriminators for payload and resolution data. |
| `candidateScores` | `String` (JSON) | Per-candidate routing scores from selection strategy. |
| `routingExperiences` | `String` (JSON) | Historical routing experience data for learning-based selection. |
| `templateId` / `templateVersion` | `UUID` / `Long` | Template provenance for audit trail. |
| `version` | `Long` | JPA optimistic locking — drives HTTP 409 Conflict on concurrent modification. |
| `tenancyId` | `String` | Mandatory tenant identifier for multi-tenant isolation. |

## WorkItemRef — API-Safe Reference

`WorkItemRef` is a lightweight record carried across module boundaries:

```java
public record WorkItemRef(
    UUID id, WorkItemStatus status, String callerRef, String assigneeId,
    String resolution, String candidateGroups, String outcome,
    String tenancyId, String payload, String payloadTypeName, String resolutionTypeName
) {}
```

## WorkItemCreateRequest

Builder-pattern request object for creating WorkItems. All fields are optional except `title` (required unless `templateId` is set). Key fields include `types`, `labels`, `scope`, `permittedOutcomes`, `candidateScores`, `routingExperiences`, `payloadTypeName`, `resolutionTypeName`, `tenancyId`, `auditDetail`, and `templateVersion`.

## SPIs to Implement

17 interfaces in `io.casehub.work.api.spi`:

| SPI | Purpose | Extends NamedStrategy? | Default |
|-----|---------|----------------------|---------|
| `WorkerSelectionStrategy` | Pluggable worker routing — receives `SelectionContext` + `List<WorkerCandidate>`, returns `AssignmentDecision`. Configurable `triggers()` subset. | yes | "least-loaded" |
| `SlaBreachPolicy` | Breach handling — returns sealed `BreachDecision` hierarchy: `Fail`, `EscalateTo`, `Extend`, `Exhausted`, `Chained`. | yes | "no-op" |
| `ClaimSlaPolicy` | Pool-phase deadline computation — how long an unclaimed item gets before escalation. 4 built-in: continuation, fresh-clock, single-budget, phase-clock. | yes | "continuation" |
| `InstanceAssignmentStrategy` | Multi-instance child assignment. 4 built-in: pool, round-robin, explicit, composite. | yes | "pool" |
| `WorkerRegistry` | Resolves candidateGroup names to `List<WorkerCandidate>`. Connect LDAP, Keycloak, or any directory. | no | empty list (NoOp) |
| `WorkloadProvider` | Active WorkItem count per worker for load-balanced selection. | no | JPA-backed |
| `ExclusionPolicy` | Conflict-of-interest user exclusion — returns `PolicyDecision` (ALLOW/deny with reason). | no | comma-separated CSV check |
| `WorkItemCreator` | WorkItem creation, `findByCallerRef()`, `findActiveByCallerRef()`, `obsoleteByCallerRef()`, `createMultiInstance()`, `findChildApprovers()`. | no | — |
| `WorkItemLifecycle` | Cancel/complete transitions with extended `complete()` accepting `rationale` and `planRef`. | no | — |
| `WorkItemObserver` | Synchronous lifecycle event observation via `WorkItemStatusEvent`. Runs in emitter's transaction. | no | — |
| `BusinessCalendar` | Business-hours-aware deadline calculation. | no | config-driven |
| `HolidayCalendar` | Holiday data sub-SPI — static list or iCal feed. | no | config-driven |
| `CapabilityRegistry` | Capability vocabulary validation (STRICT/WARN/PERMISSIVE). | no | permissive |
| `SkillMatcher` | Worker skill scoring against WorkItem requirements. | no | — |
| `SkillProfileProvider` | Builds worker `SkillProfile` for AI-based selection. | no | — |
| `NotificationChannel` | Outbound notification delivery (Slack, Teams, webhook). | no | — |
| `SpawnPort` | Child WorkItem creation with `spawn(SpawnRequest)` and `cancelGroup(UUID, boolean)`. | no | — |

### Progress Model SPIs

In `io.casehub.work.progress` and `io.casehub.work.progress.spi`:

| SPI | Purpose |
|-----|---------|
| `RollupStrategy` | Computes aggregate state from parent + children context. Extends `NamedStrategy`. Built-in: `AveragePercentageStrategy`, `CountCompletedStrategy`, `WeightedPercentageStrategy`. |
| `ProgressInstanceStore` | CRUD + scope/parent queries for `ProgressInstance`. |
| `ProgressEventStore` | Append-only event store for `ProgressUpdatedEvent` with time-range queries. |
| `ConditionEvaluator` | Evaluates step condition expressions against JSON context. |

### Issue Tracker SPIs

In `io.casehub.work.issuetracker.spi`:

| SPI | Purpose |
|-----|---------|
| `IssueTrackerProvider` | Abstraction for external issue trackers. Built-in: `GitHubIssueTrackerProvider`. |

## Lifecycle Events

26 event types in `WorkEventType`:

`CREATED`, `ASSIGNED`, `STARTED`, `COMPLETED`, `REJECTED`, `FAULTED`, `DELEGATED`, `DELEGATION_ACCEPTED`, `DELEGATION_DECLINED`, `RELEASED`, `SUSPENDED`, `RESUMED`, `CANCELLED`, `OBSOLETE`, `EXPIRED`, `CLAIM_EXPIRED`, `SPAWNED`, `ESCALATED`, `DEADLINE_EXTENDED`, `SLA_REASSIGNED`, `SLA_EXTENDED`, `SIGNAL_RECEIVED`, `MANUALLY_ESCALATED`, `PROGRESS_UPDATE`, `LABEL_ADDED`, `LABEL_REMOVED`.

CDI events fire on every status transition via `WorkItemLifecycleEmitter`. Downstream adapters (engine, ledger, notifications) observe these events.

## CloudEvent Types

All lifecycle events have corresponding CloudEvent type URIs in `WorkCloudEventTypes`:

- Prefix: `io.casehub.work.workitem.*` (e.g. `io.casehub.work.workitem.created`)
- Group events: `io.casehub.work.group.*`
- Inbound: `io.casehub.work.workitem.requested` (creation via CloudEvent, not a lifecycle event)
- Extension attributes: `tenancyid`, `templateid`

`WorkCloudEventAdapter` bridges `WorkItemLifecycleEvent` to CloudEvent; `WorkCloudEventInboundAdapter` handles inbound creation requests.

## BreachDecision — Sealed Hierarchy

`SlaBreachPolicy.onBreach()` returns one of:

| Variant | Effect |
|---------|--------|
| `Fail(reason)` | Terminates WorkItem with EXPIRED status |
| `EscalateTo(groups, deadline)` | Reassigns to new groups with optional deadline override |
| `Extend(by)` | Extends the active deadline by a duration |
| `Exhausted(reason)` | All policy branches exhausted — transitions to ESCALATED (terminal) |
| `Chained(primary, fallback)` | Try primary; if it fails, try fallback. Build with `.thenOnBreach()`. |

## Conditional Outcomes

`Outcome(name, displayName, condition)` — the `condition` is a nullable JEXL expression evaluated at completion/rejection. `WorkItem.permittedOutcomes` stores the JSON array snapshotted from the template. `OutcomeValidator` encapsulates validation.

## GroupStatus (M-of-N)

`GroupStatus` enum for multi-instance WorkItem groups:
- `IN_PROGRESS` — threshold not yet reached
- `COMPLETED` — majority approval
- `REJECTED` — majority rejection or escalation

`isTerminal()` / `isActive()` methods. Carried by `WorkItemGroupLifecycleEvent`.

`MultiInstanceConfig` governs group creation with `instanceCount`, `requiredCount`, `parentRole` (COORDINATOR/PARTICIPANT), `assignmentStrategyName`, `onThresholdReached` (KEEP/CANCEL_REMAINING), `allowSameAssignee`, and `explicitAssignees`.

## WorkItem Relations

Directed graph between WorkItems via `WorkItemRelation`:

| Relation Type | Direction | Purpose |
|---------------|-----------|---------|
| `PART_OF` | child -> parent | Tree hierarchy, group summaries, epic-style views |
| `BLOCKS` / `BLOCKED_BY` | source -> target | Dependency tracking (inverse pair) |
| `RELATES_TO` | bidirectional by convention | Contextual association |
| `DUPLICATES` | duplicate -> canonical | Deduplication tracking |

Custom relation types are plain strings — no registration or schema change needed.

## WorkItem Links

External resource references via `WorkItemLink`:

| Link Type | Purpose |
|-----------|---------|
| `reference` | General resource reference |
| `design-spec` | Architecture decisions, API specs, wireframes |
| `policy` | Regulations, governance documents |
| `evidence` | Supporting evidence (model outputs, test results) |
| `source-document` | Original source material |
| `attachment` | Files stored externally (S3, GCS, MinIO) — URL reference only |

Custom link types are plain strings — use any non-blank string.

## Delegation

`DELEGATED` is pre-acceptance — the target actor must `acceptDelegation()` or `declineDelegation()`. On decline, the item returns to POOL or DELEGATOR per the `DeclineTarget` preference (instance-level override or scope preference). The `delegationChain` tracks the full delegation history as comma-separated actor IDs.

Cross-system note: `WorkItemStatus.DELEGATED` differs from `CommitmentState.DELEGATED` (casehub-qhorus, which is terminal) and `PlanItemStatus.DELEGATED` (casehub-engine, which is broader).

## Progress Model

Platform-level observation and reporting system, independent of WorkItems. Tracks progress as scoped instances with pluggable shape types.

**Key types:**
- `ProgressInstance` — record with `id`, `tenancyId`, `scopeType`, `scopeId`, `parentProgressId`, `rootProgressId`, `shapeType`, JSON `definition` and `state`, `ProgressStatus`, `rollupStrategyId`
- `ProgressStatus` — `PENDING`, `ACTIVE`, `COMPLETED`, `FAILED`. Check with `isQuiescent()` / `isActive()`.
- `StepDefinition` — named step with optional flag, `dependsOn` list, and condition expression
- `ProgressChangeType` — `CREATED`, `STATE_UPDATED`, `CHILD_ATTACHED`, `COMPLETED`, `FAILED`, `REACTIVATED`, `ROLLED_BACK`
- `ProgressUpdatedEvent` — event-sourced change record with previous/current state, scope context, and timestamp

**Rollup strategies:** parent progress auto-computes from children via `RollupStrategy` SPI. Built-in: `AveragePercentageStrategy`, `CountCompletedStrategy`, `WeightedPercentageStrategy`.

**Validation:** `ShapeValidator` dispatches to `PercentageValidator`, `CountValidator`, `StepValidator`/`StepShapeValidator`, `StepDefinitionValidator`. `RollbackDetector` flags non-monotonic progress.

## Quarkus-Flow Integration

The `flow/` module provides a DSL for embedding human tasks in Quarkus-Flow workflow definitions:

```java
@ApplicationScoped
public class DocumentApprovalWorkflow extends WorkItemsFlow {
    @Override
    public Workflow descriptor() {
        return workflow("document-approval")
                .tasks(
                        workItem("legalReview")
                                .title("Legal review required")
                                .candidateGroups("legal-team")
                                .priority(WorkItemPriority.HIGH)
                                .payloadFrom((DocumentDraft d) -> d.toJson())
                                .buildTask(DocumentDraft.class))
                .build();
    }
}
```

`WorkItemsFlow` extends Quarkus-Flow's `Flow` base class. `workItem()` creates a `WorkItemTaskBuilder`. `fn()` wraps automated function steps at the same visual level. `HumanTaskFlowBridge` provides lower-level programmatic access via `requestApproval()` and `requestGroupApproval()` — both return `Uni<String>` that suspends the workflow until a human resolves the WorkItem.

## REST API

JAX-RS resources in the `rest/` module:

| Resource | Key Endpoints |
|----------|---------------|
| `WorkItemResource` | `POST /workitems` (create), `GET /workitems` (scan by assignee, candidateUser, candidateGroups, type), lifecycle transitions: `PUT /workitems/{id}/start`, `/complete`, `/cancel`, `/reject`, `/delegate`, `/escalate`, `/suspend`, `/resume`, `/fault`, `/obsolete`, `/extend` |
| `WorkItemTemplateResource` | `PATCH /workitem-templates/{id}` (RFC 7396 merge-patch) |
| `WorkItemBulkResource` | Batch lifecycle operations |
| `WorkItemInstancesResource` | Multi-instance group management |
| `WorkItemSpawnResource` | Spawn child WorkItems |
| `SpawnGroupResource` | Spawn group queries |
| `WorkItemRelationResource` | Create/query WorkItem relations |
| `WorkItemScheduleResource` | Scheduled WorkItem management |
| `AuditResource` | Audit trail queries |
| `LabelRuleResource` | CRUD for label rules (platform LabelRule) |
| `VocabularyResource` | Label vocabulary management |
| `AsyncApiResource` | AsyncAPI specification endpoint |

**Queue endpoints** (queues module):
| Resource | Key Endpoints |
|----------|---------------|
| `QueueResource` | `GET /queues/{id}/trend?period=24h`, `GET /queues/{id}/summary` |
| `QueueStateResource` | Queue membership and state queries |

**Report endpoints** (reports module):
| Resource | Key Endpoints |
|----------|---------------|
| `ReportResource` | SLA compliance reports, breach rates, throughput, actor reports, queue health |

**AI endpoints** (ai module):
| Resource | Key Endpoints |
|----------|---------------|
| `WorkerSkillProfileResource` | Worker profile CRUD |
| `EscalationSummaryResource` | AI-generated escalation summaries |
| `ResolutionSuggestionResource` | Resolution suggestions based on historical data |

**Issue tracker endpoints** (issue-tracker module):
| Resource | Key Endpoints |
|----------|---------------|
| `IssueLinkResource` | WorkItem-to-issue link CRUD |
| `GitHubWebhookResource` | GitHub webhook receiver |
| `JiraWebhookResource` | Jira webhook receiver |

**Progress endpoints** (progress-rest module):
| Resource | Key Endpoints |
|----------|---------------|
| `ProgressResource` | `POST /progress` (create), `PUT /progress/{id}/state`, `POST /progress/{id}/complete`, `/fail`, `/reactivate`, `POST /progress/{id}/children`, `GET /progress/{id}/tree`, `GET /progress/{id}/events`, `GET /progress/{id}/stream` (SSE), step endpoints: `POST /progress/{id}/steps/{stepName}/start`, `/complete`, `/skip`, `/fail`, `PUT /progress/{id}/steps/{stepName}/state` |

**Ledger endpoints** (ledger module):
| Resource | Key Endpoints |
|----------|---------------|
| `LedgerResource` | Ledger entry queries, provenance, attestation |
| `ActorTrustResource` | Actor trust score queries |

## Configuration

All properties prefixed with `casehub.work`:

| Property | Default | What it controls |
|----------|---------|-----------------|
| `casehub.work.default-expiry-hours` | `24` | Default completion deadline hours |
| `casehub.work.default-claim-hours` | `4` | Default claim deadline hours. 0 disables. |
| `casehub.work.cleanup.expiry-check-seconds` | `60` | Expiry/claim-deadline poll interval |
| `casehub.work.routing.strategy` | `least-loaded` | Worker selection strategy ID |
| `casehub.work.routing.cursor.ttl-days` | `30` | Round-robin cursor TTL |
| `casehub.work.routing.cursor.cleanup-cron` | `0 0 2 * * ?` | Cursor GC schedule (Quartz format). "disabled" skips. |
| `casehub.work.sla.claim-policy` | `continuation` | Claim SLA policy ID |
| `casehub.work.sla.breach-policy` | `no-op` | SLA breach policy ID |
| `casehub.work.capability-validation` | `PERMISSIVE` | Capability validation mode (STRICT/WARN/PERMISSIVE) |
| `casehub.work.business-hours.timezone` | `UTC` | Business hours timezone (IANA) |
| `casehub.work.business-hours.start` | `09:00` | Business day start |
| `casehub.work.business-hours.end` | `17:00` | Business day end |
| `casehub.work.business-hours.work-days` | `MON,TUE,WED,THU,FRI` | Working days |
| `casehub.work.business-hours.holidays` | — | Static holiday dates (YYYY-MM-DD, comma-separated) |
| `casehub.work.business-hours.holiday-ical-url` | — | iCal feed URL for holidays (overrides static list) |
| `casehub.work.delegation.decline-target` | `POOL` | Where declined delegations return (POOL/DELEGATOR) — per scope via `PreferenceProvider` |

**Preference keys** (scope-resolved via platform `PreferenceProvider`):
| Key | Namespace | Default |
|-----|-----------|---------|
| `sla.default-hours` | `casehub.work` | 24 |
| `sla.default-claim-hours` | `casehub.work` | 4 |

## Boundary Rules

- Does NOT orchestrate — fires events and provides primitives
- Does NOT do heterogeneous plan-item completion — that is engine (see LAYERING.md). Homogeneous M-of-N IS casehub-work.
- Does NOT interpret `callerRef` — stored and echoed opaquely
- Does NOT provision or manage AI agents
- Does NOT know when to spawn child WorkItems — callers drive spawn via `SpawnPort`
- Progress model is scope-generic — not tied to WorkItems (any scopeType/scopeId pair works)

## Flyway Conventions

All migrations in `db/work/migration`:

| Range | Module |
|-------|--------|
| V1-V44 | runtime (core schema, labels, vocabulary, notes, templates, relations, schedules, links, outcomes, types, routing) |
| V1000-V1004 | shared ledger base schema |
| V2000-V2004 | queues (schema, membership, tenancy, snapshots) |
| V2001 | ledger (work-item ledger entries) |
| V3000-V3002 | notifications (rules, tenancy, types rename) |
| V4001-V4002 | ai (escalation summaries, tenancy) |
| V5003-V5004 | queues subject view migration + label rule schema |
| V6000-V6002 | issue-tracker (issue links, priority rename, tenancy) |
| V7000-V7001 | progress (schema, drop legacy progress fields from work_item) |

Runtime migration V14 (`worker_skill_profile`) is in the `ai` module's resources for historical reasons.
