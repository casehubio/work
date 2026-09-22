package io.casehub.work.rest.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.path.Path;
import io.casehub.work.api.spi.WorkItemVocabularyApi;
import io.casehub.work.api.view.AddDefinitionRequest;
import io.casehub.work.api.view.AddDefinitionResult;
import io.casehub.work.api.view.LabelDefinitionView;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.service.LabelVocabularyService;

@ApplicationScoped
public class DefaultWorkItemVocabularyApi implements WorkItemVocabularyApi {

    @Inject
    LabelVocabularyService vocabularyService;

    @Override
    public List<LabelDefinitionView> listAll(String tenancyId) {
        return vocabularyService.listAllDefinitions().stream()
                .map(sd -> new LabelDefinitionView(
                        sd.definition().id,
                        sd.definition().path.value(),
                        sd.definition().vocabularyId,
                        sd.scope().value(),
                        sd.definition().description != null ? sd.definition().description : "",
                        sd.definition().createdBy,
                        sd.definition().createdAt))
                .toList();
    }

    @Override
    public AddDefinitionResult addDefinition(AddDefinitionRequest request, String tenancyId) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (request.path().contains("*") || request.path().contains("?")) {
            throw new IllegalArgumentException("path must not contain wildcard characters");
        }

        final Path labelPath = Path.parse(request.path());

        final Path scopePath = (request.scope() == null || request.scope().isBlank())
                ? Path.root()
                : Path.parse(request.scope());

        final String vocabName = scopePath.value().isEmpty() ? "Global" : scopePath.value();
        final LabelVocabulary vocab = vocabularyService.findOrCreateVocabulary(scopePath, vocabName);

        final LabelDefinition def = vocabularyService.addDefinition(
                vocab.id, labelPath, request.description(),
                request.addedBy() != null ? request.addedBy() : "unknown");

        return new AddDefinitionResult(def.id, def.path.value(), scopePath.value());
    }
}
