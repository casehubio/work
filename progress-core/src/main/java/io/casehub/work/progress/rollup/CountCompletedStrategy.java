package io.casehub.work.progress.rollup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.RollupContext;
import io.casehub.work.progress.RollupStrategy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CountCompletedStrategy implements RollupStrategy {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String id() {
        return "count-completed";
    }

    @Override
    public JsonNode compute(RollupContext context) {
        int total = context.children().size();
        int completed = (int) context.children().stream()
                .filter(c -> c.status() == ProgressStatus.COMPLETED)
                .count();
        return MAPPER.createObjectNode()
                .put("current", completed)
                .put("total", total);
    }
}
