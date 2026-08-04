# Qhorus WorkItem Bridge — Design Spec

**Issue:** casehubio/work#97
**Date:** 2026-08-04
**Status:** Approved

## Problem

`WorkItemLifecycleEvent` and `WorkItemQueueEvent` are CDI events — they fire in-process only. In a distributed system, service B cannot observe WorkItem transitions in service A. The Qhorus blockers (casehubio/qhorus#131 Channel abstraction, casehubio/qhorus#132 delivery guarantees) are now closed.

## Approach

A new `casehub-work-qhorus` module providing a thin MCP-tool bridge between casehub-work and Qhorus. Three MCP tools give agents an explicit, discoverable interface for requesting and tracking human work. A `WorkItemObserver` SPI implementation posts terminal speech acts back to the originating Qhorus channel.

No automatic channel observation. No new entities or migrations. No new SPIs.

## Module Structure

**Location:** `casehub-work-qhorus/` in the work repo root (alongside `engine-adapter/`, `flow/`)
**Artifact:** `casehub-work-qhorus`
**Package:** `io.casehub.work.qhorus`
**Activation:** Classpath presence — consumer adds the dependency; CDI discovers the beans

### Dependencies

| Scope | Artifact | Why |
|---|---|---|
| compile | `casehub-work-api` | `WorkItemCreator`, `WorkItemObserver`, `WorkItemStatusEvent`, `WorkItemRef` |
| compile | `casehub-qhorus` | `MessageService.dispatch()` for posting to channels |
| compile | `quarkus-mcp-server-http` | `@Tool` annotation |
| compile | `quarkus-arc` | CDI |
| test | `casehub-work`, `casehub-work-deployment`, `casehub-work-persistence-memory` | Full runtime for integration tests |
| test | `casehub-qhorus`, `casehub-qhorus-persistence-memory` | Full runtime for integration tests |
| test | `quarkus-junit`, `quarkus-jdbc-h2`, `assertj-core`, `awaitility` | Test infrastructure |

Runtime requires both casehub-work and casehub-qhorus on the classpath.

## CallerRef Correlation

The existing `callerRef` field on WorkItem is the correlation mechanism, following the engine adapter's prefixed format (`case:{caseId}/pi:{planItemId}`).

### Format

```
qhorus:{channelName}/{correlationId}
```

Example: `qhorus:case-abc/oversight/550e8400-e29b-41d4-a716-446655440000`

### QhorusCallerRef

Utility record in `io.casehub.work.qhorus`:

```java
public record QhorusCallerRef(String channelName, String correlationId) {
    public static final String PREFIX = "qhorus:";

    public static boolean isQhorus(String callerRef) {
        return callerRef != null && callerRef.startsWith(PREFIX);
    }

    public static QhorusCallerRef parse(String callerRef) {
        // Strip prefix, split on last '/' to separate channel name from correlationId
        // Channel names may contain '/' (e.g. "case-abc/oversight")
    }

    public String encode() {
        return PREFIX + channelName + "/" + correlationId;
    }
}
```

The outbound adapter checks `isQhorus()` on every `WorkItemStatusEvent`. Non-Qhorus WorkItems are ignored.

## MCP Tools

`WorkQhorusMcpTools` — `@ApplicationScoped` bean with `@Tool`-annotated methods. Discovered by the same `quarkus-mcp-server-http` endpoint alongside `QhorusMcpTools`.

### request_human_work

Creates a WorkItem and posts an oversight/QUERY to the originating channel.

**Parameters:** `channel` (channel name), `title`, `description`, `candidate_groups` (optional), `priority` (optional), `payload` (optional JSON), `template_id` (optional), `sender` (requesting agent's instance ID).

**Flow:**
1. Generate `correlationId` (UUID)
2. Build `callerRef = QhorusCallerRef.encode(channel, correlationId)`
3. Create WorkItem via `WorkItemCreator.create()` — `createdBy = "qhorus:" + sender`, `tenancyId` from `CurrentPrincipal`
4. Post oversight/QUERY to the named channel via `MessageService.dispatch()` with the correlationId
5. Return `HumanWorkResponse(workItemId, callerRef, correlationId, status)`

If WorkItem creation succeeds but the channel post fails: log at WARN, return success. The WorkItem is the primary artifact; the channel post is a normative side effect.

### check_work_status

Polls current WorkItem status.

**Parameters:** `caller_ref` (the callerRef returned from `request_human_work`).

**Flow:**
1. `WorkItemCreator.findByCallerRef(callerRef)`
2. Return `WorkStatusResponse(workItemId, status, assigneeId, outcome, resolution)` or "not found"

### wait_for_work

Blocks until the WorkItem reaches a terminal state or times out.

**Parameters:** `caller_ref`, `timeout_seconds` (default 300).

**Flow:**
1. Check current status — if already terminal, return immediately
2. Register a `CompletableFuture<WorkItemRef>` in `PendingWorkCompletionRegistry`
3. Block with timeout
4. Return status + outcome, or `timedOut = true`

### PendingWorkCompletionRegistry

`@ApplicationScoped` bean. `ConcurrentHashMap<String, CompletableFuture<WorkItemStatusEvent>>` keyed by callerRef. The outbound lifecycle adapter completes futures on terminal events. Same pattern as Qhorus's `PendingReply` for `wait_for_reply`.

## Outbound Lifecycle Adapter

`QhorusWorkItemLifecycleAdapter` — `@ApplicationScoped`, implements `WorkItemObserver`.

### Behaviour

On every `WorkItemStatusEvent`:

1. Check `QhorusCallerRef.isQhorus(event.callerRef())` — if not Qhorus-originated, return
2. Notify `PendingWorkCompletionRegistry` (for `wait_for_work` futures) — runs on ALL status changes, not just terminal
3. If status is not terminal, return
4. Parse `QhorusCallerRef` from callerRef
5. Map terminal status to speech act
6. Post to originating channel via `MessageService.dispatch()`

### Speech Act Mapping (terminal only)

| WorkItem terminal status | Qhorus speech act | Meaning |
|---|---|---|
| COMPLETED | DONE | Obligation fulfilled |
| REJECTED | FAILURE | Tried and could not complete |
| CANCELLED | DECLINE | Reasoned refusal |
| EXPIRED | DECLINE | Deadline passed without resolution |

### Message Content

JSON payload with `workItemId`, `outcome`, `resolution`, `assigneeId`. The requesting agent gets everything it needs to act on the result.

### Sender Identity

Posts as `"workitems"` — a system identity. The human's identity is in the message content, not the sender field.

### Tenant Context

`WorkItemObserver.onStatusChange()` runs synchronously in the emitter's transaction. Tenant context is already active — no `TenantContextRunner` needed (unlike `@ObservesAsync` handlers per protocol PP-20260609-fb6563).

### Error Handling

`MessageService.dispatch()` failure: log at WARN. The WorkItem lifecycle is already committed. The channel post is best-effort notification.

## What We Don't Touch

- `WorkCloudEventAdapter` — continues serving non-Qhorus external systems via CloudEvents. Both adapters coexist; they observe via different mechanisms (CDI async vs WorkItemObserver SPI).
- `WorkItemLifecycleEmitter` — unchanged. Already dispatches to `WorkItemObserver` implementations.
- `QhorusMcpTools` — not modified. `WorkQhorusMcpTools` is a separate bean discovered by the same MCP server.
- No Flyway migrations. No new entities. No changes to the WorkItem model.

## Testing

`@QuarkusTest` with both casehub-work and casehub-qhorus runtimes. In-memory stores (`casehub-work-persistence-memory`, `casehub-qhorus-persistence-memory`). H2 datasource.

### Test Cases

1. **request_human_work round-trip** — call tool, assert WorkItem created with correct callerRef, assert QUERY posted to channel with matching correlationId
2. **check_work_status** — create via tool, check PENDING; complete, check COMPLETED with outcome
3. **wait_for_work happy path** — request, complete after delay in separate thread, assert wait returns terminal status
4. **wait_for_work timeout** — request, wait with 1s timeout, assert timedOut
5. **wait_for_work already terminal** — create and complete, wait returns immediately
6. **Outbound terminal posting** — Qhorus-originated WorkItem completed → DONE posted to originating channel
7. **Non-Qhorus WorkItem ignored** — non-Qhorus callerRef → no channel messages
8. **Speech act mapping** — REJECTED→FAILURE, CANCELLED→DECLINE, EXPIRED→DECLINE
9. **Idempotency** — two `request_human_work` calls → two distinct WorkItems (unique correlationIds)
10. **CallerRef parsing** — unit tests for parse/encode/isQhorus with valid, malformed, edge-case inputs
