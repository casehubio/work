package io.casehub.work.progress;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Logger;

public class ProgressDefinitionRegistry {

    private static final Logger LOG = Logger.getLogger(ProgressDefinitionRegistry.class.getName());
    private final Map<String, ProgressDefinition> definitions = new ConcurrentHashMap<>();

    public void register(ProgressDefinition definition) {
        ProgressDefinition prev = definitions.put(definition.name(), definition);
        if (prev != null) {
            LOG.warning("ProgressDefinition '" + definition.name() + "' overwritten");
        }
    }

    public Optional<ProgressDefinition> get(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    public Collection<ProgressDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }
}
