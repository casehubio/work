package io.casehub.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.label.LabelAction;

class LabelRuleEntityTest {

    @Test
    void serializeActions_addAndRemove() {
        var actions = List.<LabelAction>of(
                new LabelAction.Add("queue/urgent"),
                new LabelAction.Remove("queue/normal"));
        String json = LabelRuleEntity.serializeActions(actions);
        assertThat(json).contains("\"type\":\"Add\"", "\"label\":\"queue/urgent\"");
        assertThat(json).contains("\"type\":\"Remove\"", "\"label\":\"queue/normal\"");
    }

    @Test
    void parseActions_roundTrip() {
        var original = List.<LabelAction>of(
                new LabelAction.Add("a"),
                new LabelAction.Remove("b"));
        String json = LabelRuleEntity.serializeActions(original);
        var entity = new LabelRuleEntity();
        entity.actionsJson = json;
        List<LabelAction> parsed = entity.parseActions();
        assertThat(parsed).containsExactly(
                new LabelAction.Add("a"),
                new LabelAction.Remove("b"));
    }

    @Test
    void parseActions_emptyJson_returnsEmptyList() {
        var entity = new LabelRuleEntity();
        entity.actionsJson = "[]";
        assertThat(entity.parseActions()).isEmpty();
    }

    @Test
    void parseActions_nullJson_returnsEmptyList() {
        var entity = new LabelRuleEntity();
        entity.actionsJson = null;
        assertThat(entity.parseActions()).isEmpty();
    }

    @Test
    void parseActions_malformedJson_returnsEmptyList() {
        var entity = new LabelRuleEntity();
        entity.actionsJson = "not json";
        assertThat(entity.parseActions()).isEmpty();
    }

    @Test
    void serializeActions_emptyList() {
        assertThat(LabelRuleEntity.serializeActions(List.of())).isEqualTo("[]");
    }
}
