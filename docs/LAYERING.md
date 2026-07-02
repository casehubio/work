# Layering Architecture
**Date:** 2026-04-28  
**Scope:** casehub-work, CaseHub, Quarkus-Flow ecosystem

---

## Work, WorkItem, and Task — the taxonomy

**Work** is the generic concept: anything that needs doing. The `casehub-work-api` and `casehub-work-core` modules define Work at this generic level — SPIs, relation types, event types, selection strategies — without assuming what kind of Work is involved.

**WorkItem** is a specific kind of Work: work that sits in a **human inbox**. A WorkItem is claimed by a person, worked on by a person, and completed by a person with a resolution. It has SLA, delegation, escalation, audit trail, and assignment routing. WorkItems live in the `runtime/` module of casehub-work.

**Task** is another specific kind of Work: an **automated workflow step** executed by a machine. Task is owned by Quarkus-Flow and maps directly to the CNCF Serverless Workflow SDK `Task` type. The name cannot change — it is fixed by the specification.

```
Work  (generic concept — casehub-work-api / casehub-work-core)
 ├── WorkItem   (human inbox — casehub-work runtime/)
 └── Task       (automated step — Quarkus-Flow)
```

A WorkItem is a type of Work. A Task is a type of Work. They are peers, not hierarchically related to each other. CaseHub coordinates Work — which may include WorkItems, Tasks, or future Work types — without needing to know which kind it is dealing with at the coordination level.

---

## The layers

```
┌─────────────────────────────────────────────────────────┐
│                        CaseHub                          │
│   case orchestration — blackboard + CMMN                │
│   owns: when, which, what-it-means                      │
└────────────────────────┬────────────────────────────────┘
                         │ calls / observes
┌────────────────────────┴────────────────────────────────┐
│                     casehub-work                        │
│   human task primitives — inbox + lifecycle             │
│   owns: how WorkItems are created, managed, related     │
└────────────────────────┬────────────────────────────────┘
                         │ parallel peer
┌────────────────────────┴────────────────────────────────┐
│                    Quarkus-Flow                         │
│   workflow execution — CNCF Serverless Workflow         │
│   owns: automated Task execution                        │
└─────────────────────────────────────────────────────────┘
```

casehub-work and Quarkus-Flow are peers at the Work execution layer. CaseHub sits above both and coordinates between them. Neither casehub-work nor Quarkus-Flow knows about the other directly — CaseHub is the only thing that sees both.

---

## What casehub-work owns

casehub-work is the **human task primitive layer**. It provides the mechanics of managing WorkItems. It makes no decisions about what events mean or what should happen next.

**WorkItem lifecycle:**
- Create, assign, claim, start, complete, cancel, expire, escalate, delegate
- SLA enforcement — expiry deadlines, claim deadlines
- Status transitions and the guards on them

**Assignment and routing:**
- Candidate users and groups
- Selection strategies (`LeastLoadedStrategy`, `SemanticWorkerSelectionStrategy`, etc.)
- Skill profiles and embedding-based matching
- Workload awareness

**Relationships:**
- `PART_OF` and other Work relation types — the graph of how Work is related
- Parent/child linking via `WorkItemRelation`
- Cycle detection on PART_OF graphs

**Spawn mechanics:**
- Creating child WorkItems from templates on explicit request
- Wiring children to a parent via `PART_OF`
- Carrying `callerRef` opaquely on spawned children — stored on the WorkItem and echoed in lifecycle event context, never interpreted
- Firing per-child lifecycle events

**Events:**
- `WorkItemLifecycleEvent` per state transition — fired, not acted on
- casehub-work fires events; it does not react to them to drive other WorkItems

**Templates:**
- Blueprints for WorkItem creation — category, priority, groups, SLA, labels
- Instantiation from template in a single API call

**Queues and filters:**
- Inbox organisation — filter chains, queue views, label-based routing
- `FilterRegistryEngine` — applies rules to WorkItems as they transition

**Audit and ledger:**
- Append-only audit trail per WorkItem
- Ledger entries with causal chain (`causedByEntryId`) for provenance

**Group policies:**
- M-of-N completion — a spawn group configured with `instanceCount=N, requiredCount=M` tracks child completions and completes the parent when M children reach COMPLETED, or rejects it when the group can no longer reach M. Declared at creation time, evaluated purely from member states — no external context, no ordering.
- Cascade cancellation — `SpawnPort.cancelGroup(cascadeChildren=true)` cancels pending children within a group. Same rationale: static policy, no external context, no ordering imposed.

Group policies are distinct from orchestration because they operate on **homogeneous, unordered members** with a **statically declared policy** that can be evaluated in isolation. Orchestration operates on heterogeneous, potentially ordered work whose completion semantics depend on external case context.

---

## What casehub-work does NOT own

casehub-work **does not orchestrate**. It does not impose ordering between distinct pieces of work or make decisions that require external case state. Specifically:

- **When to spawn WorkItems** — that is the caller's decision (CaseHub, application)
- **What completing a WorkItem means for a case** — casehub-work fires the event; CaseHub decides
- **Activation conditions** — whether a child should activate based on parent state; that is CaseHub's blackboard
- **Milestones** — case-level checkpoints; CaseHub's CMMN model
- **Case lifecycle** — fault handling, case cancellation semantics; CaseHub
- **What a rejected or expired WorkItem means for a process** — CaseHub decides
- **Heterogeneous plan-item completion** — whether named plan items A, B, and C have all completed to advance a Stage; that is CaseHub's `Stage.requiredItemIds` (see reconciliation note below)

The rule is: **casehub-work provides primitives and group policies. Every decision that requires ordering or external context lives above it.**

---

## What CaseHub owns

CaseHub is the **case orchestration layer**. It coordinates Work — which may be WorkItems, Tasks, or future Work types — according to a `CasePlanModel`.

- **When to create WorkItems** — CaseHub calls `POST /workitems/{id}/spawn` or creates individual WorkItems when bindings fire
- **Which children to spawn** — CaseHub's `CasePlanModel` defines the structure; casehub-work executes it
- **What completing a WorkItem means for a case** — CaseHub's `PlanItemCompletionHandler` marks the corresponding `PlanItem` COMPLETED and evaluates stage autocomplete
- **Heterogeneous stage completion** — `Stage.requiredItemIds` tracks which specific named plan items (WorkItem A AND Task B AND milestone C) must complete before a Stage advances. This is the right tool for named, heterogeneous plan-level completion — not for homogeneous parallel instances (see reconciliation note below).
- **Case-level fault handling** — REJECTED child → CaseHub's goal evaluation decides whether to fault the case
- **Cancellation semantics** — when a stage terminates, CaseHub instructs casehub-work to cancel children; casehub-work executes, does not decide
- **Activation conditions** — CaseHub's blackboard evaluates whether a planned child should activate now
- **Orchestration vs choreography** — CaseHub chooses the execution model; casehub-work supports both via `callerRef` on the WorkItem entity (accessible from lifecycle events via `event.source()`)

### Stage.requiredItemIds — reconciliation note

`Stage.requiredItemIds` is the right tool when a Stage requires specific, named, heterogeneous plan items to complete — WorkItem A AND Task B AND milestone C. Each item is distinct, its identity matters, and the completion condition is structural.

It is **not** the right tool for homogeneous parallel instances (five reviewers of the same template, any three must approve). For that case, CaseHub should spawn a multi-instance WorkItem — casehub-work tracks the M-of-N policy internally and fires a single COMPLETED event on the parent when satisfied. CaseHub observes the parent's COMPLETED event; it does not track the individual instances. Using `Stage.requiredItemIds` for multi-instance tracking would duplicate what casehub-work already handles at the group primitive level and couple CaseHub to implementation details of a group it should treat as atomic.

---

## The boundary rule

> **casehub-work provides primitives and group policies.**  
> **CaseHub orchestrates — it imposes ordering and applies domain context.**  
> **Nothing crosses this line in either direction.**

The test is **not** "does this touch another WorkItem." Cascade cancellation touches other WorkItems and is correct in casehub-work. The real test is two questions:

1. **Does it impose ordering between distinct pieces of work?** If code says "do B after A completes," that is ordering — it requires a plan model and belongs in CaseHub.
2. **Does it require external context or domain knowledge to evaluate?** If the decision depends on case state, blackboard data, risk scores, or business goals, it belongs in CaseHub. If it can be evaluated purely from the group's own member states, it is a primitive.

A policy that satisfies neither test — no ordering imposed, no external context required — is a casehub-work primitive or group policy.

Examples:
- "When a WorkItem expires, mark it EXPIRED" — **lifecycle management, casehub-work** ✓
- "When a WorkItem expires, escalate it to a supervisor group" — **lifecycle management, casehub-work** ✓ (SlaBreachPolicy)
- "When M-of-N child instances complete (static M, static N, same template), complete the parent" — **group policy, casehub-work** ✓ (no ordering, no external context)
- "When the group can no longer reach M completions due to rejections, reject the parent" — **group policy, casehub-work** ✓ (same rationale)
- "When a spawn group is cancelled, cancel pending children" — **group policy, casehub-work** ✓ (cascade cancel)
- "When WorkItem A (risk assessment) completes, spawn WorkItem B (review)" — **orchestration, CaseHub** ✗ (ordering + specific named items)
- "When a child is rejected, decide whether to fault the case based on risk score" — **orchestration, CaseHub** ✗ (external context)
- "When all required named plan items in a Stage complete, advance the Stage" — **orchestration, CaseHub** ✗ (heterogeneous named items, plan-level semantics)

---

## How they interact

**CaseHub → casehub-work (commands):**
```
POST /workitems                    create a WorkItem
POST /workitems/{id}/spawn         spawn children, link via PART_OF
PUT   /workitems/{id}/claim         claim for a user (?claimant=<user>)
DELETE /workitems/{id}             cancel
GET   /workitems/{id}/children     query PART_OF children
```

**casehub-work → CaseHub (events):**
```
WorkItemLifecycleEvent(CREATED,   workItemId, status, actor, ...)
WorkItemLifecycleEvent(ASSIGNED,  workItemId, status, actor, ...)
WorkItemLifecycleEvent(COMPLETED, workItemId, status, actor, outcome, ...)
WorkItemLifecycleEvent(REJECTED,  workItemId, status, actor, ...)
WorkItemLifecycleEvent(EXPIRED,   workItemId, status, actor, ...)
WorkItemLifecycleEvent(SPAWNED,   workItemId, status, actor, ...)
  └── event.source().callerRef  — the routing key back to the caller
```

`callerRef` is the routing key: CaseHub sets it at creation time (e.g. `caseId:planItemId`), casehub-work stores it opaquely on the WorkItem entity, and lifecycle events carry the WorkItem via `event.source()`. CaseHub's adapter calls `event.source().callerRef` to route completion back to the right `CasePlanModel` and `PlanItem` without a query.

**The `casehub-work-casehub` integration module** (future) is the translation layer: it observes `WorkItemLifecycleEvent`, extracts `callerRef` from the embedded WorkItem via `event.source()`, and signals `CONTEXT_CHANGED` on the right `CaseInstance`. Neither casehub-work nor CaseHub knows about the other — the adapter bridges them.

---

## Why this matters

**No competing functionality.** Group policies in casehub-work and Stage orchestration in CaseHub cover distinct concerns — homogeneous group primitives vs. heterogeneous plan-level coordination. When each system owns what it is best suited for, they compose cleanly rather than duplicate. Two systems making the same decision with potentially different answers is worse than one system making it correctly.

**Clean buildability.** CaseHub can build on casehub-work without working around it. casehub-work does not have opinions that CaseHub needs to suppress with config flags.

**Independent evolution.** casehub-work can improve assignment routing, skill matching, SLA mechanics without CaseHub needing to change. CaseHub can improve case orchestration, blackboard evaluation, planning strategies without casehub-work needing to change. The events and REST API are the stable contract between them.

**Reusability.** Any application — with or without CaseHub — can use casehub-work for human task management. The primitives are useful standalone. CaseHub adds orchestration on top; it does not replace the primitives.

**Intuitive taxonomy.** A developer reading either codebase knows immediately what it owns. casehub-work answers: "how is this WorkItem created, assigned, tracked, and completed?" CaseHub answers: "what work needs to happen in this case, and in what order?"

---

## Normative alignment

This document covers the CaseHub-vs-casehub-work boundary — what each system
owns and how they interact. A separate axis of alignment exists between
casehub-work and the Qhorus normative layer (speech acts, commitment states,
deontic logic).

casehub-work is the human-agent extension of the Qhorus normative model.
WorkItem lifecycle transitions map to speech acts (COMMAND, DONE, FAILURE,
DECLINE, HANDOFF, STATUS). The mapping is direct for most transitions but
diverges where human-agent concepts have no machine-agent equivalent
(SUSPENDED, sub-delegation, OBSOLETE).

See [`docs/NORMATIVE-ALIGNMENT.md`](NORMATIVE-ALIGNMENT.md) for the complete
mapping tables and semantic conflict documentation.
