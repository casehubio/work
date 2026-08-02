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

class CountCompletedStrategyTest {

    private final CountCompletedStrategy strategy = new CountCompletedStrategy();
    private final ObjectMapper mapper = new ObjectMapper();

    private ProgressInstance child(ProgressStatus status) {
        return new ProgressInstance(UUID.randomUUID(), "t1", "workitem", UUID.randomUUID().toString(),
                UUID.randomUUID(), UUID.randomUUID(), "percentage", null,
                mapper.createObjectNode().put("value", 100), status, null,
                Instant.now(), Instant.now());
    }

    private ProgressInstance parent() {
        return new ProgressInstance(UUID.randomUUID(), "t1", "workitem", UUID.randomUUID().toString(),
                null, UUID.randomUUID(), "count", null,
                mapper.createObjectNode().put("current", 0).put("total", 0),
                ProgressStatus.ACTIVE, "count-completed", Instant.now(), Instant.now());
    }

    @Test
    void twoCompletedOneActive() {
        RollupContext ctx = new RollupContext(parent(), List.of(
                child(ProgressStatus.COMPLETED),
                child(ProgressStatus.COMPLETED),
                child(ProgressStatus.ACTIVE)
        ));
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("current").intValue()).isEqualTo(2);
        assertThat(result.get("total").intValue()).isEqualTo(3);
    }

    @Test
    void zeroChildren() {
        RollupContext ctx = new RollupContext(parent(), List.of());
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("current").intValue()).isEqualTo(0);
        assertThat(result.get("total").intValue()).isEqualTo(0);
    }

    @Test
    void allCompleted() {
        RollupContext ctx = new RollupContext(parent(), List.of(
                child(ProgressStatus.COMPLETED),
                child(ProgressStatus.COMPLETED),
                child(ProgressStatus.COMPLETED)
        ));
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("current").intValue()).isEqualTo(3);
        assertThat(result.get("total").intValue()).isEqualTo(3);
    }

    @Test
    void failedChildIncludedInTotalNotCurrent() {
        RollupContext ctx = new RollupContext(parent(), List.of(
                child(ProgressStatus.COMPLETED),
                child(ProgressStatus.FAILED),
                child(ProgressStatus.ACTIVE)
        ));
        JsonNode result = strategy.compute(ctx);
        assertThat(result.get("current").intValue()).isEqualTo(1);
        assertThat(result.get("total").intValue()).isEqualTo(3);
    }

    @Test
    void strategyIdIsCountCompleted() {
        assertThat(strategy.id()).isEqualTo("count-completed");
    }
}
