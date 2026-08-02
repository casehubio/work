package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountValidatorTest {

    private final CountValidator validator = new CountValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void zeroOfFiftyIsValid() {
        ObjectNode state = mapper.createObjectNode().put("current", 0).put("total", 50);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void fiftyOfFiftyIsValid() {
        ObjectNode state = mapper.createObjectNode().put("current", 50).put("total", 50);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void currentExceedsTotalIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("current", 51).put("total", 50);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeCurrentIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("current", -1).put("total", 50);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTotalIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("current", 0).put("total", -1);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingCurrentIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("total", 50);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingTotalIsRejected() {
        ObjectNode state = mapper.createObjectNode().put("current", 0);
        assertThatThrownBy(() -> validator.validate(state, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitIsOptional() {
        ObjectNode state = mapper.createObjectNode().put("current", 23).put("total", 50).put("unit", "files");
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }

    @Test
    void unitAbsentIsValid() {
        ObjectNode state = mapper.createObjectNode().put("current", 23).put("total", 50);
        assertThatCode(() -> validator.validate(state, null)).doesNotThrowAnyException();
    }
}
