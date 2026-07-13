package io.casehub.work.engine;

import io.casehub.engine.internal.routing.EngineStrategyResolver;
import io.casehub.work.api.spi.ClaimSlaPolicy;
import io.casehub.work.api.spi.InstanceAssignmentStrategy;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;

/**
 * Registers casehub-work strategy beans with {@link EngineStrategyResolver}.
 *
 * <p>Quarkus ARC's {@code Instance<NamedStrategy>} does not resolve beans whose
 * NamedStrategy relationship is transitive (e.g. ContinuationPolicy → ClaimSlaPolicy
 * → NamedStrategy). Per-SPI-type {@code Instance<T>} injection finds them. This bean
 * bridges the gap by registering work strategies after the resolver is constructed.
 */
@ApplicationScoped
public class WorkStrategyContributor {

    @Inject EngineStrategyResolver resolver;
    @Inject @Any Instance<WorkerSelectionStrategy> workerStrategies;
    @Inject @Any Instance<ClaimSlaPolicy> claimPolicies;
    @Inject @Any Instance<SlaBreachPolicy> breachPolicies;
    @Inject @Any Instance<InstanceAssignmentStrategy> assignmentStrategies;

    void onStart(@Observes StartupEvent ev) {
        workerStrategies.forEach(s -> safeRegister(s));
        claimPolicies.forEach(s -> safeRegister(s));
        breachPolicies.forEach(s -> safeRegister(s));
        assignmentStrategies.forEach(s -> safeRegister(s));
    }

    private void safeRegister(io.casehub.platform.api.routing.NamedStrategy strategy) {
        try {
            resolver.registerEntry(strategy, false);
        } catch (IllegalStateException e) {
            // already registered — ignore
        }
    }
}
