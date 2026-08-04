package io.casehub.work.progress.rollup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressStatus;
import io.casehub.work.progress.RollupContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AveragePercentageStrategyTest {

    private final AveragePercentageStrategy strategy = new AveragePercentageStrategy();
    private final ObjectMapper mapper = new ObjectMapper();

    private ProgressInstance childWithPercentage(int value, ProgressStatus status) {
        return new ProgressInstance(UUID.randomUUID(), "t1", "workitem", UUID.randomUUID().toString(),
                                    UUID.randomUUID(), UUID.randomUUID(), "percentage", null,
                                    mapper.createObjectNode().put("value", value), status, null,
                                    null, null, Instant.now(), Instant.now());}

    private ProgressInstance parent() {
        return new ProgressInstance(UUID.randomUUID(), "t1", "workitem", UUID.randomUUID().toString(),
                                    null, UUID.randomUUID(), "percentage", null,
                                    mapper.createObjectNode().put("value", 0),
                                    ProgressStatus.ACTIVE, "average-percentage", null, null, Instant.now(), Instant.now());}

    @Test
    void averageOfThreeChildren() {
        RollupContext ctx = new RollupContext(parent(), List.of(
                childWithPercentage(50, ProgressStatus.ACTIVE),
                childWithPercentage(80, ProgressStatus.COMPLETED),
                childWithPercentage(100, ProgressStatus.COMPLETED)
        ));
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("value").intValue()).isEqualTo(76);
    }

    @Test
    void zeroChildren() {
        RollupContext ctx = new RollupContext(parent(), List.of());
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("value").intValue()).isEqualTo(0);
    }

    @Test
    void failedChildExcludedFromAverage() {
        RollupContext ctx = new RollupContext(parent(), List.of(
                childWithPercentage(100, ProgressStatus.COMPLETED),
                childWithPercentage(0, ProgressStatus.FAILED),
                childWithPercentage(50, ProgressStatus.ACTIVE)
        ));
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("value").intValue()).isEqualTo(75);
    }

    @Test
    void strategyIdIsAveragePercentage() {
        assertThat(strategy.id()).isEqualTo("average-percentage");
    }
}
