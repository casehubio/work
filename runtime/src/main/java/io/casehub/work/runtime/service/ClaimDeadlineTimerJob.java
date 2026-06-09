package io.casehub.work.runtime.service;

import java.util.UUID;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz job that fires when a WorkItem's claim deadline passes.
 *
 * <p>Delegates to {@link ExpiryLifecycleService#processClaimDeadline(UUID)} inside a
 * tenant-scoped request context so that {@code CurrentPrincipal.tenancyId()}
 * is available to downstream services.
 *
 * @see WorkItemTimerService#scheduleClaimDeadline
 */
public class ClaimDeadlineTimerJob implements Job {

    private static final Logger LOG = Logger.getLogger(ClaimDeadlineTimerJob.class);

    @Inject
    TenantContextRunner tenantContextRunner;

    @Inject
    ExpiryLifecycleService expiryService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String workItemId = context.getJobDetail().getJobDataMap().getString("workItemId");
        String tenancyId = context.getJobDetail().getJobDataMap().getString("tenancyId");

        LOG.debugf("Claim deadline timer fired for WorkItem %s (tenant: %s)", workItemId, tenancyId);

        tenantContextRunner.runInTenantContext(tenancyId, () -> {
            expiryService.processClaimDeadline(UUID.fromString(workItemId));
        });
    }
}
