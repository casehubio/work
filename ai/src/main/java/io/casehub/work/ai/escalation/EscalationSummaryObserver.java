package io.casehub.work.ai.escalation;

import io.casehub.work.ai.repository.EscalationSummaryStore;
import io.casehub.work.api.WorkEventType;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * CDI observer that generates an LLM escalation summary when a WorkItem
 * fires an {@code EXPIRED} or {@code CLAIM_EXPIRED} lifecycle event.
 *
 * <p>
 * The observer runs {@link TransactionPhase#AFTER_SUCCESS} so the WorkItem's
 * final state is committed before the summary is generated and persisted in
 * its own transaction.
 */
@ApplicationScoped
public class EscalationSummaryObserver {

    private static final Logger LOG = Logger.getLogger(EscalationSummaryObserver.class);

    @Inject
    EscalationSummaryService summaryService;

    @Inject
    EscalationSummaryStore summaryStore;

    void onEscalation(@Observes final WorkItemLifecycleEvent event) {
        final WorkEventType type = event.eventType();
        final WorkItem      wi   = event.workItem();
        if (wi == null) {return;}

        try {
            switch (type) {
                case SLA_REASSIGNED -> {
                    if (Boolean.TRUE.equals(wi.escalationGenerateSummary())) {
                        summaryStore.put(summaryService.buildSummary(wi.id(), type.name()));
                    }
                }
                case EXPIRED, CLAIM_EXPIRED -> {
                    if (Boolean.FALSE.equals(wi.escalationGenerateSummary())) {return;}
                    if (type == WorkEventType.CLAIM_EXPIRED
                        && wi.escalationOnClaimDeadline() != null
                        && wi.escalationGenerateSummary() != null) {return;}
                    summaryStore.put(summaryService.buildSummary(wi.id(), type.name()));
                }
                default -> {}
            }
        } catch (final Exception e) {
            LOG.warnf("Failed to generate escalation summary for event %s: %s",
                      type, e.getMessage());
        }
    }
}
