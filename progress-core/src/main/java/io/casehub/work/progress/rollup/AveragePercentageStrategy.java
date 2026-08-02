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
public class AveragePercentageStrategy implements RollupStrategy {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String id() {
        return "average-percentage";
    }

    @Override
    public JsonNode compute(RollupContext context) {
        List<ProgressInstance> eligible = context.children().stream()
                .filter(c -> c.status() == ProgressStatus.ACTIVE || c.status() == ProgressStatus.COMPLETED)
                .toList();

        if (eligible.isEmpty()) {
            return MAPPER.createObjectNode().put("value", 0);
        }

        int sum = eligible.stream()
                .mapToInt(c -> c.state().path("value").asInt(0))
                .sum();

        return MAPPER.createObjectNode().put("value", sum / eligible.size());
    }
}
