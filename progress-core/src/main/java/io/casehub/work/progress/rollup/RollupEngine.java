package io.casehub.work.progress.rollup;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.RollupContext;
import io.casehub.work.progress.RollupStrategy;
import io.casehub.platform.api.routing.StrategyResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class RollupEngine {

    @Inject
    StrategyResolver strategyResolver;

    public JsonNode recompute(ProgressInstance parent, List<ProgressInstance> children) {
        if (parent.rollupStrategyId() == null) {
            return null;
        }

        RollupStrategy strategy = strategyResolver.resolve(RollupStrategy.class, parent.rollupStrategyId());
        RollupContext context = new RollupContext(parent, children);
        return strategy.compute(context);
    }

    public boolean hasStateChanged(JsonNode previousState, JsonNode newState) {
        if (previousState == null && newState == null) return false;
        if (previousState == null || newState == null) return true;
        return !previousState.equals(newState);
    }
}
