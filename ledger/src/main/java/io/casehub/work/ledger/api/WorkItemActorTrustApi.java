package io.casehub.work.ledger.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.ActorTrustScore;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.work.ledger.api.dto.ActorTrustScoreResponse;

@McpDomain("work/actor-trust")
@ApplicationScoped
public class WorkItemActorTrustApi {

    @Inject ActorTrustScoreRepository trustScoreRepository;
    @Inject LedgerConfig config;

    @PlatformQuery("Get the computed trust score for a specific actor")
    public ActorTrustScoreResponse getActorTrust(@PathParam String actorId,
                                                 @ContextParam("tenancyId") String tenancyId) {
        if (!config.trustScore().enabled()) {
            return null;
        }
        return trustScoreRepository.findByActorId(actorId)
                .map(this::toResponse)
                .orElse(null);
    }

    private ActorTrustScoreResponse toResponse(ActorTrustScore s) {
        return new ActorTrustScoreResponse(s.actorId, s.actorType, s.trustScore,
                s.decisionCount, s.overturnedCount, s.attestationPositive,
                s.attestationNegative, s.lastComputedAt);
    }
}
