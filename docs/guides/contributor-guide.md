# casehub-work — Contributor Guide

> Internals, architecture, and extension points for platform builders working on casehub-work itself.

**GitHub:** [casehubio/work](https://github.com/casehubio/work)

---

## Module Architecture

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

## Core/Runtime Split

`casehub-work-core` is a Jandex library (not a Quarkus extension) containing only `WorkBroker` and selection strategies. Engine depends on this — gets worker routing without WorkItem entities, Flyway, or datasource requirements. REST is a separate opt-in module.

## Engine Adapter

The two-way bridge between engine PlanItems and work WorkItems (`casehub-work-engine-adapter`) was relocated from engine. It lives here because the bridge owns the WorkItem entity and transaction boundaries. Contains: `HumanTaskScheduleHandler` (outbound: engine → work), `WorkItemLifecycleAdapter` (inbound: work → engine), `ActionGateWorkItemHandler`/`ActionGateCompletionApplier` (oversight gate bridge), `WorkStrategyContributor` (NamedStrategy registration), `HumanTaskRecoveryService` (startup recovery), `JpaPlanItemStore`.

## Dependencies

**Depends on:** `casehub-platform-api` (Path, Preferences, ActorType). Zero other casehubio deps in core.

**Depended on by:**
- `casehub-engine` — `casehub-work-api` for CaseSignalSink, WorkItemCreator, WorkItemLifecycle. Engine-adapter bridge lives here.
- `casehub-clinical` — Layer 2 adverse event WorkItems with GCP SLA
- `casehub-aml`, `casehub-life`, `casehub-devtown` — WorkItem inbox + SLA

## Notification Concern

`casehub-work-notifications` ships Slack/Teams/webhook directly, overlapping with `casehub-connectors`. Future: delegate to connectors SPI.

## Current State

Core lifecycle, SPI extraction, MongoDB persistence, queue trends, engine-adapter relocation, NamedStrategy retrofit (4 SPIs), WorkItemObserver, template versioning: all shipped.

Pending: `casehub-work-qhorus` adapter (MCP tools for agent-driven approval flows).

## Design Documents

- [ARC42STORIES.MD](https://raw.githubusercontent.com/casehubio/work/main/docs/ARC42STORIES.MD) — domain model, SPI contracts, status enumeration, service class structure
- [adr/INDEX.md](https://raw.githubusercontent.com/casehubio/work/main/adr/INDEX.md) — architectural decision records
