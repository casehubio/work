package io.casehub.work.runtime.preferences;

import io.casehub.platform.api.preferences.SingleValuePreference;
import io.casehub.work.runtime.service.BreachAction;

public record BreachActionPreference(BreachAction action) implements SingleValuePreference {

    public static final BreachActionPreference UNSET = new BreachActionPreference(null);

    public static BreachActionPreference parse(String raw) {
        return new BreachActionPreference(BreachAction.parseColon(raw));
    }

    @Override
    public String toSerializedValue() {
        if (action == null) return "";
        return toColonString(action);
    }

    private static String toColonString(BreachAction action) {
        return switch (action) {
            case BreachAction.FailAction f ->
                "sla-breach".equals(f.reason()) ? "fail" : "fail:" + f.reason();
            case BreachAction.ExtendAction e ->
                e.explicitDuration() == null ? "extend" : "extend:" + e.explicitDuration();
            case BreachAction.EscalateToAction e ->
                e.deadline() == null
                    ? "escalateTo:" + e.group()
                    : "escalateTo:" + e.group() + ":" + e.deadline();
            case BreachAction.ExhaustedAction e ->
                "sla-exhausted".equals(e.reason()) ? "exhausted" : "exhausted:" + e.reason();
            case BreachAction.ChainedAction c ->
                throw new UnsupportedOperationException(
                    "ChainedAction cannot be serialized as a preference — use CallbackSlaBreachPolicyDecorator for complex fallback chains");
        };
    }
}
