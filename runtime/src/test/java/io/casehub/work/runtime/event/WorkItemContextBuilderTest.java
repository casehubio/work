package io.casehub.work.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.casehub.work.api.WorkItem;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;

class WorkItemContextBuilderTest {

    @Test
    void toMap_containsId() {
        final UUID id = UUID.randomUUID();
        final WorkItem wi = WorkItem.builder()
                .id(id)
                .title("Test")
                .status(WorkItemStatus.PENDING)
                .priority(WorkItemPriority.HIGH)
                .build();
        final Map<String, Object> map = WorkItemContextBuilder.toMap(wi);
        assertThat(map).containsKey("id");
        assertThat(map.get("id")).isEqualTo(id);
    }

    @Test
    void toMap_containsAllRecordComponents() {
        final var expected = java.util.Arrays.stream(WorkItem.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        final WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .build();
        final Map<String, Object> map = WorkItemContextBuilder.toMap(wi);
        assertThat(map.keySet()).containsAll(expected);
    }

    @Test
    void toMap_containsOutcomeValue() {
        final WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .outcome("approved")
                .build();
        final Map<String, Object> map = WorkItemContextBuilder.toMap(wi);
        assertThat(map.get("outcome")).isEqualTo("approved");
    }

    @Test
    void toMap_permittedOutcomes_decodedToList_notRawJson() {
        // Verifies that permittedOutcomes is a List<String> for JEXL collection semantics,
        // not the raw JSON string (which would break .contains() in filter expressions).
        final WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .permittedOutcomes("[\"approved\",\"rejected\"]")
                .build();
        final Map<String, Object> map = WorkItemContextBuilder.toMap(wi);
        assertThat(map.get("permittedOutcomes")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        final List<String> names = (List<String>) map.get("permittedOutcomes");
        assertThat(names).containsExactly("approved", "rejected");
    }

    @Test
    void toMap_preservesEnumConstants() {
        final WorkItem wi = WorkItem.builder()
                .id(UUID.randomUUID())
                .status(WorkItemStatus.IN_PROGRESS)
                .priority(WorkItemPriority.URGENT)
                .build();
        final Map<String, Object> map = WorkItemContextBuilder.toMap(wi);
        assertThat(map.get("status")).isEqualTo(WorkItemStatus.IN_PROGRESS);
        assertThat(map.get("priority")).isEqualTo(WorkItemPriority.URGENT);
    }
}
