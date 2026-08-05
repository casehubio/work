# io.casehub.work.api.BreachDecision

**Package:** `io.casehub.work.api`

**Kind:** `interface`

Decision returned by `SlaBreachPolicy.onBreach` describing what the
casehub-work runtime should do when a WorkItem breaches its SLA deadline.

<p>Use `.thenOnBreach` to chain a fallback for when the primary decision
cannot be executed (e.g. `EscalateTo` with an empty group set):
<pre>
EscalateTo.to("senior-reviewers")
    .withDeadline(Duration.ofHours(4))
    .thenOnBreach(new Fail("no-escalation-target-configured"))
</pre>

## Methods

### `public default io.casehub.work.api.BreachDecision thenOnBreach(io.casehub.work.api.BreachDecision fallback)`

Chains a fallback decision: if this decision cannot be executed,
the runtime will execute `fallback` instead.

#### Parameters

- `fallback` (`io.casehub.work.api.BreachDecision`)
