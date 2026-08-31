package io.casehub.work.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgressDefinitionRegistryTest {

    private ProgressDefinitionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProgressDefinitionRegistry();
    }

    @Test
    void registerAndGet() {
        var def = new ProgressDefinition("doc-review", "step", null, null, null);
        registry.register(def);
        assertThat(registry.get("doc-review")).contains(def);
    }

    @Test
    void getUnknownReturnsEmpty() {
        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void duplicateNameOverwrites() {
        var def1 = new ProgressDefinition("doc-review", "step", null, null, null);
        var def2 = new ProgressDefinition("doc-review", "percentage", null, null, null);
        registry.register(def1);
        registry.register(def2);
        assertThat(registry.get("doc-review")).contains(def2);
    }

    @Test
    void getAll() {
        registry.register(new ProgressDefinition("a", "step", null, null, null));
        registry.register(new ProgressDefinition("b", "percentage", null, null, null));
        assertThat(registry.getAll()).hasSize(2);
    }
}
