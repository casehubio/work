package io.casehub.work.ai.service;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.ai.repository.EscalationSummaryStore;
import io.casehub.work.api.spi.WorkItemEscalationApi;
import io.casehub.work.api.view.EscalationSummaryView;

@ApplicationScoped
public class DefaultWorkItemEscalationApi implements WorkItemEscalationApi {

    @Inject
    EscalationSummaryStore summaryStore;

    @Override
    public List<EscalationSummaryView> list(UUID workItemId, String tenancyId) {
        return summaryStore.findByWorkItemId(workItemId).stream()
                .map(s -> new EscalationSummaryView(s.id, s.workItemId, s.eventType,
                        s.summary, s.generatedAt))
                .toList();
    }
}
