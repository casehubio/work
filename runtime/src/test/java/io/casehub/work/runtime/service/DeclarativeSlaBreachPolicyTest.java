package io.casehub.work.runtime.service;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.SlaBreachContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeSlaBreachPolicyTest {

    private DeclarativeSlaBreachPolicy policy;
    private SlaDefaultsYamlLoader loader;
    private int defaultExpiryHours;

    @BeforeEach
    void setUp() {
        defaultExpiryHours = 24;
        io.casehub.work.api.spi.SlaBreachPolicy fallback = new io.casehub.work.api.spi.SlaBreachPolicy() {
            @Override public String id() { return "test-fallback"; }
            @Override public BreachDecision onBreach(SlaBreachContext ctx) { return new BreachDecision.Fail("fallback-fired"); }
        };

        Map<Path, SlaDeclarativeConfig.ScopeConfig> scopes = new LinkedHashMap<>();
        scopes.put(Path.parse("casehubio/clinical"), new SlaDeclarativeConfig.ScopeConfig(
                new BreachAction.EscalateToAction("senior-reviewers", Duration.ofHours(24)),
                new BreachAction.EscalateToAction("team-leads", null),
                null, null));
        scopes.put(Path.parse("casehubio/clinical/triage"), new SlaDeclarativeConfig.ScopeConfig(
                new BreachAction.ExhaustedAction("triage-sla-exceeded"),
                null, null, null));

        var config = new SlaDeclarativeConfig(
                new BreachAction.FailAction("sla-breach"),
                new BreachAction.ExtendAction(null),
                4, 8, scopes);

        loader = new SlaDefaultsYamlLoader();
        loader.loadedConfig = config;

        policy = new DeclarativeSlaBreachPolicy();
        policy.loader = loader;
        policy.fallbackPolicyRef = fallback;
        policy.defaultExpiryHours = defaultExpiryHours;
    }

    private SlaBreachContext ctx(String scope, BreachType type) {
        return ctx(scope, type, Set.of("group-a"));
    }

    private SlaBreachContext ctx(String scope, BreachType type, Set<String> candidateGroups) {
        return new SlaBreachContext(type,
                new BreachedTask(UUID.randomUUID(), null, "test", candidateGroups),
                scope != null ? Path.parse(scope) : Path.root(),
                new MapPreferences(Map.of()));
    }

    @Test
    void id() {
        assertThat(policy.id()).isEqualTo("declarative");
    }

    // ── Scope resolution ──

    @Test
    void exactScopeMatch() {
        BreachDecision d = policy.onBreach(ctx("casehubio/clinical/triage", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Exhausted("triage-sla-exceeded"));
    }

    @Test
    void parentScopeMatch() {
        BreachDecision d = policy.onBreach(ctx("casehubio/clinical/triage", BreachType.CLAIM_EXPIRED));
        assertThat(d).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) d).groups()).containsExactly("team-leads");
    }

    @Test
    void defaultsFallback() {
        BreachDecision d = policy.onBreach(ctx("casehubio/finance", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Fail("sla-breach"));
    }

    @Test
    void rootScopeGoesToDefaults() {
        BreachDecision d = policy.onBreach(ctx(null, BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Fail("sla-breach"));
    }

    @Test
    void claimExpiryDefaults() {
        BreachDecision d = policy.onBreach(ctx("casehubio/finance", BreachType.CLAIM_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(8)));
    }

    @Test
    void delegatesToFallbackWhenNoMatch() {
        loader.loadedConfig = new SlaDeclarativeConfig(null, null, null, null, Map.of());
        BreachDecision d = policy.onBreach(ctx("casehubio/clinical", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Fail("fallback-fired"));
    }

    @Test
    void delegatesToFallbackWhenNoConfig() {
        loader.loadedConfig = null;
        BreachDecision d = policy.onBreach(ctx("casehubio/clinical", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Fail("fallback-fired"));
    }

    // ── Self-detection guard ──

    @Test
    void escalateToSkippedWhenAlreadyAtTargetGroup() {
        var ctxAtTarget = ctx("casehubio/clinical", BreachType.COMPLETION_EXPIRED, Set.of("senior-reviewers"));
        BreachDecision d = policy.onBreach(ctxAtTarget);
        assertThat(d).isEqualTo(new BreachDecision.Fail("sla-breach"));
    }

    @Test
    void escalateToNotSkippedWhenDifferentGroup() {
        BreachDecision d = policy.onBreach(ctx("casehubio/clinical", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) d).groups()).containsExactly("senior-reviewers");
    }

    @Test
    void defaultEscalateToSkippedWhenAlreadyAtTarget() {
        loader.loadedConfig = new SlaDeclarativeConfig(
                new BreachAction.EscalateToAction("default-group", null),
                null, null, null, Map.of());
        var ctxAtTarget = ctx("casehubio/unknown", BreachType.COMPLETION_EXPIRED, Set.of("default-group"));
        BreachDecision d = policy.onBreach(ctxAtTarget);
        assertThat(d).isEqualTo(new BreachDecision.Fail("fallback-fired"));
    }

    @Test
    void chainedUnwrapsWhenPrimaryAlreadyAtTarget() {
        java.util.Map<io.casehub.platform.api.path.Path, SlaDeclarativeConfig.ScopeConfig> scopes = new java.util.LinkedHashMap<>();
        scopes.put(io.casehub.platform.api.path.Path.parse("casehubio/clinical"), new SlaDeclarativeConfig.ScopeConfig(
                new BreachAction.ChainedAction(
                        new BreachAction.EscalateToAction("senior-reviewers", java.time.Duration.ofHours(24)),
                        new BreachAction.FailAction("escalation-exhausted")),
                null, null, null));
        loader.loadedConfig = new SlaDeclarativeConfig(null, null, null, null, scopes);

        var ctxAtTarget = ctx("casehubio/clinical", io.casehub.work.api.BreachType.COMPLETION_EXPIRED,
                              java.util.Set.of("senior-reviewers"));
        io.casehub.work.api.BreachDecision d = policy.onBreach(ctxAtTarget);
        assertThat(d).isEqualTo(new io.casehub.work.api.BreachDecision.Fail("escalation-exhausted"));
    }


    // ── extensionHours resolution ──

    @Test
    void extendUsesDefaultClaimExtensionHours() {
        BreachDecision d = policy.onBreach(ctx("casehubio/finance", BreachType.CLAIM_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(8)));
    }

    @Test
    void extendUsesScopeExtensionHoursWhenPresent() {
        Map<Path, SlaDeclarativeConfig.ScopeConfig> scopes = new LinkedHashMap<>();
        scopes.put(Path.parse("casehubio/clinical"), new SlaDeclarativeConfig.ScopeConfig(
                null, new BreachAction.ExtendAction(null), 12, null));
        loader.loadedConfig = new SlaDeclarativeConfig(null, null, 4, 8, scopes);

        BreachDecision d = policy.onBreach(ctx("casehubio/clinical", BreachType.CLAIM_EXPIRED));
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(12)));
    }

    @Test
    void escalateToUsesResolvedExtensionHoursAsDeadline() {
        Map<Path, SlaDeclarativeConfig.ScopeConfig> scopes = new LinkedHashMap<>();
        scopes.put(Path.parse("casehubio/clinical"), new SlaDeclarativeConfig.ScopeConfig(
                new BreachAction.EscalateToAction("sr", null), null, 12, null));
        loader.loadedConfig = new SlaDeclarativeConfig(null, null, 4, 8, scopes);

        BreachDecision d = policy.onBreach(ctx("casehubio/clinical", BreachType.COMPLETION_EXPIRED));
        assertThat(d).isEqualTo(BreachDecision.EscalateTo.to("sr").withDeadline(Duration.ofHours(12)));
    }
}
