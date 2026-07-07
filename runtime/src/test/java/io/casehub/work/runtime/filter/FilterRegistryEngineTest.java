package io.casehub.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkEventType;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;

class FilterRegistryEngineTest {

    private FilterRegistryEngine engine;
    private List<WorkItem> capturedWorkItems;
    private FilterAction capturingAction;

    @BeforeEach
    void setUp() {
        capturedWorkItems = new ArrayList<>();
        capturingAction = new FilterAction() {
            @Override
            public String type() {
                return "CAPTURE";
            }

            @Override
            public void apply(final WorkItem workItem, final Map<String, Object> params) {
                capturedWorkItems.add(workItem);
            }
        };
        engine = new FilterRegistryEngine(new JexlConditionEvaluator(), List.of(capturingAction));
    }

    private WorkItemLifecycleEvent event(final WorkEventType type, final String title) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = title;
        wi.status = io.casehub.work.api.WorkItemStatus.PENDING;
        return WorkItemLifecycleEvent.of(type.name(), wi, "test", null);
    }

    @Test
    void matchingCondition_actionFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.title == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkItems).hasSize(1);
        assertThat(capturedWorkItems.get(0).title).isEqualTo("finance");
    }

    @Test
    void nonMatchingCondition_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.title == 'legal'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkItems).isEmpty();
    }

    @Test
    void disabledDefinition_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", false,
                "workItem.title == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkItems).isEmpty();
    }

    @Test
    void wrongEventType_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.title == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.ASSIGNED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkItems).isEmpty();
    }

    @Test
    void unknownActionType_ignoredGracefully() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.title == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("UNKNOWN_ACTION", Map.of())));
        final var evt = event(WorkEventType.CREATED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkItems).isEmpty();
    }

    @Test
    void reentrancyGuard_preventsRecursiveProcessing() {
        final int[] callCount = { 0 };
        final FilterAction selfFiringAction = new FilterAction() {
            @Override
            public String type() {
                return "SELF_FIRE";
            }

            @Override
            public void apply(final WorkItem workItem, final Map<String, Object> params) {
                callCount[0]++;
            }
        };
        engine = new FilterRegistryEngine(new JexlConditionEvaluator(), List.of(selfFiringAction));
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.title == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("SELF_FIRE", Map.of())));
        final var evt = event(WorkEventType.CREATED, "finance");
        engine.processEvent(evt, List.of(def));
        assertThat(callCount[0]).isEqualTo(1);
    }
}
