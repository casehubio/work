# casehub-work

> Human task lifecycle management — WorkItem inbox with status lifecycle, SLA, delegation, escalation, spawn, and audit trail.

**GitHub:** [casehubio/work](https://github.com/casehubio/work)
**Tier:** Foundation

---

## Consumer Guide

### What This Module Does

Provides a human task inbox (`WorkItem`) with a 12-status lifecycle, SLA breach policies, delegation with accept/decline, M-of-N parallel group completion, conflict-of-interest exclusion, and conditional named outcomes. Usable standalone or integrated with CaseHub engine and Qhorus.

A `WorkItem` is deliberately NOT called `Task` — CNCF Serverless Workflow and CaseHub both have `Task` concepts with different semantics.

### Modules to Depend On

| Module | Scope | What you get |
|--------|-------|-------------|
| `casehub-work-api` | compile | All SPIs, WorkItem types, event types. Pure Java — no Quarkus. |
| `casehub-work-core` | compile | `WorkBroker` + built-in selection strategies (LeastLoaded, ClaimFirst, RoundRobin) |
| `runtime` | compile | Full WorkItem entity, services, filter engine. Requires datasource. |
| `rest` | compile (opt-in) | 12 JAX-RS resources — inbox, lifecycle transitions, audit, SLA reports |
| `casehub-work-ledger` | compile (opt-in) | Attaches casehub-ledger for audit entries + trust scoring |
| `casehub-work-queues` | compile (opt-in) | Label-based queue views with filter expressions and trend data |
| `casehub-work-ai` | compile (opt-in) | Embedding-based semantic worker selection |
| `casehub-work-notifications` | compile (opt-in) | Slack/Teams/webhook lifecycle notifications |
| `casehub-work-persistence-mongodb` | compile (opt-in) | MongoDB stores (13 tenant-scoped + 3 cross-tenant). Beats JPA when co-deployed. |
| `casehub-work-persistence-memory` | test | In-memory stores for `@QuarkusTest` isolation. Zero-datasource. |
| `casehub-work-engine-adapter` | compile (opt-in) | Two-way bridge to CaseHub engine PlanItem transitions |

### WorkItem Lifecycle

12 statuses: `PENDING`, `ASSIGNED`, `IN_PROGRESS`, `DELEGATED`, `SUSPENDED`, `COMPLETED`, `REJECTED`, `FAULTED`, `CANCELLED`, `EXPIRED`, `ESCALATED`, `OBSOLETE`.

Always use `isTerminal()` / `isActive()` — never enumerate statuses in consumer code.

**Key fields:**
- `scope` — hierarchical scope path (`Path` type) for SLA preference resolution. Null = org root.
- `types: List<String>` — hierarchical type classification (replaces legacy `category`). REST queries accept `type` param for filtering.
- `callerRef` — opaque string stored and echoed. Convention: `case:{caseId}/pi:{planItemId}` for engine-linked items.

**Delegation:** `DELEGATED` is pre-acceptance — forwarded actor must accept or decline. On decline, item returns to POOL or DELEGATOR per `DeclineTarget` preference.

### SPIs to Implement

17 interfaces in `io.casehub.work.api.spi`:

| SPI | Purpose | Extends NamedStrategy? |
|-----|---------|----------------------|
| `WorkerSelectionStrategy` | Pluggable routing (LeastLoaded default) | ✅ (id: "least-loaded") |
| `SlaBreachPolicy` | Breach handling: Fail / EscalateTo / Extend / Chained | ✅ (id: "no-op") |
| `ClaimSlaPolicy` | Pool-phase deadline computation | ✅ (id: "continuation") |
| `InstanceAssignmentStrategy` | Multi-instance assignment | ✅ (id: "pool") |
| `WorkerRegistry` | Resolves candidateGroup names to candidates | |
| `WorkloadProvider` | Active WorkItem count per worker | |
| `ExclusionPolicy` | Conflict-of-interest user exclusion | |
| `WorkItemCreator` | WorkItem creation and callerRef lookup | |
| `WorkItemLifecycle` | Cancel/complete transitions | |
| `WorkItemObserver` | Lifecycle event observation (synchronous) | |
| `BusinessCalendar` | Business-hours-aware deadline calculation | |
| `HolidayCalendar` | Holiday data sub-SPI | |
| `CapabilityRegistry` | Capability vocabulary validation | |
| `SkillMatcher` | Worker skill scoring | |
| `SkillProfileProvider` | Builds worker SkillProfile | |
| `NotificationChannel` | Outbound notification delivery | |
| `SpawnPort` | Child WorkItem creation with idempotency | |

### Conditional Outcomes

`Outcome(name, displayName, condition)` — the `condition` is a nullable JEXL expression evaluated at completion/rejection. `WorkItem.permittedOutcomes` stores full `Outcome` objects. `OutcomeValidator` encapsulates validation.

### GroupStatus (M-of-N)

`GroupStatus` enum for multi-instance WorkItem groups:
- `IN_PROGRESS` — threshold not yet reached
- `COMPLETED` — majority approval
- `REJECTED` — majority rejection or escalation

`isTerminal()` / `isActive()` methods. Carried by `WorkItemGroupLifecycleEvent`.

### REST API

12 JAX-RS resources in the opt-in `rest` module. Key endpoints:
- Inbox: `GET /workitems` (scan by assignee, candidateUser, candidateGroups)
- Lifecycle: `PUT /workitems/{id}/start`, `/complete`, `/cancel`, `/delegate`
- Templates: `PATCH /workitem-templates/{id}` (RFC 7396 merge-patch)
- Queues: `GET /queues/{id}/trend?period=24h`, `GET /queues/{id}/summary`
- SLA: compliance reports, breach rates

### CDI Events

A lifecycle event fires on every status transition carrying transition details and optional named outcome. Downstream adapters (engine, ledger) observe these to drive plan-item transitions and audit writes.

### Configuration

| Property | Default | What it controls |
|----------|---------|-----------------|
| `casehub.work.delegation.decline-target` | `POOL` | Where declined items return (POOL/DELEGATOR) |
| `casehub.work.sla.*` | — | SLA deadline preferences (per scope via `PreferenceProvider`) |

### Boundary Rules

- Does NOT orchestrate — fires events and provides primitives
- Does NOT do heterogeneous plan-item completion — that is engine (see LAYERING.md). Homogeneous M-of-N IS casehub-work.
- Does NOT interpret `callerRef` — stored and echoed opaquely
- Does NOT provision or manage AI agents
- Does NOT know when to spawn child WorkItems — callers drive spawn via `SpawnPort`

### Flyway Conventions

Migrations at `classpath:db/migration` (runtime) plus per-module ranges:

| Range | Module |
|-------|--------|
| V1–V999 | runtime |
| V2000–V2999 | queues + ledger |
| V3000–V3999 | notifications |
| V4000–V4999 | ai |
| V5000–V5999 | issue-tracker |

If using `casehub-work-ledger`, add `db/ledger/migration` to `quarkus.flyway.locations`.

---

## Contributor Guide

### Module Architecture

| Module | Artifact | Type | Purpose |
|--------|----------|------|---------|
| `api/` | `casehub-work-api` | Pure-Java SPI | All SPIs (17 interfaces), event types (WorkEventType 24+ values), WorkItemRef, CallerRef sealed interface. Depends on `casehub-platform-api`. |
| `core/` | `casehub-work-core` | Jandex library | WorkBroker + selection strategies. No JPA, no Quarkus extension. Engine depends on this. |
| `runtime/` | `casehub-work` | Quarkus extension | WorkItem JPA entity, services, filter engine, CDI event emission |
| `deployment/` | `casehub-work-deployment` | Extension deployment | Build-time `@BuildStep` processor |
| `rest/` | `casehub-work-rest` | Jandex library | 12 JAX-RS resources, DTOs, mappers. Opt-in REST surface. |
| `engine-adapter/` | `casehub-work-engine-adapter` | Bridge | HumanTaskScheduleHandler, WorkItemLifecycleAdapter, ActionGateWorkItemHandler, WorkStrategyContributor, HumanTaskRecoveryService, JpaPlanItemStore. Relocated from engine. |
| `persistence-memory/` | `casehub-work-persistence-memory` | Test | In-memory stores, `@Alternative @Priority(100)`. ConcurrentHashMap (thread-safe). |
| `persistence-mongodb/` | `casehub-work-persistence-mongodb` | Optional | 13 tenant-scoped + 3 cross-tenant MongoDB stores. MongoIndexInitializer. |
| `ledger/` | `casehub-work-ledger` | Optional | Ledger integration for trust scoring |
| `queues/` | `casehub-work-queues` | Optional | Label-based queues with JEXL/JQ filters, trend snapshots (QueueSnapshotJob) |
| `queues-dashboard/` | `casehub-work-queues-dashboard` | Optional | SSE queue dashboard |
| `queues-postgres-broadcaster/` | — | Optional | Distributed SSE via PostgreSQL LISTEN/NOTIFY |
| `ai/` | `casehub-work-ai` | Optional | Semantic worker selection |
| `notifications/` | `casehub-work-notifications` | Optional | Slack/Teams/webhook lifecycle notifications |
| `reports/` | `casehub-work-reports` | Optional | SLA compliance reporting |
| `issue-tracker/` | `casehub-work-issue-tracker` | Optional | GitHub/Jira issue linking |
| `postgres-broadcaster/` | — | Optional | Distributed SSE for WorkItem events |
| `work-flow/` | — | Optional | Quarkus-Flow bridge (HumanTaskFlowBridge) |
| `examples/` | — | Runnable | Demo scenarios |
| `queues-examples/` | — | Runnable | Queue pattern demos |
| `flow-examples/` | — | Runnable | WorkItemsFlow DSL demos |
| `integration-tests/` | — | Test | `@QuarkusIntegrationTest` + native image (25 tests) |
| `integration-tests-memory/` | — | Test | Boot verification through in-memory stores |

### Core/Runtime Split

`casehub-work-core` is a Jandex library (not a Quarkus extension) containing only `WorkBroker` and selection strategies. Engine depends on this — gets worker routing without WorkItem entities, Flyway, or datasource requirements. REST is a separate opt-in module.

### Engine Adapter

The two-way bridge between engine PlanItems and work WorkItems (`casehub-work-engine-adapter`) was relocated from engine. It lives here because the bridge owns the WorkItem entity and transaction boundaries. Contains: `HumanTaskScheduleHandler` (outbound: engine → work), `WorkItemLifecycleAdapter` (inbound: work → engine), `ActionGateWorkItemHandler`/`ActionGateCompletionApplier` (oversight gate bridge), `WorkStrategyContributor` (NamedStrategy registration), `HumanTaskRecoveryService` (startup recovery), `JpaPlanItemStore`.

### Dependencies

**Depends on:** `casehub-platform-api` (Path, Preferences, ActorType). Zero other casehubio deps in core.

**Depended on by:**
- `casehub-engine` — `casehub-work-api` for CaseSignalSink, WorkItemCreator, WorkItemLifecycle. Engine-adapter bridge lives here.
- `casehub-clinical` — Layer 2 adverse event WorkItems with GCP SLA
- `casehub-aml`, `casehub-life`, `casehub-devtown` — WorkItem inbox + SLA

### Notification Concern

`casehub-work-notifications` ships Slack/Teams/webhook directly, overlapping with `casehub-connectors`. Future: delegate to connectors SPI.

### Current State

Core lifecycle, SPI extraction, MongoDB persistence, queue trends, engine-adapter relocation, NamedStrategy retrofit (4 SPIs), WorkItemObserver, template versioning: all shipped.

Pending: `casehub-work-qhorus` adapter (MCP tools for agent-driven approval flows).

### Design Documents

- [ARC42STORIES.MD](https://raw.githubusercontent.com/casehubio/work/main/docs/ARC42STORIES.MD) — domain model, SPI contracts, status enumeration, service class structure
- [adr/INDEX.md](https://raw.githubusercontent.com/casehubio/work/main/adr/INDEX.md) — architectural decision records
