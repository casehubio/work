# io.casehub.work.api.spi.WorkerSelectionStrategy

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

Pluggable worker selection SPI.

<p>Implementations are CDI beans annotated `@ApplicationScoped` that return
a unique `.id()` string. The active strategy is selected by configuration:
`casehub.work.routing.strategy=<id>` (default: `"least-loaded"`).

<p>Built-in implementations (in the core module):
<ul>
<li>`LeastLoadedStrategy` (id: "least-loaded") — pre-assigns to fewest-active-items candidate (default)
<li>`ClaimFirstStrategy` (id: "claim-first") — no pre-assignment; whoever claims first wins
<li>`RoundRobinStrategy` (id: "round-robin") — sequential rotation across all candidates
</ul>

<p>CaseHub alignment: corresponds to `WorkerSelectionStrategy` in casehub-engine.

## Methods

### `public abstract io.casehub.work.api.AssignmentDecision select(io.casehub.work.api.SelectionContext context, java.util.List<io.casehub.work.api.WorkerCandidate> candidates)`

Select an assignment outcome from the resolved candidate list.

#### Parameters

- `context` (`io.casehub.work.api.SelectionContext`) — minimal WorkItem context (types, priority, capabilities, pools)
- `candidates` (`java.util.List<io.casehub.work.api.WorkerCandidate>`) — resolved candidates with pre-populated `activeWorkItemCount`

#### Returns

assignment decision; never null — use `AssignmentDecision.noChange()`
        to leave the WorkItem unassigned

### `public default java.util.Set<io.casehub.work.api.AssignmentTrigger> triggers()`

The lifecycle events that trigger this strategy.
Override to restrict to a subset. Default: all three events.
