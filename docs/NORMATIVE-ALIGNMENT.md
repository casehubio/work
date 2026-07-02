# Normative Alignment — WorkItem Lifecycle to Qhorus Speech Acts

**Issue:** casehubio/work#159
**Date:** 2026-07-02

This document maps casehub-work lifecycle concepts to the Qhorus normative
layer. It is the authoritative reference for developers working in casehub-work
who need to understand how WorkItem state transitions relate to speech acts and
deontic commitment states.

For the full theoretical framework, see:
- `qhorus/docs/normative-layer.md` — four-layer normative model
- `qhorus/docs/work-and-workitems.md` — principled boundary between machine
  and human agent layers

For lifecycle mechanics and industry spec alignment (WS-HumanTask 1.1,
OpenHumanTask, BPMN 2.0, CMMN 1.1), see the lifecycle alignment spec (#240).

---

## The Two-Layer Model

casehub-work is not filling gaps in the Qhorus normative model. It is the
**human-agent layer** — a formal extension of Qhorus for obligations held
discontinuously. Machines execute or stop. Humans pause, resume, sub-delegate,
and return after interruption. The differences between WorkItem and Qhorus
are the deliberate boundary between two coherent layers:

```
Qhorus core          machine-agent normative layer (complete for machines)
casehub-work         human-agent normative extension (complete for humans)
```

A **WorkItem** is not the obligation itself. It is the mechanism by which an
obligation is fulfilled when the agent is human. The obligation lives in the
Qhorus CommitmentStore; the WorkItem tracks the human-scale lifecycle of
discharging it.

---

## WorkItemStatus to CommitmentState

Every WorkItemStatus value mapped to its Qhorus CommitmentState equivalent.

| WorkItemStatus | CommitmentState | Terminal? | Normative meaning |
|---|---|---|---|
| `PENDING` | OPEN | no / no | Obligation commissioned; no obligor yet. COMMAND issued, awaiting claim. |
| `ASSIGNED` | OPEN (obligor named) | no / no | Obligor selected — between OPEN and ACKNOWLEDGED. Assignee present but no STATUS sent. |
| `IN_PROGRESS` | ACKNOWLEDGED | no / no | Obligor actively working; deadline window extended. STATUS speech act equivalent. |
| `COMPLETED` | FULFILLED | yes / yes | DONE — obligation discharged successfully. Direct alignment. |
| `REJECTED` | FAILED or DECLINED | yes / yes | Actor's deliberate refusal or inability to complete. See **Ambiguity note** below. |
| `FAULTED` | FAILED | yes / yes | System/infrastructure failure — no actor judgment. Always FAILED, never DECLINED. |
| `DELEGATED` | *(no equivalent)* | **no / yes** | Pre-acceptance hold — forwarded to named actor, awaiting accept/decline. **Semantic conflict** — see §6. |
| `SUSPENDED` | *(no equivalent)* | no / n/a | Human-layer extension — obligation held while paused. See §5a. |
| `CANCELLED` | DECLINED | yes / yes | DECLINE — deliberate withdrawal of the obligation. |
| `EXPIRED` | EXPIRED | yes / yes | Deadline passed with no resolution. Direct alignment. |
| `ESCALATED` | *(no equivalent)* | yes / n/a | All SLA breach policy branches exhausted — operator intervention required. See **ESCALATED note** below. |
| `OBSOLETE` | *(no equivalent)* | yes / n/a | Context superseded — work became irrelevant. See §5c. |

**Ambiguity note — REJECTED:** WorkItemStatus.REJECTED covers two normatively
distinct outcomes: "tried and could not complete" (CommitmentState.FAILED) and
"deliberate refusal" (CommitmentState.DECLINED). The semantic distinction is
carried by the `resolution` field, not the status value. For inbound translation
from external systems, `NormativeResolution` cleanly separates these:
FAILURE → REJECTED, DECLINE → CANCELLED. See §7.

**ESCALATED note:** ESCALATED has no clean CommitmentState equivalent. It is
terminal but is not FULFILLED, DECLINED, or FAILED in the Qhorus sense. It is
an infrastructure-terminal state meaning "all automated resolution paths
exhausted." Closest interpretation: a special case of FAILED where the failure
is the system's inability to route, not an actor's inability to complete.

---

## WorkEventType to MessageType

Every WorkEventType value classified as Normative (maps to a speech act),
Operational (internal machinery, no agent-to-agent communicative significance),
Human-layer extension, or Infrastructure (system-generated, no agent origin).

| WorkEventType | MessageType | Category | Normative meaning |
|---|---|---|---|
| `CREATED` | COMMAND | Normative | Directive issued — obligation created |
| `ASSIGNED` | *(operational)* | Operational | Internal routing — obligor selected |
| `STARTED` | STATUS | Normative | "I am actively working on this" — extends deadline window |
| `COMPLETED` | DONE | Normative | Fulfillment declaration — obligation discharged |
| `REJECTED` | FAILURE or DECLINE | Normative | Work cannot or will not be completed |
| `FAULTED` | FAILURE | Normative | System failure — involuntary inability to proceed |
| `DELEGATED` | HANDOFF | Normative | Obligation forwarded to named actor (with sub-delegation semantics — see §5b) |
| `DELEGATION_ACCEPTED` | STATUS | Normative | Delegatee signals acceptance — functionally an ACKNOWLEDGED |
| `DELEGATION_DECLINED` | DECLINE | Normative | Delegatee refuses the forwarded obligation |
| `RELEASED` | *(operational)* | Operational | Inverse of claim — obligor relinquishes without formal resolution; obligation returns to OPEN |
| `SUSPENDED` | STATUS | Human-layer | Pause report — "obligation still held, work temporarily halted" |
| `RESUMED` | STATUS | Human-layer | Resumption report — "active work resumes" |
| `CANCELLED` | DECLINE | Normative | Deliberate withdrawal of the obligation |
| `OBSOLETE` | *(no equivalent)* | Human-layer | Context-driven termination — no agent made a normative act; the environment changed |
| `EXPIRED` | *(infrastructure)* | Infrastructure | Deadline passed — generated by infrastructure, not by an agent's communicative act |
| `CLAIM_EXPIRED` | *(infrastructure)* | Infrastructure | Claim deadline variant of EXPIRED |
| `SPAWNED` | COMMAND (batch) | Normative | New obligations commissioned from this work unit |
| `ESCALATED` | *(infrastructure)* | Infrastructure | All breach policy branches exhausted — system-generated terminal |
| `DEADLINE_EXTENDED` | STATUS | Normative | Actor extended the deadline — signals continued intent |
| `SLA_REASSIGNED` | HANDOFF | Normative | WorkItem re-routed to new candidate groups by policy — obligation transferred to new pool |
| `SLA_EXTENDED` | STATUS | Normative | Policy extended the deadline — system-initiated STATUS |
| `SIGNAL_RECEIVED` | EVENT | Normative | External signal routed to this work unit — telemetry |
| `MANUALLY_ESCALATED` | HANDOFF | Normative | Actor explicitly escalated to a different candidate group — deliberate transfer |
| `PROGRESS_UPDATE` | STATUS | Normative | Actor reports intermediate progress (percentComplete, statusNote) |
| `LABEL_ADDED` | *(operational)* | Operational | Label management — no normative significance |
| `LABEL_REMOVED` | *(operational)* | Operational | Label management — no normative significance |

**Category summary:**
- **Normative** (16): maps cleanly to a Qhorus speech act
- **Operational** (4): internal WorkItem machinery — ASSIGNED, RELEASED, LABEL_ADDED, LABEL_REMOVED
- **Human-layer** (3): normative acts that exist only in the human-agent model — SUSPENDED, RESUMED, OBSOLETE
- **Infrastructure** (3): system-generated with no agent origin — EXPIRED, CLAIM_EXPIRED, ESCALATED

---

## Human-Layer Extensions

Three WorkItem concepts have no direct equivalent in the machine-agent layer.
These are correct omissions at the machine level that become necessary additions
at the human level.

### 5a. SUSPENDED — discontinuous holding

When a human suspends a WorkItem they are neither failing nor delegating. They
are holding the obligation while pausing active work. Machine agents do not
suspend — they execute or they stop. If a machine cannot proceed, it emits
DECLINE (with reason) and the coordinator re-issues when the condition is met.

SUSPENDED has no CommitmentState equivalent and correctly so. It sits alongside
terminal states as a valid human-layer resolution of IN_PROGRESS but is
reversible — resume restores the prior status.

### 5b. Sub-delegation vs transfer (DELEGATED vs HANDOFF)

In Qhorus, HANDOFF transfers the obligation and releases the original obligor.
The machine agent that hands off is done.

WorkItem DELEGATED retains the original assignee as `owner`. They remain
accountable even after transfer. The delegation chain is recorded and ownership
persists through it. This distinction — **transfer** (HANDOFF: releases) vs
**sub-delegation** (DELEGATED: retains) — is a real concept in deontic logic.
The person who delegated a payment decision remains in the accountability chain
even after the senior adjuster took it over.

Both are valid normative acts. The machine layer needs only transfer. The human
layer needs both.

### 5c. OBSOLETE — context-driven termination

OBSOLETE means the case context changed, making this work irrelevant. It was
never completed but never needed to be. No agent made a normative act — the
environment changed.

Not DECLINE (nobody refused). Not FAILURE (nobody tried and failed). Not
CANCELLED (nobody deliberately stopped it). The obligation was rendered moot
by external circumstances. Example: an IRB review WorkItem becomes OBSOLETE
when the clinical trial is withdrawn.

Unique to the human-task layer — workflow steps don't become obsolete, they
are cancelled.

---

## Semantic Conflict: DELEGATED

DELEGATED has incompatible meanings across three systems. This is the single
most dangerous cross-system confusion point:

| System | Enum | DELEGATED means | Terminal? |
|---|---|---|---|
| casehub-work | `WorkItemStatus.DELEGATED` | Pre-acceptance hold — forwarded to named actor, awaiting accept/decline | **No** |
| casehub-qhorus | `CommitmentState.DELEGATED` | Obligation transferred to new debtor via HANDOFF — original discharged | **Yes** |
| casehub-engine | `PlanItemStatus.DELEGATED` | Control passed to external actor (HumanTask, SubCase, Extension) — engine waiting | **No** |

No code change fixes this — the semantics are correct within each system.
Javadoc on all three enums carries cross-system warnings. See `docs/LIFECYCLE.md`
(casehub-parent) for the authoritative cross-system semantics.

**Integration warning:** when building the `casehub-work-qhorus` bridge, a
`WorkItemStatus.DELEGATED` event must NOT produce a `CommitmentState.DELEGATED`
transition. WorkItem DELEGATED is a pre-acceptance hold (non-terminal);
Commitment DELEGATED means the obligation is discharged (terminal).

---

## NormativeResolution — the Inbound Vocabulary Bridge

`NormativeResolution` in `casehub-work-api` translates external system
close vocabulary into stable WorkItem terminal transitions:

| NormativeResolution | WorkItemStatus | CommitmentState | Meaning |
|---|---|---|---|
| `DONE` | COMPLETED | FULFILLED | Obligation discharged |
| `DECLINE` | CANCELLED | DECLINED | Deliberate refusal |
| `FAILURE` | REJECTED | FAILED | Attempted but could not complete |

Used by `WebhookEventHandler` (issue-tracker module) to map tracker-specific
close reasons (GitHub `state_reason`, Jira resolution fields) without leaking
tracker terms past the provider boundary.

See: `api/src/main/java/io/casehub/work/api/NormativeResolution.java`

---

## Forward Look: casehub-work-qhorus

When the bridge module is built, WorkItem lifecycle events will translate to
Qhorus channel messages using the WorkEventType → MessageType table (§4)
as the mapping specification.

**Translate to channel messages:**
- All **Normative** events — 16 events that map to speech acts
- **Human-layer** SUSPENDED and RESUMED — as STATUS messages on the oversight channel

**Do NOT translate:**
- **Operational** events (ASSIGNED, RELEASED, LABEL_ADDED, LABEL_REMOVED) — no normative significance
- **Infrastructure** events (EXPIRED, CLAIM_EXPIRED, ESCALATED) — may generate synthetic FAILURE messages or be captured as EVENT telemetry, depending on the oversight channel's subscription policy

**The DELEGATED trap:** WorkItem DELEGATED fires as a lifecycle event and
maps to HANDOFF in the event table. But the bridge must NOT produce a
`CommitmentState.DELEGATED` transition (terminal). Instead, the bridge should:
1. Create a child Commitment (OPEN) for the delegatee
2. Keep the parent Commitment ACKNOWLEDGED (not DELEGATED)
3. On DELEGATION_ACCEPTED → child Commitment transitions to ACKNOWLEDGED
4. On DELEGATION_DECLINED → child Commitment closes; parent resumes

This preserves the sub-delegation accountability chain in the human layer
while keeping the Commitment model's terminal-DELEGATED semantics correct.

---

## References

| Document | What it covers |
|---|---|
| `qhorus/docs/normative-layer.md` | Four-layer normative model; speech act taxonomy; insurance claim and database corruption scenarios |
| `qhorus/docs/work-and-workitems.md` | Principled boundary between machine and human agent layers; cross-channel correlation |
| `docs/LAYERING.md` | Work/WorkItem/Task taxonomy; CaseHub vs casehub-work boundary |
| `docs/specs/2026-06-18-lifecycle-alignment-design.md` | Lifecycle alignment (#240); industry spec mapping; cross-repo deliverables |
| `api/.../WorkItemStatus.java` | 12 lifecycle statuses with `isTerminal()` and `isActive()` |
| `api/.../WorkEventType.java` | 26 lifecycle event types |
| `api/.../NormativeResolution.java` | Inbound vocabulary bridge (DONE/DECLINE/FAILURE) |
| `qhorus/api/.../MessageType.java` | 9 speech acts with helper methods |
| `qhorus/api/.../CommitmentState.java` | 7 commitment states with `isTerminal()` and `isActive()` |
