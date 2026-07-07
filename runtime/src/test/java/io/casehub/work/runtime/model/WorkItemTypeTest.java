package io.casehub.work.runtime.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkItemTypeTest {

    @Test
    void equals_samePath() {
        assertThat(new WorkItemType("approval")).isEqualTo(new WorkItemType("approval"));
    }

    @Test
    void equals_differentPath() {
        assertThat(new WorkItemType("approval")).isNotEqualTo(new WorkItemType("review"));
    }

    @Test
    void hashCode_samePath() {
        assertThat(new WorkItemType("approval").hashCode())
                .isEqualTo(new WorkItemType("approval").hashCode());
    }

    @Test
    void noArgConstructor() {
        WorkItemType type = new WorkItemType();
        assertThat(type.path).isNull();
    }
}
