package io.casehub.work.runtime.filter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.PathAttributeConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "label_rule")
public class LabelRuleEntity extends PanacheEntityBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    public UUID id;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(nullable = false, length = 255)
    public String name;

    @Column(length = 500)
    public String description;

    @Column(name = "condition_language", nullable = false, length = 20)
    public String conditionLanguage;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    public String conditionExpression;

    @Column(name = "actions_json", nullable = false, columnDefinition = "TEXT")
    public String actionsJson = "[]";

    @Column(name = "trigger_events", length = 100)
    public String triggerEvents = "";

    @Convert(converter = PathAttributeConverter.class)
    @Column(length = 500)
    public Path scope;

    public boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public List<LabelAction> parseActions() {
        if (actionsJson == null || actionsJson.isBlank()) {
            return List.of();
        }
        try {
            List<ActionJson> raw = MAPPER.readValue(actionsJson, new TypeReference<>() {});
            List<LabelAction> result = new ArrayList<>();
            for (ActionJson a : raw) {
                if ("Add".equals(a.type)) {
                    result.add(new LabelAction.Add(a.label));
                } else if ("Remove".equals(a.type)) {
                    result.add(new LabelAction.Remove(a.label));
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static String serializeActions(List<LabelAction> actions) {
        try {
            List<ActionJson> raw = actions.stream()
                    .map(a -> new ActionJson(
                            a instanceof LabelAction.Add ? "Add" : "Remove",
                            a.label()))
                    .toList();
            return MAPPER.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    public LabelRule toLabelRule(ExpressionEngineRegistry registry) {
        CompiledExpression<Map<String, Object>, Boolean> condition =
                registry.compile(conditionLanguage, conditionExpression,
                        (Class<Map<String, Object>>) (Class<?>) Map.class,
                        Boolean.class);
        Set<String> events = (triggerEvents == null || triggerEvents.isBlank())
                ? Set.of()
                : Set.of(triggerEvents.split(","));
        return new LabelRule(name, condition, parseActions(), events);
    }

    record ActionJson(String type, String label) {}
}
