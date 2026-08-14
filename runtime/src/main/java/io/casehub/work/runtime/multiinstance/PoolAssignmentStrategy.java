package io.casehub.work.runtime.multiinstance;

import java.util.List;

import io.casehub.work.api.WorkItem;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;

import io.casehub.work.api.spi.InstanceAssignmentStrategy;
import io.casehub.work.api.MultiInstanceContext;

/**
 * Copies the parent WorkItem's candidateGroups and candidateUsers to every child instance.
 *
 * <p>
 * This is the default strategy: all children are placed in the same candidate pool,
 * and whoever claims first gets the assignment. Suitable when any member of a team
 * can handle any instance.
 */
@Unremovable
@ApplicationScoped
public class PoolAssignmentStrategy implements InstanceAssignmentStrategy {

    @Override
    public String id() { return "pool"; }

    /**
     * Copies candidateGroups and candidateUsers from the parent WorkItem to all instances.
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     */
    @Override
    public void assign(final List<Object> instances, final MultiInstanceContext context) {
        final WorkItem parent = (WorkItem) context.parent();
        if (parent.candidateGroups() == null && parent.candidateUsers() == null) {
            return;
        }
        for (int i = 0; i < instances.size(); i++) {
            final WorkItem child = (WorkItem) instances.get(i);
            instances.set(i, child.toBuilder()
                    .candidateGroups(parent.candidateGroups())
                    .candidateUsers(parent.candidateUsers())
                    .build());
        }
    }
}
