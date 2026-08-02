package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackDetectorTest {

    private final RollbackDetector detector = new RollbackDetector();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void percentageDecreaseIsRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 80);
        ObjectNode curr = mapper.createObjectNode().put("value", 60);
        assertThat(detector.isRollback("percentage", prev, curr)).isTrue();
    }

    @Test
    void percentageIncreaseIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 60);
        ObjectNode curr = mapper.createObjectNode().put("value", 80);
        assertThat(detector.isRollback("percentage", prev, curr)).isFalse();
    }

    @Test
    void percentageSameIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 50);
        ObjectNode curr = mapper.createObjectNode().put("value", 50);
        assertThat(detector.isRollback("percentage", prev, curr)).isFalse();
    }

    @Test
    void countCurrentDecreaseIsRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 35).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 30).put("total", 50);
        assertThat(detector.isRollback("count", prev, curr)).isTrue();
    }

    @Test
    void countCurrentIncreaseIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 30).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 35).put("total", 50);
        assertThat(detector.isRollback("count", prev, curr)).isFalse();
    }

    @Test
    void countTotalChangeAloneIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 30).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 30).put("total", 60);
        assertThat(detector.isRollback("count", prev, curr)).isFalse();
    }

    @Test
    void stepCompletedToActiveIsRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "completed");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr)).isTrue();
    }

    @Test
    void stepFailedToActiveIsRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "failed");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr)).isTrue();
    }

    @Test
    void stepPendingToActiveIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "pending");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr)).isFalse();
    }

    @Test
    void nullPreviousStateIsNotRollback() {
        ObjectNode curr = mapper.createObjectNode().put("value", 50);
        assertThat(detector.isRollback("percentage", null, curr)).isFalse();
    }
}
