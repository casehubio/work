package io.casehub.work.queues.config;

import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class QueuePreferenceRegistrar {

    @Inject PreferenceSchemaRegistry registry;

    void onStart(@Observes StartupEvent event) {
        registry.register(PreferenceSchemaDescriptor.of(QueueSnapshotInterval.KEY)
                .label("Queue snapshot interval")
                .description("How often queue metrics are snapshotted for trend analysis")
                .build());

        registry.register(PreferenceSchemaDescriptor.of(QueueTrendRetention.KEY)
                .label("Queue trend retention")
                .description("How long queue trend snapshots are retained before purge")
                .build());
    }
}
