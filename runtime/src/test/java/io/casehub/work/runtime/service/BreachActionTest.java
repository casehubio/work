package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.BreachDecision;

class BreachActionTest {

    // ── String shorthand parsing ──

    @Test
    void parseStringFail() {
        BreachAction action = BreachAction.parse("fail");
        assertThat(action).isInstanceOf(BreachAction.FailAction.class);
        assertThat(((BreachAction.FailAction) action).reason()).isEqualTo("sla-breach");
    }

    @Test
    void parseStringExtend() {
        BreachAction action = BreachAction.parse("extend");
        assertThat(action).isInstanceOf(BreachAction.ExtendAction.class);
        assertThat(((BreachAction.ExtendAction) action).explicitDuration()).isNull();
    }

    @Test
    void parseStringExhausted() {
        BreachAction action = BreachAction.parse("exhausted");
        assertThat(action).isInstanceOf(BreachAction.ExhaustedAction.class);
        assertThat(((BreachAction.ExhaustedAction) action).reason()).isEqualTo("sla-exhausted");
    }

    @Test
    void parseUnknownStringThrows() {
        assertThatThrownBy(() -> BreachAction.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Map parsing ──

    @Test
    void parseMapFail() {
        BreachAction action = BreachAction.parse(Map.of("fail", "custom-reason"));
        assertThat(action).isEqualTo(new BreachAction.FailAction("custom-reason"));
    }

    @Test
    void parseMapExtend() {
        BreachAction action = BreachAction.parse(Map.of("extend", "PT6H"));
        assertThat(action).isEqualTo(new BreachAction.ExtendAction(Duration.ofHours(6)));
    }

    @Test
    void parseMapEscalateTo() {
        BreachAction action = BreachAction.parse(Map.of("escalateTo", "team-leads", "deadline", "PT4H"));
        assertThat(action).isEqualTo(new BreachAction.EscalateToAction("team-leads", Duration.ofHours(4)));
    }

    @Test
    void parseMapEscalateToNoDeadline() {
        BreachAction action = BreachAction.parse(Map.of("escalateTo", "team-leads"));
        assertThat(action).isEqualTo(new BreachAction.EscalateToAction("team-leads", null));
    }

    @Test
    void parseMapExhausted() {
        BreachAction action = BreachAction.parse(Map.of("exhausted", "triage-sla-exceeded"));
        assertThat(action).isEqualTo(new BreachAction.ExhaustedAction("triage-sla-exceeded"));
    }

    @Test
    void parseMapUnknownKeyThrows() {
        assertThatThrownBy(() -> BreachAction.parse(Map.of("unknown", "value")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Duration validation ──

    @Test
    void parseMapExtendZeroDurationThrows() {
        assertThatThrownBy(() -> BreachAction.parse(Map.of("extend", "PT0S")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extend duration must be positive");
    }

    @Test
    void parseMapExtendNegativeDurationThrows() {
        assertThatThrownBy(() -> BreachAction.parse(Map.of("extend", "-PT1H")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extend duration must be positive");
    }

    @Test
    void parseMapEscalateToZeroDeadlineThrows() {
        assertThatThrownBy(() -> BreachAction.parse(Map.of("escalateTo", "group", "deadline", "PT0S")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escalateTo deadline must be positive");
    }

    @Test
    void parseMapEscalateToNegativeDeadlineThrows() {
        assertThatThrownBy(() -> BreachAction.parse(Map.of("escalateTo", "group", "deadline", "-PT1H")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escalateTo deadline must be positive");
    }

    // ── Colon-delimited parsing ──

    @Test
    void parseColonFail() {
        assertThat(BreachAction.parseColon("fail"))
                .isEqualTo(new BreachAction.FailAction("sla-breach"));
    }

    @Test
    void parseColonFailWithReason() {
        assertThat(BreachAction.parseColon("fail:custom-reason"))
                .isEqualTo(new BreachAction.FailAction("custom-reason"));
    }

    @Test
    void parseColonExtend() {
        assertThat(BreachAction.parseColon("extend"))
                .isEqualTo(new BreachAction.ExtendAction(null));
    }

    @Test
    void parseColonExtendWithDuration() {
        assertThat(BreachAction.parseColon("extend:PT6H"))
                .isEqualTo(new BreachAction.ExtendAction(Duration.ofHours(6)));
    }

    @Test
    void parseColonEscalateTo() {
        assertThat(BreachAction.parseColon("escalateTo:team-leads"))
                .isEqualTo(new BreachAction.EscalateToAction("team-leads", null));
    }

    @Test
    void parseColonEscalateToWithDeadline() {
        assertThat(BreachAction.parseColon("escalateTo:team-leads:PT4H"))
                .isEqualTo(new BreachAction.EscalateToAction("team-leads", Duration.ofHours(4)));
    }

    @Test
    void parseColonEscalateToZeroDeadlineThrows() {
        assertThatThrownBy(() -> BreachAction.parseColon("escalateTo:group:PT0S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escalateTo deadline must be positive");
    }

    @Test
    void parseColonExhausted() {
        assertThat(BreachAction.parseColon("exhausted"))
                .isEqualTo(new BreachAction.ExhaustedAction("sla-exhausted"));
    }

    @Test
    void parseColonExhaustedWithReason() {
        assertThat(BreachAction.parseColon("exhausted:custom-reason"))
                .isEqualTo(new BreachAction.ExhaustedAction("custom-reason"));
    }

    @Test
    void parseColonMissingGroupThrows() {
        assertThatThrownBy(() -> BreachAction.parseColon("escalateTo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a group");
    }

    @Test
    void parseColonUnknownThrows() {
        assertThatThrownBy(() -> BreachAction.parseColon("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── toBreachDecision ──

    @Test
    void failToBreachDecision() {
        BreachDecision d = new BreachAction.FailAction("reason").toBreachDecision(null, 24);
        assertThat(d).isEqualTo(new BreachDecision.Fail("reason"));
    }

    @Test
    void extendWithExplicitDurationToBreachDecision() {
        BreachDecision d = new BreachAction.ExtendAction(Duration.ofHours(6)).toBreachDecision(null, 24);
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(6)));
    }

    @Test
    void extendWithFallbackHoursToBreachDecision() {
        BreachDecision d = new BreachAction.ExtendAction(null).toBreachDecision(8, 24);
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(8)));
    }

    @Test
    void extendWithDefaultExpiryHoursToBreachDecision() {
        BreachDecision d = new BreachAction.ExtendAction(null).toBreachDecision(null, 24);
        assertThat(d).isEqualTo(new BreachDecision.Extend(Duration.ofHours(24)));
    }

    @Test
    void escalateToWithDeadlineToBreachDecision() {
        BreachDecision d = new BreachAction.EscalateToAction("team-leads", Duration.ofHours(4))
                .toBreachDecision(null, 24);
        assertThat(d).isEqualTo(BreachDecision.EscalateTo.to("team-leads").withDeadline(Duration.ofHours(4)));
    }

    @Test
    void escalateToWithFallbackHoursDeadlineToBreachDecision() {
        BreachDecision d = new BreachAction.EscalateToAction("team-leads", null)
                .toBreachDecision(8, 24);
        assertThat(d).isEqualTo(BreachDecision.EscalateTo.to("team-leads").withDeadline(Duration.ofHours(8)));
    }

    @Test
    void escalateToWithDefaultExpiryHoursDeadlineToBreachDecision() {
        BreachDecision d = new BreachAction.EscalateToAction("team-leads", null)
                .toBreachDecision(null, 24);
        assertThat(d).isEqualTo(BreachDecision.EscalateTo.to("team-leads").withDeadline(Duration.ofHours(24)));
    }

    @Test
    void exhaustedToBreachDecision() {
        BreachDecision d = new BreachAction.ExhaustedAction("reason").toBreachDecision(null, 24);
        assertThat(d).isEqualTo(new BreachDecision.Exhausted("reason"));
    }
}
