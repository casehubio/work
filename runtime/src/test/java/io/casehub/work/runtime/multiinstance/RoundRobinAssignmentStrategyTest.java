package io.casehub.work.runtime.multiinstance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import io.casehub.work.core.strategy.ClaimFirstStrategy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import io.casehub.work.core.strategy.RoundRobinStrategy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.model.WorkItem;

@ExtendWith(MockitoExtension.class)
class RoundRobinAssignmentStrategyTest {

    private static MultiInstanceContext context(final String candidateUsers) {
        final WorkItem parent = new WorkItem();
        parent.candidateUsers = candidateUsers;
        return new MultiInstanceContext(parent,
                new MultiInstanceConfig(1, 1, null, "round-robin", null, false, null));
    }

    private static List<WorkItem> oneInstance() {
        final WorkItem child = new WorkItem();
        return List.of(child);
    }

    private static RoundRobinAssignmentStrategy strategyResolving(
            final WorkerSelectionStrategy resolved, final String configId) {
        final StrategyResolver resolver = mock(StrategyResolver.class);
        when(resolver.resolve(WorkerSelectionStrategy.class, configId)).thenReturn(resolved);
        final WorkItemsConfig config = mock(WorkItemsConfig.class);
        final WorkItemsConfig.RoutingConfig routing = mock(WorkItemsConfig.RoutingConfig.class);
        when(config.routing()).thenReturn(routing);
        when(routing.strategy()).thenReturn(configId);
        return new RoundRobinAssignmentStrategy(() -> resolver, config);
    }

    @Test
    void roundRobinConfig_delegatesToRoundRobinStrategy() {
        final RoundRobinStrategy roundRobin = mock(RoundRobinStrategy.class);
        when(roundRobin.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("alice"));

        final var strategy = strategyResolving(roundRobin, "round-robin");
        strategy.assign((List) oneInstance(), context("alice,bob"));

        verify(roundRobin).select(any(), anyList());
    }

    @Test
    void claimFirstConfig_delegatesToClaimFirstStrategy() {
        final ClaimFirstStrategy claimFirst = mock(ClaimFirstStrategy.class);
        when(claimFirst.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("bob"));

        final var strategy = strategyResolving(claimFirst, "claim-first");
        strategy.assign((List) oneInstance(), context("alice,bob"));

        verify(claimFirst).select(any(), anyList());
    }

    @Test
    void leastLoadedConfig_delegatesToLeastLoadedStrategy() {
        final LeastLoadedStrategy leastLoaded = mock(LeastLoadedStrategy.class);
        when(leastLoaded.select(any(), anyList())).thenReturn(AssignmentDecision.assignTo("carol"));

        final var strategy = strategyResolving(leastLoaded, "least-loaded");
        strategy.assign((List) oneInstance(), context("alice,bob,carol"));

        verify(leastLoaded).select(any(), anyList());
    }
}
