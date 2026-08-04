package io.casehub.work.progress.runtime.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.progress.ProgressChangeType;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.rollup.RollupEngine;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.casehub.work.runtime.service.TenantContextRunner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class RollupObserver {

    private static final Logger LOG = Logger.getLogger(RollupObserver.class);

    @Inject
    ProgressInstanceStore instanceStore;

    @Inject
    ProgressEventStore eventStore;

    @Inject
    RollupEngine rollupEngine;

    @Inject
    TenantContextRunner tenantContextRunner;

    @Inject
    Event<ProgressUpdatedEvent> cdiEvent;

    @ConfigProperty(name = "casehub.progress.rollup.max-retries", defaultValue = "3")
    int maxRetries;

    void onProgressUpdated(@ObservesAsync ProgressUpdatedEvent event) {
        if (event.parentProgressId() == null) {
            return;
        }
        tenantContextRunner.runInTenantContext(event.tenancyId(), () ->
                recomputeWithRetry(event.parentProgressId(), event.tenancyId()));
    }

    @Transactional
    public void recomputeWithRetry(java.util.UUID parentId, String tenancyId) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                recompute(parentId, tenancyId);
                return;
            } catch (OptimisticLockException e) {
                if (attempt == maxRetries - 1) {
                    LOG.warnf("Rollup OCC exhausted for parent %s after %d attempts", parentId, maxRetries);
                }
            }
        }
    }

    private void recompute(java.util.UUID parentId, String tenancyId) {
        ProgressInstance parent = instanceStore.get(parentId).orElse(null);
        if (parent == null || parent.rollupStrategyId() == null) {
            return;
        }

        List<ProgressInstance> children      = instanceStore.findByParentProgressId(parentId);
        JsonNode               previousState = parent.state();
        JsonNode               newState      = rollupEngine.recompute(parent, children);

        if (newState != null && rollupEngine.hasStateChanged(previousState, newState)) {
            ProgressInstance updated = new ProgressInstance(
                    parent.id(), parent.tenancyId(), parent.scopeType(), parent.scopeId(),
                    parent.parentProgressId(), parent.rootProgressId(),
                    parent.shapeType(), parent.definition(), newState,
                    parent.status(), parent.rollupStrategyId(),
                    parent.rollbackPolicy(), parent.visualisationMode(),
                    parent.createdAt(), Instant.now());
            instanceStore.put(updated);

            ProgressUpdatedEvent rollupEvent = new ProgressUpdatedEvent(
                    java.util.UUID.randomUUID(),
                    parent.id(), tenancyId,
                    parent.scopeType(), parent.scopeId(),
                    parent.parentProgressId(), parent.rootProgressId(),
                    parent.shapeType(), previousState, newState,
                    parent.status(), ProgressChangeType.STATE_UPDATED,
                    Instant.now());
            eventStore.append(rollupEvent);
            cdiEvent.fireAsync(rollupEvent);
        }}
}
