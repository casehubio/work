package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentageValidatorTest {

    private final PercentageValidator validator = new PercentageValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void zeroIsValid() {
        ObjectNode state = mapper.createObjectNode().put("value", 0);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void hundredIsValid() {
        ObjectNode state = mapper.createObjectNode().put("value", 100);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void fiftyIsValid() {
        ObjectNode state = mapper.createObjectNode().put("value", 50);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void negativeIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("value", -1);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overHundredIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("value", 101);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fractionalIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("value", 50.5);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingValueIsRejected() {
        ObjectNode state = mapper.createObjectNode();
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shapeTypeIsPercentage() {
        assertThatCode(() -> validator.shapeType()).doesNotThrowAnyException();
        assert validator.shapeType().equals("percentage");
    }
}
