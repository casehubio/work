package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.PreferenceConstraintKeys;
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

class WorkPreferenceRegistrarTest {

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
    private WorkPreferenceRegistrar registrar;

    @BeforeEach
    void setUp() {
        registry = new TestSchemaRegistry();
        registrar = new WorkPreferenceRegistrar();
        registrar.registry = registry;
        registrar.onStart(new StartupEvent());
    }

    @Test
    void registers_three_work_keys() {
        assertThat(registry.discover()).hasSize(3);
    }

    @Test
    void decline_target_is_enum_with_options() {
        Optional<PreferenceSchemaDescriptor> desc = registry.resolve("casehub.work.delegation.decline-target");
        assertThat(desc).isPresent();
        assertThat(desc.get().type()).isEqualTo("enum");
        assertThat(desc.get().defaultValue()).isEqualTo("pool");
        assertThat(desc.get().label()).isEqualTo("Decline target");
        assertThat(desc.get().options()).hasSize(2);
        assertThat(desc.get().options().get(0).value()).isEqualTo("POOL");
        assertThat(desc.get().options().get(0).label()).isEqualTo("Return to pool");
        assertThat(desc.get().options().get(1).value()).isEqualTo("DELEGATOR");
        assertThat(desc.get().options().get(1).label()).isEqualTo("Return to delegator");
    }

    @Test
    void default_expiry_hours_is_integer_with_range() {
        Optional<PreferenceSchemaDescriptor> desc = registry.resolve("casehub.work.sla.default-hours");
        assertThat(desc).isPresent();
        assertThat(desc.get().type()).isEqualTo("integer");
        assertThat(desc.get().defaultValue()).isEqualTo("24");
        assertThat(desc.get().namespace()).isEqualTo("casehub.work");
        assertThat(desc.get().name()).isEqualTo("sla.default-hours");
        assertThat(desc.get().label()).isEqualTo("Default SLA hours");
        assertThat(desc.get().constraints()).containsEntry(PreferenceConstraintKeys.MIN, 1);
        assertThat(desc.get().constraints()).containsEntry(PreferenceConstraintKeys.MAX, 720);
    }

    @Test
    void default_claim_hours_is_integer_with_range() {
        Optional<PreferenceSchemaDescriptor> desc = registry.resolve("casehub.work.sla.default-claim-hours");
        assertThat(desc).isPresent();
        assertThat(desc.get().type()).isEqualTo("integer");
        assertThat(desc.get().defaultValue()).isEqualTo("4");
        assertThat(desc.get().namespace()).isEqualTo("casehub.work");
        assertThat(desc.get().name()).isEqualTo("sla.default-claim-hours");
        assertThat(desc.get().label()).isEqualTo("Default claim hours");
        assertThat(desc.get().constraints()).containsEntry(PreferenceConstraintKeys.MIN, 0);
        assertThat(desc.get().constraints()).containsEntry(PreferenceConstraintKeys.MAX, 168);
    }

    @Test
    void idempotent_registration() {
        registrar.onStart(new StartupEvent());
        assertThat(registry.discover()).hasSize(3);
    }
}
