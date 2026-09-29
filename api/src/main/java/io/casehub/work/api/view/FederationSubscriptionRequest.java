package io.casehub.work.api.view;

import java.util.List;

public record FederationSubscriptionRequest(
        String peerId,
        String callbackUrl,
        String baseUrl,
        String tenancyId,
        FederationFilterRequest filter,
        String capabilitiesJson,
        String hmacSecret) {
}
