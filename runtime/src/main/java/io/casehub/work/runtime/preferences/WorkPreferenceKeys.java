package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.IntPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class WorkPreferenceKeys {

    private WorkPreferenceKeys() {}

    public static final PreferenceKey<IntPreference> DEFAULT_EXPIRY_HOURS =
            new PreferenceKey<>("casehub.work", "sla.default-hours",
                    IntPreference.of(24), IntPreference::parse);

    public static final PreferenceKey<IntPreference> DEFAULT_CLAIM_HOURS =
            new PreferenceKey<>("casehub.work", "sla.default-claim-hours",
                    IntPreference.of(4), IntPreference::parse);
    public static final PreferenceKey<BreachActionPreference> SLA_ON_COMPLETION_EXPIRY =
            new PreferenceKey<>("casehub.work", "sla.on-completion-expiry",
                                BreachActionPreference.UNSET, BreachActionPreference::parse);

    public static final PreferenceKey<BreachActionPreference> SLA_ON_CLAIM_EXPIRY =
            new PreferenceKey<>("casehub.work", "sla.on-claim-expiry",
                                BreachActionPreference.UNSET, BreachActionPreference::parse);

    public static final PreferenceKey<IntPreference> SLA_EXTENSION_HOURS =
            new PreferenceKey<>("casehub.work", "sla.extension-hours",
                                IntPreference.of(0), IntPreference::parse);

    public static final PreferenceKey<IntPreference> SLA_CLAIM_EXTENSION_HOURS =
            new PreferenceKey<>("casehub.work", "sla.claim-extension-hours",
                                IntPreference.of(0), IntPreference::parse);

}
