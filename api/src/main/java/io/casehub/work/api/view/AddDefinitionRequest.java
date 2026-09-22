package io.casehub.work.api.view;

public record AddDefinitionRequest(String path, String description, String addedBy, String scope) {
}
