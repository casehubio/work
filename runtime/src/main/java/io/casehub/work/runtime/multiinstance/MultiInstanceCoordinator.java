package io.casehub.work.runtime.multiinstance;

import io.casehub.work.api.WorkItemGroupLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
<<<<<<< HEAD
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
=======
import io.casehub.work.api.WorkItemLifecycleEvent;
>>>>>>> 10ac6d40 (feat(#405): dual-mode work — GraphQL, MCP, callback adapters)
import io.casehub.work.runtime.service.TenantContextRunner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Observes terminal {@link WorkItemLifecycleEvent} instances asynchronously and
 * delegates to {@link MultiInstanceGroupPolicy} to update group counters and
 * evaluate the M-of-N threshold.
 *
 * <p>
 * Using {@link ObservesAsync} ensures the child WorkItem's transaction is already
 * committed before the coordinator runs, so the policy sees consistent data.
 * {@link MultiInstanceGroupPolicy#process} is {@code @Transactional} and handles
 * its own transaction boundary.
 *
 * <p>
 * Group lifecycle events are fired <em>after</em> {@code process()} returns — i.e.
 * after the transaction commits — so that a concurrent transaction that rolls back
 * with OCC does not emit a spurious event.
 *
 * <p>
 * A single retry handles the rare case where two siblings complete concurrently
 * and contend on the spawn-group version column. In Quarkus/Narayana JTA, OCC
 * detected at commit time propagates as {@code RollbackException} wrapping
 * {@code OptimisticLockException} — catching the broad {@link Exception} type
 * ensures the retry fires regardless of how the JTA layer wraps the failure.
 * On the second attempt {@code policyTriggered=true} makes {@code process()}
 * return {@code null}, so the retry is safe even if the first attempt partially succeeded.
 */
@ApplicationScoped
public class MultiInstanceCoordinator {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(MultiInstanceCoordinator.class);


    @Inject
    MultiInstanceGroupPolicy policy;

    @Inject
    TenantContextRunner tenantContextRunner;

    void onChildTerminal(@ObservesAsync WorkItemLifecycleEvent event) {
        final io.casehub.work.api.WorkItem child = event.workItem();
        if (child.parentId() == null) {return;}
        if (!child.status().isTerminal()) {return;}

        tenantContextRunner.runInTenantContext(child.tenancyId(), () -> {
            final UUID           childId     = child.id();
            final WorkItemStatus childStatus = child.status();

            WorkItemGroupLifecycleEvent groupEvent = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    groupEvent = policy.process(childId, childStatus);
                    break;
                } catch (Exception e) {
                    if (attempt == 0) {
                        LOG.warnf("MultiInstance process failed for child %s (attempt 1), retrying: %s",
                                  childId, e.getMessage());
                    } else {
                        LOG.errorf(e, "MultiInstance process failed for child %s after retry", childId);
                        return;
                    }
                }
            }
            policy.fireEvent(groupEvent);
        });
    }
}
