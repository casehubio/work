package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;

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
}
