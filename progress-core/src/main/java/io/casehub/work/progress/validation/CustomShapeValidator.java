package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.util.List;
import java.util.Set;

public class CustomShapeValidator implements ShapeValidator {

    private static final int MAX_SCHEMA_SIZE = 65536;
    private static final Set<String> NUMERIC_TYPES = Set.of("number", "integer");
    private static final SchemaRegistry SCHEMA_REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    @Override
    public String shapeType() {
        return "custom";
    }

    @Override
    public void validate(JsonNode state, JsonNode definition) {
        if (definition == null || !definition.has("schema")) {
            throw new IllegalArgumentException("Custom shape requires 'schema' in definition");
        }

        JsonNode schemaNode = definition.get("schema");
        if (schemaNode.toString().length() > MAX_SCHEMA_SIZE) {
            throw new IllegalArgumentException("Schema exceeds maximum size of " + MAX_SCHEMA_SIZE + " bytes");
        }

        validateRollbackConfig(definition, schemaNode);

        Schema schema = SCHEMA_REGISTRY.getSchema(schemaNode);
        List<Error> errors = schema.validate(state);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("State does not conform to schema: " + errors.iterator().next().getMessage());
        }
    }

    private void validateRollbackConfig(JsonNode definition, JsonNode schemaNode) {
        boolean hasField = definition.has("rollbackField") && !definition.get("rollbackField").isNull();
        boolean hasDetector = definition.has("rollbackDetectorId") && !definition.get("rollbackDetectorId").isNull();

        if (hasField && hasDetector) {
            throw new IllegalArgumentException("rollbackField and rollbackDetectorId are mutually exclusive");
        }

        if (hasField) {
            String fieldName = definition.get("rollbackField").asText();
            JsonNode properties = schemaNode.path("properties");
            if (!properties.has(fieldName)) {
                throw new IllegalArgumentException("rollbackField '" + fieldName + "' not found in schema properties");
            }
            JsonNode fieldSchema = properties.get(fieldName);
            String fieldType = fieldSchema.path("type").asText("");
            if (!NUMERIC_TYPES.contains(fieldType)) {
                throw new IllegalArgumentException("rollbackField '" + fieldName + "' must be numeric type, got: " + fieldType);
            }
        }
    }
}
