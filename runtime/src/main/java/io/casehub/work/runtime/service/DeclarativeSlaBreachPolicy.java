package io.casehub.work.runtime.service;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;

@Unremovable
@ApplicationScoped
public class DeclarativeSlaBreachPolicy implements SlaBreachPolicy {

    @Inject
    SlaDefaultsYamlLoader loader;

    @Inject
    Provider<StrategyResolver> strategyResolverProvider;

    @Inject
    WorkItemsConfig config;

    volatile SlaBreachPolicy fallbackPolicyRef;
    int defaultExpiryHours;

    @Override
    public String id() {
        return "declarative";
    }

    @PostConstruct
    void init() {
        if (id().equals(config.sla().declarative().fallback())) {
            throw new IllegalStateException(
                    "casehub.work.sla.declarative.fallback cannot be '" + id() + "' — infinite recursion");
        }
        defaultExpiryHours = config.defaultExpiryHours();
    }

    private SlaBreachPolicy fallbackPolicy() {
        if (fallbackPolicyRef == null) {
            fallbackPolicyRef = strategyResolverProvider.get()
                    .resolve(SlaBreachPolicy.class, config.sla().declarative().fallback());
        }
        return fallbackPolicyRef;
    }

    @Override
    public BreachDecision onBreach(SlaBreachContext context) {
        SlaDeclarativeConfig cfg = loader.loadedConfig;
        if (cfg == null) return fallbackPolicy().onBreach(context);

        Set<String> groups = context.task().candidateGroups();
        BreachAction action = resolveByScope(cfg, context.scope(), context.breachType(), groups);

        if (action == null) {
            action = switch (context.breachType()) {
                case COMPLETION_EXPIRED -> cfg.defaultOnCompletionExpiry();
                case CLAIM_EXPIRED -> cfg.defaultOnClaimExpiry();
            };
            if (action instanceof BreachAction.EscalateToAction esc
                    && groups.contains(esc.group())) {
                action = null;
            }
        }

        if (action == null) return fallbackPolicy().onBreach(context);

        Integer extHours = resolveExtensionHours(cfg, context.scope(), context.breachType());
        return action.toBreachDecision(extHours, defaultExpiryHours);
    }

    private BreachAction resolveByScope(SlaDeclarativeConfig cfg, Path scope, BreachType type,
                                        Set<String> candidateGroups) {
        Path current = scope;
        while (current != null && !current.equals(Path.root())) {
            SlaDeclarativeConfig.ScopeConfig sc = cfg.scopes().get(current);
            if (sc != null) {
                BreachAction action = switch (type) {
                    case COMPLETION_EXPIRED -> sc.onCompletionExpiry();
                    case CLAIM_EXPIRED -> sc.onClaimExpiry();
                };
                if (action != null) {
                    if (action instanceof BreachAction.EscalateToAction esc
                            && candidateGroups.contains(esc.group())) {
                        current = current.parent();
                        continue;
                    }
                    return action;
                }
            }
            current = current.parent();
        }
        return null;
    }

    private Integer resolveExtensionHours(SlaDeclarativeConfig cfg, Path scope, BreachType type) {
        Path current = scope;
        while (current != null && !current.equals(Path.root())) {
            SlaDeclarativeConfig.ScopeConfig sc = cfg.scopes().get(current);
            if (sc != null) {
                Integer hours = type == BreachType.CLAIM_EXPIRED
                        ? (sc.claimExtensionHours() != null ? sc.claimExtensionHours() : sc.extensionHours())
                        : sc.extensionHours();
                if (hours != null) return hours;
            }
            current = current.parent();
        }
        return type == BreachType.CLAIM_EXPIRED
                ? (cfg.claimExtensionHours() != null ? cfg.claimExtensionHours() : cfg.extensionHours())
                : cfg.extensionHours();
    }
}
