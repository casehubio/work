package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.EnumOption;
import io.casehub.platform.api.preferences.PreferenceConstraintKeys;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.casehub.work.api.DeclineTarget;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WorkPreferenceRegistrar {

    @Inject PreferenceSchemaRegistry registry;

    void onStart(@Observes StartupEvent event) {
        registry.register(PreferenceSchemaDescriptor.of(DeclineTarget.KEY)
                .label("Decline target")
                .description("Where a delegated work item returns when the delegatee declines")
                .type("enum")
                .options(List.of(
                        new EnumOption("POOL", "Return to pool"),
                        new EnumOption("DELEGATOR", "Return to delegator")))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(WorkPreferenceKeys.DEFAULT_EXPIRY_HOURS)
                .label("Default SLA hours")
                .description("Hours before a work item expires when no explicit deadline is set")
                .constraints(Map.of(
                        PreferenceConstraintKeys.MIN, 1,
                        PreferenceConstraintKeys.MAX, 720))
                .build());

        registry.register(PreferenceSchemaDescriptor.of(WorkPreferenceKeys.DEFAULT_CLAIM_HOURS)
                .label("Default claim hours")
                .description("Hours before an unclaimed work item must be claimed; 0 disables")
                .constraints(Map.of(
                        PreferenceConstraintKeys.MIN, 0,
                        PreferenceConstraintKeys.MAX, 168))
                .build());
    }
}
