package io.casehub.work.queues.config;

import java.time.Duration;

import io.casehub.platform.api.preferences.DurationPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class QueueTrendRetention {
    private QueueTrendRetention() {}

    public static final PreferenceKey<DurationPreference> KEY =
            new PreferenceKey<>("casehub.work.queues", "trend-retention",
                    new DurationPreference(Duration.ofDays(7)),
                    s -> new DurationPreference(Duration.parse(s)));
}
