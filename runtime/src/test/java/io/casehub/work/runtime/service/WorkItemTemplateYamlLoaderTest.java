package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

class WorkItemTemplateYamlLoaderTest {

    @Test
    void interpolateResolvesEnvVars() {
        String result = WorkItemTemplateYamlLoader.interpolate("team-${env.PATH}-end");
        assertThat(result).doesNotContain("${env.PATH}");
        assertThat(result).startsWith("team-");
    }

    @Test
    void interpolateLeavesUnresolvedVarsAsIs() {
        String result = WorkItemTemplateYamlLoader.interpolate("${env.NONEXISTENT_VAR_XYZ_12345}");
        assertThat(result).isEqualTo("${env.NONEXISTENT_VAR_XYZ_12345}");
    }

    @Test
    void interpolateHandlesNull() {
        assertThat(WorkItemTemplateYamlLoader.interpolate(null)).isNull();
    }

    @Test
    void interpolateResolvesSysProps() {
        System.setProperty("test.yaml.loader.prop", "resolved-value");
        try {
            String result = WorkItemTemplateYamlLoader.interpolate("prefix-${sys.test.yaml.loader.prop}-suffix");
            assertThat(result).isEqualTo("prefix-resolved-value-suffix");
        } finally {
            System.clearProperty("test.yaml.loader.prop");
        }
    }

    @Test
    void interpolateHandlesPlainStringWithoutVars() {
        assertThat(WorkItemTemplateYamlLoader.interpolate("plain-string")).isEqualTo("plain-string");
    }

    @Test
    void parsesTestFixtureYaml() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/work-templates.yaml")) {
            assertThat(is).isNotNull();
            JsonNode root = mapper.readTree(is);
            JsonNode templates = root.get("workItemTemplates");
            assertThat(templates).isNotNull();
            assertThat(templates.isArray()).isTrue();
            assertThat(templates.size()).isEqualTo(3);
            assertThat(templates.get(0).get("name").asText()).isEqualTo("test-basic");
            assertThat(templates.get(1).get("name").asText()).isEqualTo("test-full");
            assertThat(templates.get(1).get("instanceCount").asInt()).isEqualTo(3);
        }
    }
}
