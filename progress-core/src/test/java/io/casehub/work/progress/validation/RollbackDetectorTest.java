package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.work.progress.CustomRollbackDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackDetectorTest {

    private final RollbackDetector detector = new RollbackDetector();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void percentageDecreaseIsRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 80);
        ObjectNode curr = mapper.createObjectNode().put("value", 60);
        assertThat(detector.isRollback("percentage", prev, curr, null)).isTrue();
    }

    @Test
    void percentageIncreaseIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 60);
        ObjectNode curr = mapper.createObjectNode().put("value", 80);
        assertThat(detector.isRollback("percentage", prev, curr, null)).isFalse();
    }

    @Test
    void percentageSameIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("value", 50);
        ObjectNode curr = mapper.createObjectNode().put("value", 50);
        assertThat(detector.isRollback("percentage", prev, curr, null)).isFalse();
    }

    @Test
    void countCurrentDecreaseIsRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 35).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 30).put("total", 50);
        assertThat(detector.isRollback("count", prev, curr, null)).isTrue();
    }

    @Test
    void countCurrentIncreaseIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 30).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 35).put("total", 50);
        assertThat(detector.isRollback("count", prev, curr, null)).isFalse();
    }

    @Test
    void countTotalChangeAloneIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode().put("current", 30).put("total", 50);
        ObjectNode curr = mapper.createObjectNode().put("current", 30).put("total", 60);
        assertThat(detector.isRollback("count", prev, curr, null)).isFalse();
    }

    @Test
    void stepCompletedToActiveIsRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "completed");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr, null)).isTrue();
    }

    @Test
    void stepFailedToActiveIsRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "failed");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr, null)).isTrue();
    }

    @Test
    void stepPendingToActiveIsNotRollback() {
        ObjectNode prev = mapper.createObjectNode();
        ObjectNode prevSteps = prev.putObject("steps");
        prevSteps.putObject("a").put("status", "pending");

        ObjectNode curr = mapper.createObjectNode();
        ObjectNode currSteps = curr.putObject("steps");
        currSteps.putObject("a").put("status", "active");

        assertThat(detector.isRollback("step", prev, curr, null)).isFalse();
    }

    @Test
    void nullPreviousStateIsNotRollback() {
        ObjectNode curr = mapper.createObjectNode().put("value", 50);
        assertThat(detector.isRollback("percentage", null, curr, null)).isFalse();
    }

    @Test
    void customShape_noRollbackConfig_neverDetectsRollback() {
        ObjectNode prev = mapper.createObjectNode().put("score", 80);
        ObjectNode curr = mapper.createObjectNode().put("score", 50);
        ObjectNode def  = mapper.createObjectNode();
        def.putObject("schema");
        assertThat(detector.isRollback("custom", prev, curr, def)).isFalse();
    }

    @Test
    void customShape_rollbackField_detectsDecrease() {
        ObjectNode prev = mapper.createObjectNode().put("score", 80);
        ObjectNode curr = mapper.createObjectNode().put("score", 50);
        ObjectNode def  = mapper.createObjectNode();
        def.putObject("schema");
        def.put("rollbackField", "score");
        assertThat(detector.isRollback("custom", prev, curr, def)).isTrue();
    }

    @Test
    void customShape_rollbackField_noDetectionOnIncrease() {
        ObjectNode prev = mapper.createObjectNode().put("score", 50);
        ObjectNode curr = mapper.createObjectNode().put("score", 80);
        ObjectNode def  = mapper.createObjectNode();
        def.putObject("schema");
        def.put("rollbackField", "score");
        assertThat(detector.isRollback("custom", prev, curr, def)).isFalse();
    }

    @Test
    void customShape_rollbackField_missingFieldReturnsNonRollback() {
        ObjectNode prev = mapper.createObjectNode().put("score", 80);
        ObjectNode curr = mapper.createObjectNode();
        ObjectNode def  = mapper.createObjectNode();
        def.putObject("schema");
        def.put("rollbackField", "score");
        assertThat(detector.isRollback("custom", prev, curr, def)).isFalse();
    }

    @Test
    void customShape_rollbackDetectorId_delegatesToResolver() {
        ObjectNode prev = mapper.createObjectNode().put("score", 80);
        ObjectNode curr = mapper.createObjectNode().put("score", 50);
        ObjectNode def  = mapper.createObjectNode();
        def.putObject("schema");
        def.put("rollbackDetectorId", "always-true");

        CustomRollbackDetector alwaysTrue = new CustomRollbackDetector() {
            @Override
            public String id()                                            {return "always-true";}

            @Override
            public boolean isRollback(JsonNode p, JsonNode c, JsonNode d) {return true;}
        };
        RollbackDetector detectorWithResolver = new RollbackDetector(id -> alwaysTrue);
        assertThat(detectorWithResolver.isRollback("custom", prev, curr, def)).isTrue();}

    @Test
    void builtinShapes_ignoreDefinition() {
        ObjectNode prev = mapper.createObjectNode().put("value", 80);
        ObjectNode curr = mapper.createObjectNode().put("value", 60);
        assertThat(detector.isRollback("percentage", prev, curr, null)).isTrue();
    }
}
