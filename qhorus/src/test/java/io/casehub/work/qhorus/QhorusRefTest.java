package io.casehub.work.qhorus;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QhorusRefTest {

    @Test
    void encodeProducesCorrectFormat() {
        var channelId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        var ref = new QhorusRef(channelId, 42L, "corr-001");
        assertThat(ref.encode()).isEqualTo("qhorus:a1b2c3d4-e5f6-7890-abcd-ef1234567890/42/corr-001");
    }

    @Test
    void parseRoundTrips() {
        var channelId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        var original = new QhorusRef(channelId, 42L, "corr-001");
        var parsed = QhorusRef.parse(original.encode());
        assertThat(parsed.channelId()).isEqualTo(channelId);
        assertThat(parsed.messageId()).isEqualTo(42L);
        assertThat(parsed.correlationId()).isEqualTo("corr-001");
    }

    @Test
    void isQhorusReturnsTrueForQhorusPrefix() {
        assertThat(QhorusRef.isQhorus("qhorus:abc/1/x")).isTrue();
    }

    @Test
    void isQhorusReturnsFalseForOtherPrefixes() {
        assertThat(QhorusRef.isQhorus("case:abc/pi:def")).isFalse();
        assertThat(QhorusRef.isQhorus(null)).isFalse();
        assertThat(QhorusRef.isQhorus("")).isFalse();
    }

    @Test
    void parseMalformedThrows() {
        assertThatThrownBy(() -> QhorusRef.parse("qhorus:missing-segments"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QhorusRef.parse("case:not-qhorus/1/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void qhorusRef_implementsCrossSystemRef() {
        var ref = new QhorusRef(java.util.UUID.randomUUID(), 1L, "c1");
        org.assertj.core.api.Assertions.assertThat(ref.system()).isEqualTo("qhorus");
        org.assertj.core.api.Assertions.assertThat(ref).isInstanceOf(io.casehub.work.api.CrossSystemRef.class);
        org.assertj.core.api.Assertions.assertThat(ref.encode()).startsWith("qhorus:");
    }
}
