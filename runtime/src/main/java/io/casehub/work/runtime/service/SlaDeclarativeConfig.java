package io.casehub.work.runtime.service;

import java.util.Map;

import io.casehub.platform.api.path.Path;

public record SlaDeclarativeConfig(
        BreachAction defaultOnCompletionExpiry,
        BreachAction defaultOnClaimExpiry,
        Integer extensionHours,
        Integer claimExtensionHours,
        Map<Path, ScopeConfig> scopes) {

    public record ScopeConfig(
            BreachAction onCompletionExpiry,
            BreachAction onClaimExpiry,
            Integer extensionHours,
            Integer claimExtensionHours) {}
}
