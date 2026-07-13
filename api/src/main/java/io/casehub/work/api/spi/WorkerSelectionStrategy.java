package io.casehub.work.api.spi;

import io.casehub.platform.api.routing.NamedStrategy;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;

import java.util.List;
import java.util.Set;

/**
 * Pluggable worker selection SPI.
 *
 * <p>Implementations are CDI beans annotated {@code @ApplicationScoped} that return
 * a unique {@link #id()} string. The active strategy is selected by configuration:
 * {@code casehub.work.routing.strategy=<id>} (default: {@code "least-loaded"}).
 *
 * <p>Built-in implementations (in the core module):
 * <ul>
 * <li>{@code LeastLoadedStrategy} (id: "least-loaded") — pre-assigns to fewest-active-items candidate (default)
 * <li>{@code ClaimFirstStrategy} (id: "claim-first") — no pre-assignment; whoever claims first wins
 * <li>{@code RoundRobinStrategy} (id: "round-robin") — sequential rotation across all candidates
 * </ul>
 *
 * <p>CaseHub alignment: corresponds to {@code WorkerSelectionStrategy} in casehub-engine.
 */
public interface WorkerSelectionStrategy extends NamedStrategy {

    /**
     * Select an assignment outcome from the resolved candidate list.
     *
     * @param context minimal WorkItem context (types, priority, capabilities, pools)
     * @param candidates resolved candidates with pre-populated {@code activeWorkItemCount}
     * @return assignment decision; never null — use {@link AssignmentDecision#noChange()}
     *         to leave the WorkItem unassigned
     */
    AssignmentDecision select(SelectionContext context, List<WorkerCandidate> candidates);

    /**
     * The lifecycle events that trigger this strategy.
     * Override to restrict to a subset. Default: all three events.
     */
    default Set<AssignmentTrigger> triggers() {
        return Set.of(AssignmentTrigger.values());
    }
}
