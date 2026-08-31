package io.casehub.work.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ProgressDefinitionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void constructValid() {
        JsonNode def = mapper.createArrayNode();
        var pd = new ProgressDefinition("doc-review", "step", def, "revert-to-previous", "linear");
        assertThat(pd.name()).isEqualTo("doc-review");
        assertThat(pd.shapeType()).isEqualTo("step");
        assertThat(pd.definition()).isEqualTo(def);
        assertThat(pd.rollbackPolicy()).isEqualTo("revert-to-previous");
        assertThat(pd.visualisationMode()).isEqualTo("linear");
    }

    @Test
    void constructMinimal() {
        var pd = new ProgressDefinition("simple", "percentage", null, null, null);
        assertThat(pd.name()).isEqualTo("simple");
        assertThat(pd.definition()).isNull();
    }

    @Test
    void nullNameThrows() {
        assertThatThrownBy(() -> new ProgressDefinition(null, "step", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void blankNameThrows() {
        assertThatThrownBy(() -> new ProgressDefinition("  ", "step", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void nullShapeTypeThrows() {
        assertThatThrownBy(() -> new ProgressDefinition("test", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shapeType");
    }
}
