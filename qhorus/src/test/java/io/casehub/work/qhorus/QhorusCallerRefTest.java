package io.casehub.work.qhorus;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QhorusCallerRefTest {

    @Test
    void encodeProducesCorrectFormat() {
        var channelId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        var ref = new QhorusCallerRef(channelId, 42L, "corr-001");
        assertThat(ref.encode()).isEqualTo("qhorus:a1b2c3d4-e5f6-7890-abcd-ef1234567890/42/corr-001");
    }

    @Test
    void parseRoundTrips() {
        var channelId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        var original = new QhorusCallerRef(channelId, 42L, "corr-001");
        var parsed = QhorusCallerRef.parse(original.encode());
        assertThat(parsed.channelId()).isEqualTo(channelId);
        assertThat(parsed.messageId()).isEqualTo(42L);
        assertThat(parsed.correlationId()).isEqualTo("corr-001");
    }

    @Test
    void isQhorusReturnsTrueForQhorusPrefix() {
        assertThat(QhorusCallerRef.isQhorus("qhorus:abc/1/x")).isTrue();
    }

    @Test
    void isQhorusReturnsFalseForOtherPrefixes() {
        assertThat(QhorusCallerRef.isQhorus("case:abc/pi:def")).isFalse();
        assertThat(QhorusCallerRef.isQhorus(null)).isFalse();
        assertThat(QhorusCallerRef.isQhorus("")).isFalse();
    }

    @Test
    void parseMalformedThrows() {
        assertThatThrownBy(() -> QhorusCallerRef.parse("qhorus:missing-segments"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QhorusCallerRef.parse("case:not-qhorus/1/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
