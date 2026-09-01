package io.casehub.work.runtime.preferences;

import io.casehub.work.runtime.service.BreachAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BreachActionPreferenceTest {

    @ParameterizedTest
    @CsvSource({
        "fail,          fail",
        "fail:custom,   fail:custom",
        "extend,        extend",
        "extend:PT6H,   extend:PT6H",
        "exhausted,     exhausted",
        "exhausted:why, exhausted:why",
        "escalateTo:group,          escalateTo:group",
        "escalateTo:group:PT4H,     escalateTo:group:PT4H"
    })
    void roundTrip(String input, String expected) {
        BreachActionPreference pref = BreachActionPreference.parse(input);
        assertThat(pref.toSerializedValue()).isEqualTo(expected);

        BreachActionPreference reparsed = BreachActionPreference.parse(pref.toSerializedValue());
        assertThat(reparsed.action()).isEqualTo(pref.action());
    }

    @Test
    void parseFail() {
        BreachActionPreference pref = BreachActionPreference.parse("fail");
        assertThat(pref.action()).isInstanceOf(BreachAction.FailAction.class);
        assertThat(((BreachAction.FailAction) pref.action()).reason()).isEqualTo("sla-breach");
    }

    @Test
    void parseEscalateToWithDeadline() {
        BreachActionPreference pref = BreachActionPreference.parse("escalateTo:team-leads:PT4H");
        assertThat(pref.action()).isInstanceOf(BreachAction.EscalateToAction.class);
        var esc = (BreachAction.EscalateToAction) pref.action();
        assertThat(esc.group()).isEqualTo("team-leads");
        assertThat(esc.deadline()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void invalidStringThrows() {
        assertThatThrownBy(() -> BreachActionPreference.parse("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chainedActionSerializationThrows() {
        var chained = new BreachAction.ChainedAction(
                new BreachAction.FailAction("a"),
                new BreachAction.FailAction("b"));
        var pref = new BreachActionPreference(chained);
        assertThatThrownBy(pref::toSerializedValue)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ChainedAction");
    }

    @Test
    void unsetHasNullAction() {
        assertThat(BreachActionPreference.UNSET.action()).isNull();
        assertThat(BreachActionPreference.UNSET.toSerializedValue()).isEmpty();
    }
}
