package io.casehub.work.progress.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.work.progress.ProgressDefinition;
import io.casehub.work.progress.ProgressDefinitionRegistry;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.validation.StepDefinitionValidator;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Startup
public class ProgressDefinitionYamlLoader {

    private static final Logger LOG = Logger.getLogger(ProgressDefinitionYamlLoader.class);
    static final String RESOURCE_PATH = "META-INF/work-progress-definitions.yaml";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(env|sys)\\.([^:}]+)(?::-((?:[^}]*)))?}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final StepDefinitionValidator STEP_VALIDATOR = new StepDefinitionValidator();

    @Inject
    ProgressDefinitionRegistry registry;

    @PostConstruct
    void load() {
        loadFromClasspath();
    }

    void loadFromClasspath() {
        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader().getResources(RESOURCE_PATH);
            var urls = Collections.list(resources);
            if (urls.isEmpty()) {
                LOG.info("No " + RESOURCE_PATH + " found on classpath");
                return;
            }
            for (URL url : urls) {
                LOG.infof("Loading progress definitions from %s", url);
                try (InputStream is = url.openStream()) {
                    JsonNode root = YAML_MAPPER.readTree(is);
                    JsonNode defs = root.get("progressDefinitions");
                    if (defs == null || !defs.isArray()) {
                        LOG.warnf("No progressDefinitions array in %s — skipping", url);
                        continue;
                    }
                    for (JsonNode node : defs) {
                        ProgressDefinition pd = parseDefinition(node);
                        registry.register(pd);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover " + RESOURCE_PATH, e);
        }
    }

    private ProgressDefinition parseDefinition(JsonNode node) {
        String name = interpolate(textOrNull(node, "name"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("progressDefinition entry missing required 'name'");
        }

        JsonNode stagesNode = node.get("stages");
        String shapeType = textOrNull(node, "shapeType");
        if (shapeType == null && stagesNode != null) {
            shapeType = "step";
        }
        if (shapeType == null) {
            throw new IllegalArgumentException(
                    "progressDefinition '" + name + "' requires shapeType (or stages to infer 'step')");
        }

        JsonNode definition = null;
        if (stagesNode != null && stagesNode.isArray()) {
            definition = buildStepDefinition(name, stagesNode, node.get("transitions"));
        }

        return new ProgressDefinition(name, shapeType, definition,
                interpolate(textOrNull(node, "rollbackPolicy")),
                interpolate(textOrNull(node, "visualisationMode")));
    }

    private JsonNode buildStepDefinition(String defName, JsonNode stagesNode, JsonNode transitionsNode) {
        List<StepDefinition> stepDefs = new ArrayList<>();
        for (JsonNode stage : stagesNode) {
            String stageName;
            boolean optional = false;
            List<String> dependsOn = List.of();
            String condition = null;

            if (stage.isTextual()) {
                stageName = interpolate(stage.asText());
            } else {
                stageName = interpolate(textOrNull(stage, "name"));
                if (stage.has("optional")) optional = stage.get("optional").asBoolean();
                if (stage.has("dependsOn")) {
                    List<String> deps = new ArrayList<>();
                    stage.get("dependsOn").forEach(d -> deps.add(d.asText()));
                    dependsOn = deps;
                }
                if (stage.has("condition")) condition = textOrNull(stage, "condition");
            }

            if (stageName == null || stageName.isBlank()) {
                throw new IllegalArgumentException(
                        "progressDefinition '" + defName + "' has a stage with no name");
            }
            stepDefs.add(new StepDefinition(stageName, optional, dependsOn, condition));
        }

        STEP_VALIDATOR.validate(stepDefs);

        ObjectNode result = JSON_MAPPER.createObjectNode();
        result.set("steps", JSON_MAPPER.valueToTree(stepDefs));

        if (transitionsNode != null && transitionsNode.isObject()) {
            var names = stepDefs.stream().map(StepDefinition::name).toList();
            transitionsNode.fields().forEachRemaining(entry -> {
                String from = entry.getKey();
                if (!names.contains(from)) {
                    throw new IllegalArgumentException(
                            "progressDefinition '" + defName + "' transition key '" + from + "' is not a defined stage");
                }
                entry.getValue().forEach(target -> {
                    if (!names.contains(target.asText())) {
                        throw new IllegalArgumentException(
                                "progressDefinition '" + defName + "' transition target '" + target.asText()
                                        + "' from '" + from + "' is not a defined stage");
                    }
                });
            });
            result.set("transitions", transitionsNode.deepCopy());
        }

        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    static String interpolate(String value) {
        if (value == null) return null;
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String type = m.group(1);
            String key = m.group(2);
            String resolved = "env".equals(type) ? System.getenv(key) : System.getProperty(key);
            if (resolved == null) {
                String defaultValue = m.group(3);
                resolved = defaultValue != null ? defaultValue : m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
