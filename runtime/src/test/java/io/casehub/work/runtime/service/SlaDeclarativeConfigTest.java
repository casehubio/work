package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;

class SlaDeclarativeConfigTest {

    @Test
    void constructWithDefaults() {
        var config = new SlaDeclarativeConfig(
                new BreachAction.FailAction("sla-breach"),
                new BreachAction.ExtendAction(null),
                4, 8, Map.of());
        assertThat(config.defaultOnCompletionExpiry()).isInstanceOf(BreachAction.FailAction.class);
        assertThat(config.defaultOnClaimExpiry()).isInstanceOf(BreachAction.ExtendAction.class);
        assertThat(config.extensionHours()).isEqualTo(4);
        assertThat(config.claimExtensionHours()).isEqualTo(8);
        assertThat(config.scopes()).isEmpty();
    }

    @Test
    void constructWithNullableFields() {
        var config = new SlaDeclarativeConfig(null, null, null, null, Map.of());
        assertThat(config.defaultOnCompletionExpiry()).isNull();
        assertThat(config.defaultOnClaimExpiry()).isNull();
        assertThat(config.extensionHours()).isNull();
        assertThat(config.claimExtensionHours()).isNull();
    }

    @Test
    void constructWithScopes() {
        var scope = new SlaDeclarativeConfig.ScopeConfig(
                new BreachAction.EscalateToAction("senior-reviewers", Duration.ofHours(24)),
                new BreachAction.EscalateToAction("team-leads", null),
                null, null);
        var config = new SlaDeclarativeConfig(null, null, null, null,
                Map.of(Path.parse("casehubio/clinical"), scope));
        assertThat(config.scopes()).hasSize(1);
        assertThat(config.scopes().get(Path.parse("casehubio/clinical"))).isEqualTo(scope);
    }

    @Test
    void scopeConfigWithExtensionHours() {
        var scope = new SlaDeclarativeConfig.ScopeConfig(null, null, 12, 16);
        assertThat(scope.extensionHours()).isEqualTo(12);
        assertThat(scope.claimExtensionHours()).isEqualTo(16);
    }
}
