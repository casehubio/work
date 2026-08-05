# io.casehub.work.api.spi.SlaBreachPolicy

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI: decides what happens when a WorkItem breaches its SLA deadline.

<p>Implementations are CDI beans annotated `@ApplicationScoped` that return
a unique `.id()` string. The active policy is selected by configuration:
`casehub.work.sla.breach-policy=<id>` (default: `"no-op"`).

<p>The casehub-work runtime calls `.onBreach` when a WorkItem's
`expiresAt` or `claimDeadline` passes, then executes the
returned `BreachDecision`. The policy is pure — it makes a decision
and returns; all state mutations and CDI events are handled by the runtime.

<p>Stateless two-tier escalation pattern — check candidateGroups to detect tier:
<pre>
public BreachDecision onBreach(SlaBreachContext ctx) {
    String escalationGroup = prefs(ctx).get(ESCALATION_GROUP);
    if (ctx.task().candidateGroups().contains(escalationGroup)) {
        return new Fail(prefs(ctx).get(BREACH_TERMINAL_REASON));
    }
    Duration deadline = Duration.ofHours(prefs(ctx).get(ESCALATION_HOURS));
    return EscalateTo.to(escalationGroup).withDeadline(deadline);
}
</pre>
The policy is called again when the escalated WorkItem expires — no serialization
of decision trees required.

<p>Default implementation: `NoOpSlaBreachPolicy` (id: "no-op") returns
`Fail("no-sla-breach-policy-configured")`.

## Methods

### `public abstract io.casehub.work.api.BreachDecision onBreach(io.casehub.work.api.SlaBreachContext context)`

Decide what to do when `context.task()` has breached its SLA deadline.

#### Parameters

- `context` (`io.casehub.work.api.SlaBreachContext`) — breach context including task identity, scope, and resolved preferences

#### Returns

the decision to execute; never null
