package io.casehub.work.progress.rollup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.RollupContext;
import io.casehub.work.progress.RollupStrategy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class WeightedPercentageStrategy implements RollupStrategy {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String id() {
        return "weighted-percentage";
    }

    @Override
    public JsonNode compute(RollupContext context) {
        JsonNode weights = context.parent().state().path("weights");
        List<ProgressInstance> eligible = context.children().stream()
                .filter(c -> c.status() == ProgressStatus.ACTIVE || c.status() == ProgressStatus.COMPLETED)
                .toList();

        if (eligible.isEmpty()) {
            return MAPPER.createObjectNode().put("value", 0);
        }

        double weightedSum = 0;
        double totalWeight = 0;

        for (ProgressInstance child : eligible) {
            double weight = weights.path(child.scopeId()).asDouble(1.0);
            double value = child.state().path("value").asDouble(0);
            weightedSum += weight * value;
            totalWeight += weight;
        }

        int result = totalWeight > 0 ? (int) (weightedSum / totalWeight) : 0;
        return MAPPER.createObjectNode().put("value", result);
    }
}
