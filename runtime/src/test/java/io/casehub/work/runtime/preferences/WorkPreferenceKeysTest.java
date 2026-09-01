package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.IntPreference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkPreferenceKeysTest {

    @Test
    void slaOnCompletionExpiryKey() {
        assertThat(WorkPreferenceKeys.SLA_ON_COMPLETION_EXPIRY.qualifiedName())
                .isEqualTo("casehub.work.sla.on-completion-expiry");
        var parsed = WorkPreferenceKeys.SLA_ON_COMPLETION_EXPIRY.parse("fail");
        assertThat(parsed.action()).isNotNull();
    }

    @Test
    void slaOnClaimExpiryKey() {
        assertThat(WorkPreferenceKeys.SLA_ON_CLAIM_EXPIRY.qualifiedName())
                .isEqualTo("casehub.work.sla.on-claim-expiry");
        var parsed = WorkPreferenceKeys.SLA_ON_CLAIM_EXPIRY.parse("extend:PT2H");
        assertThat(parsed.action()).isNotNull();
    }

    @Test
    void slaExtensionHoursKey() {
        assertThat(WorkPreferenceKeys.SLA_EXTENSION_HOURS.qualifiedName())
                .isEqualTo("casehub.work.sla.extension-hours");
        IntPreference parsed = WorkPreferenceKeys.SLA_EXTENSION_HOURS.parse("8");
        assertThat(parsed.value()).isEqualTo(8);
    }

    @Test
    void slaClaimExtensionHoursKey() {
        assertThat(WorkPreferenceKeys.SLA_CLAIM_EXTENSION_HOURS.qualifiedName())
                .isEqualTo("casehub.work.sla.claim-extension-hours");
        IntPreference parsed = WorkPreferenceKeys.SLA_CLAIM_EXTENSION_HOURS.parse("12");
        assertThat(parsed.value()).isEqualTo(12);
    }

    @Test
    void defaultsAreNonNull() {
        assertThat(WorkPreferenceKeys.SLA_ON_COMPLETION_EXPIRY.defaultValue()).isNotNull();
        assertThat(WorkPreferenceKeys.SLA_ON_CLAIM_EXPIRY.defaultValue()).isNotNull();
        assertThat(WorkPreferenceKeys.SLA_EXTENSION_HOURS.defaultValue()).isNotNull();
        assertThat(WorkPreferenceKeys.SLA_CLAIM_EXTENSION_HOURS.defaultValue()).isNotNull();
    }
}
