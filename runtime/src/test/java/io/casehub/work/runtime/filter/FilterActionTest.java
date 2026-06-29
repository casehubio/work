package io.casehub.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;

class FilterActionTest {

    @Test
    void apply_receivesWorkItem() {
        final WorkItem[] captured = new WorkItem[1];
        final FilterAction action = new FilterAction() {
            @Override
            public String type() {
                return "TEST";
            }

            @Override
            public void apply(final WorkItem workItem, final Map<String, Object> params) {
                captured[0] = workItem;
            }
        };
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        action.apply(wi, Map.of());
        assertThat(captured[0]).isSameAs(wi);
    }

    @Test
    void type_returnsActionIdentifier() {
        final FilterAction action = new FilterAction() {
            @Override
            public String type() {
                return "MY_ACTION";
            }

            @Override
            public void apply(WorkItem workItem, Map<String, Object> params) {
            }
        };
        assertThat(action.type()).isEqualTo("MY_ACTION");
    }
}
