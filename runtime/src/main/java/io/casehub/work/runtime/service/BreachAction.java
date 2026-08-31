package io.casehub.work.runtime.service;

import java.time.Duration;
import java.util.Map;

import io.casehub.work.api.BreachDecision;

public sealed interface BreachAction {

    record FailAction(String reason) implements BreachAction {}
    record ExtendAction(Duration explicitDuration) implements BreachAction {}
    record EscalateToAction(String group, Duration deadline) implements BreachAction {}
    record ExhaustedAction(String reason) implements BreachAction {}
    record ChainedAction(BreachAction primary, BreachAction fallback) implements BreachAction {}

    @SuppressWarnings("unchecked")
    static BreachAction parse(Object yamlValue) {
        if (yamlValue instanceof String s) {
            return switch (s) {
                case "fail" -> new FailAction("sla-breach");
                case "extend" -> new ExtendAction(null);
                case "exhausted" -> new ExhaustedAction("sla-exhausted");
                default -> throw new IllegalArgumentException(
                        "Unknown SLA action: '" + s + "' — expected fail, extend, or exhausted");
            };
        }
        if (yamlValue instanceof Map<?, ?> map) {
            return parseMap((Map<String, Object>) map);
        }
        if (yamlValue instanceof java.util.List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("SLA action list must not be empty");
            }
            if (list.size() == 1) {
                return parse(list.get(0));
            }
            BreachAction result = parse(list.get(list.size() - 1));
            for (int i = list.size() - 2; i >= 0; i--) {
                result = new ChainedAction(parse(list.get(i)), result);
            }
            return result;
        }
        throw new IllegalArgumentException(
                "SLA action must be a string, object, or list, got: " + yamlValue.getClass().getSimpleName());
    }

    private static BreachAction parseMap(Map<String, Object> map) {
        if (map.containsKey("fail")) {
            return new FailAction(String.valueOf(map.get("fail")));
        }
        if (map.containsKey("extend")) {
            Duration d = Duration.parse(String.valueOf(map.get("extend")));
            if (d.isZero() || d.isNegative()) {
                throw new IllegalArgumentException("extend duration must be positive, was: " + d);
            }
            return new ExtendAction(d);
        }
        if (map.containsKey("escalateTo")) {
            String group = String.valueOf(map.get("escalateTo"));
            Duration deadline = null;
            if (map.containsKey("deadline")) {
                deadline = Duration.parse(String.valueOf(map.get("deadline")));
                if (deadline.isZero() || deadline.isNegative()) {
                    throw new IllegalArgumentException("escalateTo deadline must be positive, was: " + deadline);
                }
            }
            return new EscalateToAction(group, deadline);
        }
        if (map.containsKey("exhausted")) {
            return new ExhaustedAction(String.valueOf(map.get("exhausted")));
        }
        throw new IllegalArgumentException(
                "SLA action object must contain one of: fail, extend, escalateTo, exhausted — got keys: " + map.keySet());
    }

    static BreachAction parseColon(String configValue) {
        if (configValue == null || configValue.isBlank()) {
            throw new IllegalArgumentException("SLA config property value must not be blank");
        }
        String[] parts = configValue.split(":", 3);
        return switch (parts[0]) {
            case "fail" -> new FailAction(parts.length > 1 ? parts[1] : "sla-breach");
            case "extend" -> {
                if (parts.length > 1) {
                    Duration d = Duration.parse(parts[1]);
                    if (d.isZero() || d.isNegative()) {
                        throw new IllegalArgumentException("extend duration must be positive, was: " + d);
                    }
                    yield new ExtendAction(d);
                }
                yield new ExtendAction(null);
            }
            case "escalateTo" -> {
                if (parts.length < 2) {
                    throw new IllegalArgumentException("escalateTo requires a group: escalateTo:<group>[:<deadline>]");
                }
                Duration deadline = null;
                if (parts.length > 2) {
                    deadline = Duration.parse(parts[2]);
                    if (deadline.isZero() || deadline.isNegative()) {
                        throw new IllegalArgumentException("escalateTo deadline must be positive, was: " + deadline);
                    }
                }
                yield new EscalateToAction(parts[1], deadline);
            }
            case "exhausted" -> new ExhaustedAction(parts.length > 1 ? parts[1] : "sla-exhausted");
            default -> throw new IllegalArgumentException(
                    "Unknown SLA action: '" + parts[0] + "' — expected fail, extend, escalateTo, or exhausted");
        };
    }

    default BreachDecision toBreachDecision(Integer fallbackExtensionHours, int defaultExpiryHours) {
        return switch (this) {
            case FailAction f -> new BreachDecision.Fail(f.reason());
            case ExtendAction e -> {
                Duration d = e.explicitDuration();
                if (d == null) {
                    int hours = fallbackExtensionHours != null ? fallbackExtensionHours : defaultExpiryHours;
                    d = Duration.ofHours(hours);
                }
                yield new BreachDecision.Extend(d);
            }
            case EscalateToAction e -> {
                var decision = BreachDecision.EscalateTo.to(e.group());
                if (e.deadline() != null) {
                    yield decision.withDeadline(e.deadline());
                }
                int hours = fallbackExtensionHours != null ? fallbackExtensionHours : defaultExpiryHours;
                yield decision.withDeadline(Duration.ofHours(hours));
            }
            case ExhaustedAction e -> new BreachDecision.Exhausted(e.reason());
            case ChainedAction c -> new BreachDecision.Chained(
                    c.primary().toBreachDecision(fallbackExtensionHours, defaultExpiryHours),
                    c.fallback().toBreachDecision(fallbackExtensionHours, defaultExpiryHours));
        };
    }
}
