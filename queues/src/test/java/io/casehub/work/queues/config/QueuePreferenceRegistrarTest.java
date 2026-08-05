package io.casehub.work.queues.config;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePreferenceRegistrarTest {

    private static final class TestSchemaRegistry implements PreferenceSchemaRegistry {
        private final ConcurrentHashMap<String, PreferenceSchemaDescriptor> entries = new ConcurrentHashMap<>();

        @Override public void register(PreferenceSchemaDescriptor descriptor) {
            entries.put(descriptor.qualifiedName(), descriptor);
        }
        @Override public Optional<PreferenceSchemaDescriptor> resolve(String qualifiedName) {
            return Optional.ofNullable(entries.get(qualifiedName));
        }
        @Override public Set<PreferenceSchemaDescriptor> discover() {
            return new HashSet<>(entries.values());
        }
    }

    private TestSchemaRegistry registry;
    private QueuePreferenceRegistrar registrar;

    @BeforeEach
    void setUp() {
        registry = new TestSchemaRegistry();
        registrar = new QueuePreferenceRegistrar();
        registrar.registry = registry;
        registrar.onStart(new StartupEvent());
    }

    @Test
    void registers_two_queue_keys() {
        assertThat(registry.discover()).hasSize(2);
    }

    @Test
    void snapshot_interval_is_duration() {
        Optional<PreferenceSchemaDescriptor> desc = registry.resolve("casehub.work.queues.snapshot-interval");
        assertThat(desc).isPresent();
        assertThat(desc.get().type()).isEqualTo("duration");
        assertThat(desc.get().defaultValue()).isEqualTo("PT1H");
        assertThat(desc.get().namespace()).isEqualTo("casehub.work.queues");
        assertThat(desc.get().name()).isEqualTo("snapshot-interval");
        assertThat(desc.get().label()).isEqualTo("Queue snapshot interval");
    }

    @Test
    void trend_retention_is_duration() {
        Optional<PreferenceSchemaDescriptor> desc = registry.resolve("casehub.work.queues.trend-retention");
        assertThat(desc).isPresent();
        assertThat(desc.get().type()).isEqualTo("duration");
        assertThat(desc.get().defaultValue()).isEqualTo("PT168H");
        assertThat(desc.get().namespace()).isEqualTo("casehub.work.queues");
        assertThat(desc.get().name()).isEqualTo("trend-retention");
        assertThat(desc.get().label()).isEqualTo("Queue trend retention");
    }

    @Test
    void idempotent_registration() {
        registrar.onStart(new StartupEvent());
        assertThat(registry.discover()).hasSize(2);
    }
}
