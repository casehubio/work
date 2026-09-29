package io.casehub.work.api.view;

import java.util.UUID;

public record ResolutionSuggestionView(UUID workItemId, String suggestion, int basedOn, boolean modelAvailable) {
}
