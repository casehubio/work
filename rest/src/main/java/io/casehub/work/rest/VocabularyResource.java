package io.casehub.work.rest;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.service.LabelVocabularyService;

@Path("/vocabulary")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VocabularyResource {

    @Inject
    LabelVocabularyService vocabularyService;

    public record AddDefinitionRequest(String path, String description, String addedBy, String scope) {
    }

    /**
     * List all label definitions visible to the current tenant.
     * No scope filtering — scope enforcement is deferred.
     */
    @GET
    public List<Map<String, Object>> listAll() {
        return vocabularyService.listAllDefinitions().stream()
                .map(sd -> Map.<String, Object> of(
                        "id", sd.definition().id,
                        "path", sd.definition().path.value(),
                        "vocabularyId", sd.definition().vocabularyId,
                        "scope", sd.scope().value(),
                        "description", sd.definition().description != null ? sd.definition().description : "",
                        "createdBy", sd.definition().createdBy,
                        "createdAt", sd.definition().createdAt))
                .toList();
    }

    /**
     * Add a label definition to the vocabulary at the given scope.
     * Scope is a Path string in the request body (null/blank = root/global).
     */
    @POST
    @Transactional
    public Response addDefinition(final AddDefinitionRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "path is required")).build();
        }
        if (request.path().contains("*") || request.path().contains("?")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "path must not contain wildcard characters")).build();
        }

        final io.casehub.platform.api.path.Path labelPath;
        try {
            labelPath = io.casehub.platform.api.path.Path.parse(request.path());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid path: " + e.getMessage())).build();
        }

        final io.casehub.platform.api.path.Path scopePath;
        try {
            scopePath = (request.scope() == null || request.scope().isBlank())
                    ? io.casehub.platform.api.path.Path.root()
                    : io.casehub.platform.api.path.Path.parse(request.scope());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid scope: " + e.getMessage())).build();
        }

        final String vocabName = scopePath.value().isEmpty() ? "Global" : scopePath.value();
        final LabelVocabulary vocab = vocabularyService.findOrCreateVocabulary(scopePath, vocabName);

        final LabelDefinition def = vocabularyService.addDefinition(
                vocab.id, labelPath, request.description(),
                request.addedBy() != null ? request.addedBy() : "unknown");

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", def.id, "path", def.path.value(), "scope", scopePath.value()))
                .build();
    }
}
