# io.casehub.work.api.spi.ClaimSlaPolicy

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for computing the pool-phase deadline when a WorkItem returns to the
unclaimed pool after a claim expires, an unclaim, or a transfer.

<p>Implementations are CDI beans annotated `@ApplicationScoped` that return
a unique `.id()` string. The active policy is selected by configuration:
`casehub.work.sla.claim-policy=<id>` (default: `"continuation"`).

<p>Built-in implementations (in quarkus-work-core):
<ul>
<li>`ContinuationPolicy` (id: "continuation") — remaining pool time carries forward (default)
<li>`FreshClockPolicy` (id: "fresh-clock") — full pool SLA resets on every return to pool
<li>`SingleBudgetPolicy` (id: "single-budget") — hard deadline from submission, never moves
<li>`PhaseClockPolicy` (id: "phase-clock") — each claimant gets full time; hard total cap above
</ul>

## Methods

### `public abstract java.time.Instant computePoolDeadline(io.casehub.work.api.ClaimSlaContext context)`

Compute the pool-phase deadline for a WorkItem that has just returned to
the unclaimed pool.

#### Parameters

- `context` (`io.casehub.work.api.ClaimSlaContext`) — transition context; never null

#### Returns

the absolute deadline instant for the next unclaimed phase; never null.
        Return `Instant.MAX` to indicate no deadline (no escalation will fire).
