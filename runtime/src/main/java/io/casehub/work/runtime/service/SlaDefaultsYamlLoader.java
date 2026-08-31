package io.casehub.work.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Startup
public class SlaDefaultsYamlLoader {

    private static final Logger LOG = Logger.getLogger(SlaDefaultsYamlLoader.class);
    static final String RESOURCE_PATH = "META-INF/work-sla-defaults.yaml";
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(env|sys)\\.([^}]+)}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Inject
    WorkItemsConfig config;

    SlaDeclarativeConfig loadedConfig;

    @PostConstruct
    void load() {
        SlaDeclarativeConfig yamlConfig = loadFromClasspath();
        this.loadedConfig = mergeConfigOverrides(yamlConfig);
    }

    SlaDeclarativeConfig loadFromClasspath() {
        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader().getResources(RESOURCE_PATH);
            var urls = Collections.list(resources);
            if (urls.isEmpty()) {
                LOG.info("No " + RESOURCE_PATH + " found on classpath — declarative SLA policy has no YAML config");
                return null;
            }

            BreachAction defaultOnCompletion = null;
            BreachAction defaultOnClaim = null;
            Integer extensionHours = null;
            Integer claimExtensionHours = null;
            boolean defaultsFound = false;
            Map<Path, SlaDeclarativeConfig.ScopeConfig> scopes = new LinkedHashMap<>();

            for (URL url : urls) {
                LOG.infof("Loading SLA defaults from %s", url);
                try (InputStream is = url.openStream()) {
                    JsonNode root = YAML_MAPPER.readTree(is);
                    JsonNode sla = root.get("sla");
                    if (sla == null) continue;

                    JsonNode defaults = sla.get("defaults");
                    if (defaults != null) {
                        if (defaultsFound) {
                            throw new IllegalStateException(
                                    "Multiple resources contribute sla.defaults — deployment-wide defaults must come from a single source. Conflicting resource: " + url);
                        }
                        defaultsFound = true;
                        if (defaults.has("onCompletionExpiry")) {
                            defaultOnCompletion = parseNodeAction(defaults.get("onCompletionExpiry"));
                        }
                        if (defaults.has("onClaimExpiry")) {
                            defaultOnClaim = parseNodeAction(defaults.get("onClaimExpiry"));
                        }
                        if (defaults.has("extensionHours")) {
                            extensionHours = defaults.get("extensionHours").asInt();
                            if (extensionHours <= 0) {
                                throw new IllegalArgumentException(
                                        "sla.defaults.extensionHours must be positive, was: " + extensionHours);
                            }
                        }
                        if (defaults.has("claimExtensionHours")) {
                            claimExtensionHours = defaults.get("claimExtensionHours").asInt();
                            if (claimExtensionHours <= 0) {
                                throw new IllegalArgumentException(
                                        "sla.defaults.claimExtensionHours must be positive, was: " + claimExtensionHours);
                            }
                        }
                    }

                    JsonNode scopesNode = sla.get("scopes");
                    if (scopesNode != null && scopesNode.isObject()) {
                        scopesNode.fields().forEachRemaining(entry -> {
                            String key = entry.getKey();
                            Path scopePath;
                            try {
                                scopePath = Path.parse(key);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        "Scope key '" + key + "' is invalid — " + e.getMessage(), e);
                            }
                            if (scopePath.equals(Path.root())) {
                                throw new IllegalArgumentException(
                                        "Scope key '" + key + "' resolves to root — use the defaults section instead");
                            }
                            if (scopes.containsKey(scopePath)) {
                                LOG.warnf("Duplicate scope key '%s' — overwriting with entry from %s", key, url);
                            }
                            scopes.put(scopePath, parseScopeConfig(entry.getValue()));
                        });
                    }
                }
            }
            return new SlaDeclarativeConfig(defaultOnCompletion, defaultOnClaim,
                    extensionHours, claimExtensionHours, Map.copyOf(scopes));
        } catch (IOException e) {
            throw new RuntimeException("Failed to discover " + RESOURCE_PATH, e);
        }
    }

    SlaDeclarativeConfig mergeConfigOverrides(SlaDeclarativeConfig yamlConfig) {
        if (config == null) return yamlConfig;
        var dc = config.sla().declarative().defaults();

        BreachAction onCompletion = yamlConfig != null ? yamlConfig.defaultOnCompletionExpiry() : null;
        BreachAction onClaim = yamlConfig != null ? yamlConfig.defaultOnClaimExpiry() : null;
        Integer extHours = yamlConfig != null ? yamlConfig.extensionHours() : null;
        Integer claimExtHours = yamlConfig != null ? yamlConfig.claimExtensionHours() : null;
        Map<Path, SlaDeclarativeConfig.ScopeConfig> scopes = yamlConfig != null ? yamlConfig.scopes() : Map.of();

        if (dc.onCompletionExpiry().isPresent()) {
            BreachAction override = BreachAction.parseColon(dc.onCompletionExpiry().get());
            if (onCompletion != null) {
                LOG.warnf("SLA default for on-completion-expiry overridden by config property (config: %s; YAML had: %s)",
                        dc.onCompletionExpiry().get(), onCompletion);
            }
            onCompletion = override;
        }
        if (dc.onClaimExpiry().isPresent()) {
            BreachAction override = BreachAction.parseColon(dc.onClaimExpiry().get());
            if (onClaim != null) {
                LOG.warnf("SLA default for on-claim-expiry overridden by config property (config: %s; YAML had: %s)",
                        dc.onClaimExpiry().get(), onClaim);
            }
            onClaim = override;
        }
        if (dc.extensionHours().isPresent()) {
            int override = dc.extensionHours().getAsInt();
            if (override <= 0) {
                throw new IllegalArgumentException(
                        "casehub.work.sla.declarative.defaults.extension-hours must be positive, was: " + override);
            }
            if (extHours != null) {
                LOG.warnf("SLA default for extension-hours overridden by config property (config: %d; YAML had: %d)",
                        override, extHours);
            }
            extHours = override;
        }
        if (dc.claimExtensionHours().isPresent()) {
            int override = dc.claimExtensionHours().getAsInt();
            if (override <= 0) {
                throw new IllegalArgumentException(
                        "casehub.work.sla.declarative.defaults.claim-extension-hours must be positive, was: " + override);
            }
            if (claimExtHours != null) {
                LOG.warnf("SLA default for claim-extension-hours overridden by config property (config: %d; YAML had: %d)",
                        override, claimExtHours);
            }
            claimExtHours = override;
        }

        return new SlaDeclarativeConfig(onCompletion, onClaim, extHours, claimExtHours, scopes);
    }

    private BreachAction parseNodeAction(JsonNode node) {
        Object value = YAML_MAPPER.convertValue(node, Object.class);
        if (value instanceof String s) {
            return BreachAction.parse(interpolate(s));
        }
        return BreachAction.parse(value);
    }

    private SlaDeclarativeConfig.ScopeConfig parseScopeConfig(JsonNode node) {
        BreachAction onCompletion = null;
        BreachAction onClaim = null;
        Integer extHours = null;
        Integer claimExtHours = null;
        if (node.has("onCompletionExpiry")) {
            onCompletion = parseNodeAction(node.get("onCompletionExpiry"));
        }
        if (node.has("onClaimExpiry")) {
            onClaim = parseNodeAction(node.get("onClaimExpiry"));
        }
        if (node.has("extensionHours")) {
            extHours = node.get("extensionHours").asInt();
            if (extHours <= 0) {
                throw new IllegalArgumentException("scope extensionHours must be positive, was: " + extHours);
            }
        }
        if (node.has("claimExtensionHours")) {
            claimExtHours = node.get("claimExtensionHours").asInt();
            if (claimExtHours <= 0) {
                throw new IllegalArgumentException("scope claimExtensionHours must be positive, was: " + claimExtHours);
            }
        }
        return new SlaDeclarativeConfig.ScopeConfig(onCompletion, onClaim, extHours, claimExtHours);
    }

    static String interpolate(String value) {
        if (value == null) return null;
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String type = m.group(1);
            String key = m.group(2);
            String resolved = "env".equals(type) ? System.getenv(key) : System.getProperty(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(resolved != null ? resolved : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
