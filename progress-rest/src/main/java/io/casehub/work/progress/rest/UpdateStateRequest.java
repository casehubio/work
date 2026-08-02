package io.casehub.work.progress.rest;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateStateRequest(JsonNode state) {}
