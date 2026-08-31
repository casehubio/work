package io.casehub.work.runtime.service;

import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlaDefaultsYamlLoaderTest {

    @Test
    void loadFromClasspath() {
        SlaDefaultsYamlLoader loader = new SlaDefaultsYamlLoader();
        SlaDeclarativeConfig config = loader.loadFromClasspath();

        assertThat(config).isNotNull();
        assertThat(config.defaultOnCompletionExpiry())
                .isEqualTo(new BreachAction.FailAction("sla-breach"));
        assertThat(config.defaultOnClaimExpiry())
                .isEqualTo(new BreachAction.ExtendAction(null));
        assertThat(config.extensionHours()).isEqualTo(4);
        assertThat(config.claimExtensionHours()).isEqualTo(8);
    }

    @Test
    void loadScopes() {
        SlaDefaultsYamlLoader loader = new SlaDefaultsYamlLoader();
        SlaDeclarativeConfig config = loader.loadFromClasspath();

        assertThat(config.scopes()).hasSize(2);

        var clinical = config.scopes().get(Path.parse("casehubio/clinical"));
        assertThat(clinical).isNotNull();
        assertThat(clinical.onCompletionExpiry())
                .isEqualTo(new BreachAction.EscalateToAction("senior-reviewers", Duration.ofHours(24)));
        assertThat(clinical.onClaimExpiry())
                .isEqualTo(new BreachAction.EscalateToAction("team-leads", null));

        var triage = config.scopes().get(Path.parse("casehubio/clinical/triage"));
        assertThat(triage).isNotNull();
        assertThat(triage.onCompletionExpiry())
                .isEqualTo(new BreachAction.ExhaustedAction("triage-sla-exceeded"));
    }

    @Test
    void variableInterpolation() {
        System.setProperty("test.sla.group", "interpolated-group");
        try {
            String result = SlaDefaultsYamlLoader.interpolate("${sys.test.sla.group}");
            assertThat(result).isEqualTo("interpolated-group");
        } finally {
            System.clearProperty("test.sla.group");
        }
    }

    @Test
    void interpolateNull() {
        assertThat(SlaDefaultsYamlLoader.interpolate(null)).isNull();
    }

    @Test
    void interpolatePlainString() {
        assertThat(SlaDefaultsYamlLoader.interpolate("plain-string")).isEqualTo("plain-string");
    }

    @Test
    void interpolateDefaultValueUsedWhenUnset() {
        assertThat(SlaDefaultsYamlLoader.interpolate("${env.NONEXISTENT_VAR_XYZ_99:-fallback-group}"))
                .isEqualTo("fallback-group");
    }

    @Test
    void interpolateDefaultValueIgnoredWhenSet() {
        System.setProperty("test.sla.default", "real-value");
        try {
            assertThat(SlaDefaultsYamlLoader.interpolate("${sys.test.sla.default:-ignored}"))
                    .isEqualTo("real-value");
        } finally {
            System.clearProperty("test.sla.default");
        }
    }

    @Test
    void interpolateDefaultValueEmpty() {
        assertThat(SlaDefaultsYamlLoader.interpolate("${env.NONEXISTENT_VAR_XYZ_99:-}"))
                .isEqualTo("");
    }

    @Test
    void interpolateDefaultValueWithColons() {
        assertThat(SlaDefaultsYamlLoader.interpolate("${env.NONEXISTENT_VAR_XYZ_99:-host:8080}"))
                .isEqualTo("host:8080");
    }

    @Test
    void reloadRefreshesConfig() {
        SlaDefaultsYamlLoader loader = new SlaDefaultsYamlLoader();
        SlaDeclarativeConfig  first  = loader.loadFromClasspath();
        assertThat(first).isNotNull();

        SlaDeclarativeConfig second = loader.loadFromClasspath();
        assertThat(second).isNotNull();
        assertThat(second.defaultOnCompletionExpiry()).isEqualTo(first.defaultOnCompletionExpiry());
    }


}
