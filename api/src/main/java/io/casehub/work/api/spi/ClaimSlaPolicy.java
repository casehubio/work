package io.casehub.work.api.spi;

import io.casehub.platform.api.routing.NamedStrategy;
import io.casehub.work.api.ClaimSlaContext;

import java.time.Instant;

/**
 * SPI for computing the pool-phase deadline when a WorkItem returns to the
 * unclaimed pool after a claim expires, an unclaim, or a transfer.
 *
 * <p>Implementations are CDI beans annotated {@code @ApplicationScoped} that return
 * a unique {@link #id()} string. The active policy is selected by configuration:
 * {@code casehub.work.sla.claim-policy=<id>} (default: {@code "continuation"}).
 *
 * <p>Built-in implementations (in quarkus-work-core):
 * <ul>
 * <li>{@code ContinuationPolicy} (id: "continuation") — remaining pool time carries forward (default)
 * <li>{@code FreshClockPolicy} (id: "fresh-clock") — full pool SLA resets on every return to pool
 * <li>{@code SingleBudgetPolicy} (id: "single-budget") — hard deadline from submission, never moves
 * <li>{@code PhaseClockPolicy} (id: "phase-clock") — each claimant gets full time; hard total cap above
 * </ul>
 */
public interface ClaimSlaPolicy extends NamedStrategy {

    /**
     * Compute the pool-phase deadline for a WorkItem that has just returned to
     * the unclaimed pool.
     *
     * @param context transition context; never null
     * @return the absolute deadline instant for the next unclaimed phase; never null.
     *         Return {@code Instant.MAX} to indicate no deadline (no escalation will fire).
     */
    Instant computePoolDeadline(ClaimSlaContext context);
}
