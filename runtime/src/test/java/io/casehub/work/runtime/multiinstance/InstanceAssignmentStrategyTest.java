package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.casehub.work.api.WorkItem;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;

class InstanceAssignmentStrategyTest {

    private static WorkItem item() {
        return WorkItem.builder().build();
    }

    private static WorkItem parent(String candidateGroups, String candidateUsers) {
        return WorkItem.builder()
                .candidateGroups(candidateGroups)
                .candidateUsers(candidateUsers)
                .build();
    }

    // --- PoolAssignmentStrategy ---

    @Test
    void pool_copiesCandidateGroupsToAllInstances() {
        var strategy = new PoolAssignmentStrategy();
        var parent = parent("reviewers,approvers", null);
        List<Object> instances = new ArrayList<>(List.of(item(), item(), item()));
        strategy.assign(instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(3, 2, null, "pool", null, false, null)));

        assertThat(instances).allMatch(i -> "reviewers,approvers".equals(((WorkItem) i).candidateGroups()));
    }

    @Test
    void pool_copiesCandidateUsersToAllInstances() {
        var strategy = new PoolAssignmentStrategy();
        var parent = parent(null, "alice,bob,carol");
        List<Object> instances = new ArrayList<>(List.of(item(), item()));
        strategy.assign(instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(2, 1, null, "pool", null, false, null)));

        assertThat(instances).allMatch(i -> "alice,bob,carol".equals(((WorkItem) i).candidateUsers()));
    }

    // --- ExplicitListAssignmentStrategy ---

    @Test
    void explicit_assignsEachInstanceToCorrespondingAssignee() {
        var strategy = new ExplicitListAssignmentStrategy();
        List<Object> instances = new ArrayList<>(List.of(item(), item(), item()));
        strategy.assign(instances, new MultiInstanceContext(item(),
                new MultiInstanceConfig(3, 2, null, "explicit", null, false,
                        List.of("alice", "bob", "carol"))));

        assertThat(((WorkItem) instances.get(0)).assigneeId()).isEqualTo("alice");
        assertThat(((WorkItem) instances.get(1)).assigneeId()).isEqualTo("bob");
        assertThat(((WorkItem) instances.get(2)).assigneeId()).isEqualTo("carol");
    }

    @Test
    void explicit_throwsWhenListSizeMismatch() {
        var strategy = new ExplicitListAssignmentStrategy();
        List<Object> instances = new ArrayList<>(List.of(item(), item()));
        assertThatThrownBy(() -> strategy.assign(instances,
                new MultiInstanceContext(item(),
                        new MultiInstanceConfig(2, 1, null, "explicit", null, false,
                                List.of("alice")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitAssignees");
    }

    // --- CompositeInstanceAssignmentStrategy ---

    @Test
    void composite_appliesStrategiesInOrder() {
        var pool = new PoolAssignmentStrategy();
        var explicit = new ExplicitListAssignmentStrategy();
        var composite = new CompositeInstanceAssignmentStrategy(List.of(pool, explicit));

        var parent = parent("reviewers", null);
        List<Object> instances = new ArrayList<>(List.of(item(), item()));
        composite.assign(instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(2, 1, null, "composite", null, false,
                        List.of("alice", "bob"))));

        assertThat(instances).allMatch(i -> "reviewers".equals(((WorkItem) i).candidateGroups()));
        assertThat(((WorkItem) instances.get(0)).assigneeId()).isEqualTo("alice");
        assertThat(((WorkItem) instances.get(1)).assigneeId()).isEqualTo("bob");
    }
}
