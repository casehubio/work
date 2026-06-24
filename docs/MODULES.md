# Module Map

Use `ide_find_class` / `ide_find_symbol` to locate specific classes. This file documents module ownership and structural constraints that the IDE can't tell you.

## Core Modules

| Module | Purpose | Key constraints |
|---|---|---|
| `api/` | Pure-Java SPI — no JPA, no REST | All SPIs, events, value objects. casehub-engine depends on this directly. |
| `core/` | Jandex library — no JPA, no REST | WorkBroker, built-in strategies, claim SLA policies; pure CDI. No filter classes — filter engine moved to `runtime/filter/` in #133. |
| `runtime/` | Extension runtime | WorkItem entity, JPA stores, filter engine, multi-instance coordinator, REST endpoints at `/workitems`, CloudEvent adapter (`WorkCloudEventAdapter`), dual-channel emitters (`WorkItemLifecycleEmitter`, `WorkItemGroupLifecycleEmitter`) |
| `deployment/` | Extension build-time | `WorkItemsProcessor` @BuildStep only |
| `persistence-memory/` | In-memory persistence (`casehub-work-persistence-memory`) | Thread-safe ConcurrentHashMap stores; Tier 3 `@Alternative @Priority(100)` — beats JPA and MongoDB. For tests, demos, and ephemeral deployment. |
| `docs/` | Architecture, design, specs | `ARCHITECTURE.md` (SPI contracts), `DESIGN.md` (roadmap + Flyway history), `GOTCHAS.md`, `FLYWAY.md` |
| `scripts/` | Build helpers | See `scripts/README.md` for usage and expected test times |

## Integration Modules (built)

| Module | Purpose |
|---|---|
| `flow/` | Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`) |
| `flow-examples/` | Example scenarios for the Quarkus-Flow integration |
| `ledger/` | Optional accountability module (command/event ledger, hash chain, attestation, EigenTrust) |
| `queues/` | Optional label-based queue module; label filter chains, queue views, JEXL/JQ expression evaluation |
| `queues-dashboard/` | Optional queue dashboard UI |
| `queues-examples/` | Example scenarios for the queue module |
| `ai/` | AI-native features; confidence gating via `LowConfidenceFilterProducer`; `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1)) for embedding-based worker scoring |
| `notifications/` | Optional outbound notification module; CDI observer dispatches to HTTP webhook, Slack, and Teams channels. Flyway V3000. |
| `reports/` | Optional SLA compliance reporting; `/reports/sla-breaches`, `/actors/{id}`, `/throughput`, `/queue-health` |
| `postgres-broadcaster/` | Optional distributed SSE; PostgreSQL LISTEN/NOTIFY for WorkItem events (`casehub_work_events`) |
| `queues-postgres-broadcaster/` | Optional distributed SSE for queue events (`casehub_work_queue_events`); depends on `casehub-work-queues` + `quarkus-reactive-pg-client` |
| `issue-tracker/` | Optional issue-tracker link module; `IssueTrackerProvider` SPI; GitHub and Jira webhook handlers. Flyway V5000. |
| `examples/` | Runnable scenario demos; each runs via `POST /examples/{name}/run` |
| `persistence-mongodb/` | Optional MongoDB-backed stores for all runtime/core/issue-tracker SPIs; Tier 2 `@Alternative @Priority(1)`. Drop-in replacement for JPA — add to classpath, no consumer changes needed. |
| `integration-tests/` | `@QuarkusIntegrationTest` suite and native image validation |
| `integration-tests-memory/` | Ephemeral deployment ITs — `persistence-memory` stores with dummy H2 datasource (no Flyway, no schema); verifies full CRUD without a real database |
