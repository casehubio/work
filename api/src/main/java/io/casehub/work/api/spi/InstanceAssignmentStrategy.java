package io.casehub.work.api.spi;

import io.casehub.platform.api.routing.NamedStrategy;
import io.casehub.work.api.MultiInstanceContext;

import java.util.List;

/**
 * Pluggable multi-instance assignment SPI.
 *
 * <p>Implementations are CDI beans annotated {@code @ApplicationScoped} that return
 * a unique {@link #id()} string. The strategy is selected per-template via the
 * {@code assignmentStrategy} field on {@code WorkItemTemplate}.
 *
 * <p>Built-in implementations:
 * <ul>
 * <li>{@code PoolAssignmentStrategy} (id: "pool") — copies parent candidates to all children (default)
 * <li>{@code RoundRobinAssignmentStrategy} (id: "round-robin") — distributes across workers
 * <li>{@code ExplicitListAssignmentStrategy} (id: "explicit") — assigns from explicit list
 * <li>{@code CompositeInstanceAssignmentStrategy} (id: "composite") — chains multiple strategies
 * </ul>
 */
public interface InstanceAssignmentStrategy extends NamedStrategy {
    /**
     * Assign candidate users/groups to each instance.
     * Implementations mutate instance fields (candidateGroups, candidateUsers, assigneeId).
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    void assign(List<Object> instances, MultiInstanceContext context);
}
