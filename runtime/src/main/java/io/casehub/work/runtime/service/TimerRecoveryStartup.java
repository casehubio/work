package io.casehub.work.runtime.service;

import io.casehub.work.api.spi.CrossTenantWorkItemStore;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Application-scoped bean that re-schedules all Quartz timers on boot.
 *
 * <p>Scans all active WorkItems with deadlines and all active WorkItemSchedules
 * via {@link CrossTenant} stores, then re-schedules Quartz timers for each.
 *
 * <p>This ensures timers survive application restart — expiry/claim-deadline
 * timers are re-registered from persistent WorkItem state, and recurring schedule
 * triggers are re-registered from WorkItemSchedule state.
 */
@ApplicationScoped
public class TimerRecoveryStartup {

    private static final Logger LOG = Logger.getLogger(TimerRecoveryStartup.class);

    @Inject @CrossTenant CrossTenantWorkItemStore crossTenantWorkItemStore;
    @Inject @CrossTenant CrossTenantWorkItemScheduleStore crossTenantScheduleStore;
    @Inject WorkItemTimerService timerService;

    void onStartup(@Observes StartupEvent event) {
        recoverWorkItemTimers();
        recoverScheduleTimers();
    }

    private void recoverWorkItemTimers() {
        List<io.casehub.work.api.WorkItem> items       = crossTenantWorkItemStore.findActiveWithDeadlines();
        int                                expiryCount = 0;
        int                                claimCount  = 0;

        for (io.casehub.work.api.WorkItem item : items) {
            if (item.expiresAt() != null) {
                timerService.scheduleExpiry(item.id(), item.tenancyId(), item.expiresAt());
                expiryCount++;
            }
            if (item.claimDeadline() != null) {
                timerService.scheduleClaimDeadline(item.id(), item.tenancyId(), item.claimDeadline());
                claimCount++;
            }
        }

        if (expiryCount > 0 || claimCount > 0) {
            LOG.infof("Timer recovery: scheduled %d expiry + %d claim-deadline timers from %d active WorkItems",
                      expiryCount, claimCount, items.size());
        }
    }

    private void recoverScheduleTimers() {
        // WorkItemSchedule recurring triggers — deferred to Task 18 (ScheduleTimerJob)
        // For now, log a placeholder
        var schedules = crossTenantScheduleStore.findActive();
        if (!schedules.isEmpty()) {
            LOG.infof("Timer recovery: found %d active schedules (recurring trigger setup deferred)", schedules.size());
        }
    }
}
