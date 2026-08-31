package io.casehub.work.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Startup
public class WorkItemTemplateYamlLoader {

    private static final Logger LOG = Logger.getLogger(WorkItemTemplateYamlLoader.class);
    private static final String RESOURCE_PATH = "META-INF/work-templates.yaml";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(env|sys)\\.([^:}]+)(?::-((?:[^}]*)))?}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @PostConstruct
    @Transactional
    void loadTemplates() {
        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader().getResources(RESOURCE_PATH);
            for (URL url : Collections.list(resources)) {
                loadFromUrl(url);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover " + RESOURCE_PATH, e);
        }
    }

    private void loadFromUrl(URL url) {
        LOG.infof("Loading work-item templates from %s", url);
        try (InputStream is = url.openStream()) {
            JsonNode root = YAML_MAPPER.readTree(is);
            JsonNode templates = root.get("workItemTemplates");
            if (templates == null || !templates.isArray()) {
                LOG.warnf("No workItemTemplates array in %s — skipping", url);
                return;
            }
            for (JsonNode node : templates) {
                WorkItemTemplate template = mapToEntity(node);
                template.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
                upsertByName(template, url.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse " + url + ": " + e.getMessage(), e);
        }
    }

    private WorkItemTemplate mapToEntity(JsonNode node) {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = interpolate(textOrNull(node, "name"));
        t.description = interpolate(textOrNull(node, "description"));
        t.candidateGroups = interpolate(textOrNull(node, "candidateGroups"));
        t.candidateUsers = interpolate(textOrNull(node, "candidateUsers"));
        t.requiredCapabilities = interpolate(textOrNull(node, "requiredCapabilities"));
        t.defaultPayload = textOrNull(node, "defaultPayload");
        t.labelPaths = textOrNull(node, "labelPaths");
        t.typePaths = textOrNull(node, "typePaths");
        t.outcomes = textOrNull(node, "outcomes");
        t.excludedUsers = interpolate(textOrNull(node, "excludedUsers"));
        t.excludedGroups = interpolate(textOrNull(node, "excludedGroups"));
        t.scope = interpolate(textOrNull(node, "scope"));
        t.inputDataSchema = textOrNull(node, "inputDataSchema");
        t.outputDataSchema = textOrNull(node, "outputDataSchema");
        t.assignmentStrategy = textOrNull(node, "assignmentStrategy");
        t.onThresholdReached = textOrNull(node, "onThresholdReached");
        t.parentRole = textOrNull(node, "parentRole");
        if (node.has("priority")) {
            t.priority = WorkItemPriority.valueOf(node.get("priority").asText());
        }
        if (node.has("defaultExpiryHours")) t.defaultExpiryHours = node.get("defaultExpiryHours").asInt();
        if (node.has("defaultClaimHours")) t.defaultClaimHours = node.get("defaultClaimHours").asInt();
        if (node.has("defaultExpiryBusinessHours")) t.defaultExpiryBusinessHours = node.get("defaultExpiryBusinessHours").asInt();
        if (node.has("defaultClaimBusinessHours")) t.defaultClaimBusinessHours = node.get("defaultClaimBusinessHours").asInt();
        if (node.has("instanceCount")) t.instanceCount = node.get("instanceCount").asInt();
        if (node.has("requiredCount")) t.requiredCount = node.get("requiredCount").asInt();
        if (node.has("allowSameAssignee")) t.allowSameAssignee = node.get("allowSameAssignee").asBoolean();
        if (t.name == null || t.name.isBlank()) {
            throw new IllegalArgumentException("WorkItemTemplate YAML entry missing required 'name' field");
        }
        return t;
    }

    private void upsertByName(WorkItemTemplate template, String sourceUrl) {
        Optional<WorkItemTemplate> existing = WorkItemTemplate
                .find("name = ?1 AND tenancyId = ?2", template.name, template.tenancyId)
                .firstResultOptional();
        if (existing.isPresent()) {
            LOG.warnf("WorkItemTemplate '%s' already exists (id=%s) — overwriting from %s",
                    template.name, existing.get().id, sourceUrl);
            template.id = existing.get().id;
            template.version = existing.get().version;
        }
        template.persistAndFlush();
        LOG.infof("Loaded WorkItemTemplate '%s' (id=%s)", template.name, template.id);
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

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
