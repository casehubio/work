package io.casehub.work.api.view;

public record CreateQueueRequest(String name, String labelPattern, String scope,
                                 String additionalConditions, String sortField, String sortDirection) {
}
