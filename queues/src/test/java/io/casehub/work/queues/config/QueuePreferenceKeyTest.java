package io.casehub.work.queues.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class QueuePreferenceKeyTest {

    @Test
    void snapshotInterval_defaultIsOneHour() {
        assertThat(QueueSnapshotInterval.KEY.defaultValue().duration())
                .isEqualTo(Duration.ofHours(1));
    }

    @Test
    void snapshotInterval_parsesIso8601() {
        final var parsed = QueueSnapshotInterval.KEY.parse("PT30M");
        assertThat(parsed.duration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void trendRetention_defaultIsSevenDays() {
        assertThat(QueueTrendRetention.KEY.defaultValue().duration())
                .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void trendRetention_parsesIso8601() {
        final var parsed = QueueTrendRetention.KEY.parse("P30D");
        assertThat(parsed.duration()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void qualifiedNames_followNamespace() {
        assertThat(QueueSnapshotInterval.KEY.qualifiedName())
                .isEqualTo("casehub.work.queues.snapshot-interval");
        assertThat(QueueTrendRetention.KEY.qualifiedName())
                .isEqualTo("casehub.work.queues.trend-retention");
    }
}
