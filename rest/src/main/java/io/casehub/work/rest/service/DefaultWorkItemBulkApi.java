package io.casehub.work.rest.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.spi.WorkItemBulkApi;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.view.BulkItemResult;
import io.casehub.work.api.view.BulkOperationRequest;

@ApplicationScoped
public class DefaultWorkItemBulkApi implements WorkItemBulkApi {

    static final int MAX_BATCH_SIZE = 100;

    @Inject
    WorkItemOperations workItemOperations;

    @Override
    public List<BulkItemResult> bulk(BulkOperationRequest request, String tenancyId) {
        if (request == null || request.operation() == null || request.operation().isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        if (request.workItemIds() == null || request.workItemIds().isEmpty()) {
            throw new IllegalArgumentException("workItemIds must not be empty");
        }
        if (request.workItemIds().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }

        final String op = request.operation().toLowerCase();
        if (!List.of("claim", "cancel").contains(op)) {
            throw new IllegalArgumentException(
                    "unknown operation '" + request.operation() + "'; supported: claim, cancel");
        }

        final String actor = request.actorId() != null ? request.actorId() : "unknown";
        final List<BulkItemResult> results = new ArrayList<>();

        for (final String idStr : request.workItemIds()) {
            results.add(apply(op, idStr, actor, request.reason()));
        }
        return results;
    }

    private BulkItemResult apply(String op, String idStr, String actor, String reason) {
        try {
            final UUID id = UUID.fromString(idStr);
            switch (op) {
                case "claim" -> workItemOperations.claim(id, actor);
                case "cancel" -> workItemOperations.cancel(id, actor, reason);
                default -> throw new IllegalArgumentException("Unknown operation: " + op);
            }
            return BulkItemResult.ok(idStr);
        } catch (Exception e) {
            return BulkItemResult.error(idStr, e.getMessage());
        }
    }
}
