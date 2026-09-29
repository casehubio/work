package io.casehub.work.ai.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.ai.suggestion.ResolutionSuggestionService;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.spi.WorkItemSuggestionApi;
import io.casehub.work.api.view.ResolutionSuggestionView;

@ApplicationScoped
public class DefaultWorkItemSuggestionApi implements WorkItemSuggestionApi {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    ResolutionSuggestionService suggestionService;

    @Override
    public ResolutionSuggestionView suggest(UUID workItemId, String tenancyId) {
        final WorkItem workItem = workItemStore.get(workItemId).orElse(null);
        if (workItem == null) {
            return null;
        }

        if (!suggestionService.isModelAvailable()) {
            return new ResolutionSuggestionView(workItemId, null, 0, false);
        }

        final int exampleCount = suggestionService.exampleCount(workItem);
        final String suggestion = suggestionService.suggest(workItem);

        if (suggestion == null) {
            return new ResolutionSuggestionView(workItemId, null, exampleCount, true);
        }

        return new ResolutionSuggestionView(workItemId, suggestion, exampleCount, true);
    }
}
