package io.casehub.work.progress.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.work.progress.ProgressDefinition;
import io.casehub.work.progress.ProgressDefinitionRegistry;

class ProgressDefinitionYamlLoaderTest {

    @Test
    void loadFromClasspath() {
        ProgressDefinitionRegistry registry = new ProgressDefinitionRegistry();
        ProgressDefinitionYamlLoader loader = new ProgressDefinitionYamlLoader();
        loader.registry = registry;

        loader.loadFromClasspath();

        assertThat(registry.getAll()).hasSize(2);
    }

    @Test
    void loadStepDefinition() {
        ProgressDefinitionRegistry registry = new ProgressDefinitionRegistry();
        ProgressDefinitionYamlLoader loader = new ProgressDefinitionYamlLoader();
        loader.registry = registry;

        loader.loadFromClasspath();

        ProgressDefinition docReview = registry.get("document-review").orElseThrow();
        assertThat(docReview.shapeType()).isEqualTo("step");
        assertThat(docReview.rollbackPolicy()).isEqualTo("revert-to-previous");
        assertThat(docReview.visualisationMode()).isEqualTo("linear");
        assertThat(docReview.definition()).isNotNull();
        assertThat(docReview.definition().has("steps")).isTrue();
        assertThat(docReview.definition().get("steps").size()).isEqualTo(4);
        assertThat(docReview.definition().has("transitions")).isTrue();
        assertThat(docReview.definition().get("transitions").get("received").size()).isEqualTo(1);
    }

    @Test
    void loadPercentageDefinition() {
        ProgressDefinitionRegistry registry = new ProgressDefinitionRegistry();
        ProgressDefinitionYamlLoader loader = new ProgressDefinitionYamlLoader();
        loader.registry = registry;

        loader.loadFromClasspath();

        ProgressDefinition simple = registry.get("simple-percentage").orElseThrow();
        assertThat(simple.shapeType()).isEqualTo("percentage");
        assertThat(simple.definition()).isNull();
    }

    @Test
    void stageOptionalFlag() {
        ProgressDefinitionRegistry registry = new ProgressDefinitionRegistry();
        ProgressDefinitionYamlLoader loader = new ProgressDefinitionYamlLoader();
        loader.registry = registry;

        loader.loadFromClasspath();

        ProgressDefinition docReview = registry.get("document-review").orElseThrow();
        var steps = docReview.definition().get("steps");
        boolean rejectedOptional = false;
        for (var step : steps) {
            if ("rejected".equals(step.get("name").asText())) {
                rejectedOptional = step.get("optional").asBoolean();
            }
        }
        assertThat(rejectedOptional).isTrue();
    }

    @Test
    void variableInterpolation() {
        System.setProperty("test.progress.name", "interpolated");
        try {
            String result = ProgressDefinitionYamlLoader.interpolate("${sys.test.progress.name}");
            assertThat(result).isEqualTo("interpolated");
        } finally {
            System.clearProperty("test.progress.name");
        }
    }

    @Test
    void interpolateNull() {
        assertThat(ProgressDefinitionYamlLoader.interpolate(null)).isNull();
    }
}
