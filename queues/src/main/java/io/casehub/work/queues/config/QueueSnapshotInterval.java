package io.casehub.work.queues.config;

import java.time.Duration;

import io.casehub.platform.api.preferences.DurationPreference;
import io.casehub.platform.api.preferences.PreferenceKey;

public final class QueueSnapshotInterval {
    private QueueSnapshotInterval() {}

    public static final PreferenceKey<DurationPreference> KEY =
            new PreferenceKey<>("casehub.work.queues", "snapshot-interval",
                    new DurationPreference(Duration.ofHours(1)),
                    s -> new DurationPreference(Duration.parse(s)));
}
