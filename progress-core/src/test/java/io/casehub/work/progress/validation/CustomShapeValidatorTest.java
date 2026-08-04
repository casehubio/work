package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomShapeValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomShapeValidator validator = new CustomShapeValidator();

    private ObjectNode schemaDefinition(ObjectNode schema) {
        ObjectNode def = mapper.createObjectNode();
        def.set("schema", schema);
        return def;
    }

    private ObjectNode simpleSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode score = props.putObject("score");
        score.put("type", "number");
        score.put("minimum", 0);
        score.put("maximum", 100);
        schema.putArray("required").add("score");
        return schema;
    }

    @Test
    void validState_passes() {
        ObjectNode state = mapper.createObjectNode().put("score", 50);
        ObjectNode def = schemaDefinition(simpleSchema());
        assertThatNoException().isThrownBy(() -> validator.validate(state, def));
    }

    @Test
    void invalidState_rejectsOutOfRange() {
        ObjectNode state = mapper.createObjectNode().put("score", 150);
        ObjectNode def = schemaDefinition(simpleSchema());
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingRequiredField_rejects() {
        ObjectNode state = mapper.createObjectNode().put("label", "test");
        ObjectNode def = schemaDefinition(simpleSchema());
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionWithoutSchema_rejects() {
        ObjectNode state = mapper.createObjectNode().put("score", 50);
        ObjectNode def = mapper.createObjectNode();
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
    }

    @Test
    void rollbackFieldAndDetectorIdBothSet_rejects() {
        ObjectNode def = schemaDefinition(simpleSchema());
        def.put("rollbackField", "score");
        def.put("rollbackDetectorId", "custom-detector");
        ObjectNode state = mapper.createObjectNode().put("score", 50);
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void rollbackFieldNotInSchema_rejects() {
        ObjectNode def = schemaDefinition(simpleSchema());
        def.put("rollbackField", "nonexistent");
        ObjectNode state = mapper.createObjectNode().put("score", 50);
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rollbackField");
    }

    @Test
    void rollbackFieldNonNumericType_rejects() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("label").put("type", "string");
        schema.putArray("required").add("label");

        ObjectNode def = schemaDefinition(schema);
        def.put("rollbackField", "label");
        ObjectNode state = mapper.createObjectNode().put("label", "test");
        assertThatThrownBy(() -> validator.validate(state, def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
    }

    @Test
    void shapeType_isCustom() {
        assertThat(validator.shapeType()).isEqualTo("custom");
    }
}
