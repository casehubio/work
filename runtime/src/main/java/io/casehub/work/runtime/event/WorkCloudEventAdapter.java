package io.casehub.work.runtime.event;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.work.api.WorkCloudEventTypes;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;

@ApplicationScoped
public class WorkCloudEventAdapter {

    private static final Logger LOG = Logger.getLogger(WorkCloudEventAdapter.class);

    private final Event<CloudEvent> cloudEventBus;
    private final ObjectMapper objectMapper;

    @Inject
    public WorkCloudEventAdapter(final Event<CloudEvent> cloudEventBus, final ObjectMapper objectMapper) {
        this.cloudEventBus = cloudEventBus;
        this.objectMapper = objectMapper;
    }

    public void onWorkItemLifecycle(@ObservesAsync final WorkItemLifecycleEvent event) {
        buildAndFire(event.type(),
                URI.create(event.sourceUri()),
                event.subject(),
                event.occurredAt(),
                event.tenancyId(),
                event);
    }

    public void onGroupLifecycle(@ObservesAsync final WorkItemGroupLifecycleEvent event) {
        buildAndFire(WorkCloudEventTypes.GROUP_PREFIX + event.groupStatus().name().toLowerCase(Locale.ROOT),
                URI.create("/workitems/groups/" + event.groupId()),
                event.groupId().toString(),
                event.occurredAt(),
                event.tenancyId(),
                event);
    }

    private void buildAndFire(final String type, final URI source, final String subject,
            final Instant occurredAt, final String tenancyId, final Object payload) {
        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(payload);
        } catch (final JsonProcessingException e) {
            LOG.warnf("Serialisation failed for %s: %s", type, e.getMessage());
            data = new byte[0];
        }

        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(type)
                .withSource(source)
                .withSubject(subject)
                .withTime(occurredAt.atOffset(ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withData(data);

        if (tenancyId != null) {
            builder = builder.withExtension(WorkCloudEventTypes.EXT_TENANCY_ID, tenancyId);
        }

        cloudEventBus.fireAsync(builder.build())
                .exceptionally(ex -> {
                    LOG.warnf(ex, "CloudEvent dispatch failed for %s", type);
                    return null;
                });
    }
}
