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
}
