package io.casehub.work.queues.service;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.DurationPreference;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.work.queues.config.QueueSnapshotInterval;
import io.casehub.work.queues.config.QueueTrendRetention;
import io.casehub.platform.api.view.CrossTenantSubjectViewStore;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.work.queues.model.QueueSnapshot;
import io.casehub.work.queues.repository.QueueSnapshotStore;
import io.casehub.work.runtime.service.TenantContextRunner;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class QueueSnapshotJob {

    private static final Logger LOG = Logger.getLogger(QueueSnapshotJob.class);

    @Inject
    CrossTenantSubjectViewStore crossTenantStore;

    @Inject
    SubjectViewStore viewStore;

    @Inject
    QueueSnapshotStore snapshotStore;

    @Inject
    QueueMembershipService membershipService;

    @Inject
    PreferenceProvider preferenceProvider;

    @Inject
    TenantContextRunner tenantContextRunner;

    @Scheduled(identity = "queue-snapshot-heartbeat", every = "5m", delayed = "30s")
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void tick() {
        final List<String> tenantIds;
        try {
            tenantIds = crossTenantStore.findDistinctTenancyIds();
        } catch (final Exception e) {
            LOG.warnf("Failed to query tenant IDs for queue snapshots: %s", e.getMessage());
            return;
        }
        for (final String tenancyId : tenantIds) {
            tenantContextRunner.runInTenantContext(tenancyId, () ->
                                                                      processForTenant(tenancyId));
        }
    }

    private void processForTenant(final String tenancyId) {
        try {
            final var prefs = preferenceProvider.resolve(
                    new SettingsScope(Path.root(), Instant.now()));
            final DurationPreference intervalPref =
                    prefs.getOrDefault(QueueSnapshotInterval.KEY, "");
            final DurationPreference retentionPref =
                    prefs.getOrDefault(QueueTrendRetention.KEY, "");
            final Duration interval  = intervalPref.duration();
            final Duration retention = retentionPref.duration();

            final List<SubjectViewSpec> queues = viewStore.findByTenancy(tenancyId);
            if (queues.isEmpty()) {return;}

            final List<UUID> queueIds = queues.stream()
                                              .map(SubjectViewSpec::id).toList();
            final Map<UUID, Instant> latestTimes =
                    snapshotStore.findLatestSnapshotTimes(queueIds);
            final Instant now = Instant.now();

            for (final SubjectViewSpec queue : queues) {
                final Instant lastSnapshot = latestTimes.get(queue.id());
                if (lastSnapshot != null
                    && Duration.between(lastSnapshot, now).compareTo(interval) < 0) {
                    continue;
                }
                snapshotQueue(queue, now);
            }

            snapshotStore.deleteOlderThan(now.minus(retention));
        } catch (final Exception e) {
            LOG.warnf("Queue snapshot tick failed for tenant %s: %s",
                      tenancyId, e.getMessage());
        }
    }

    private void snapshotQueue(final SubjectViewSpec queue, final Instant now) {
        try {
            final int           count    = membershipService.countMembers(queue);
            final QueueSnapshot snapshot = new QueueSnapshot();
            snapshot.queueViewId = queue.id();
            snapshot.memberCount = count;
            snapshot.snapshotAt  = now;
            snapshotStore.put(snapshot);
        } catch (final Exception e) {
            LOG.warnf("Snapshot failed for queue %s (%s): %s",
                      queue.id(), queue.name(), e.getMessage());
        }
    }
}
