package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.LabelPersistence;

class WorkItemLabelEntityTest {

    @Test
    void labelPersistence_hasTwoValues() {
        assertThat(LabelPersistence.values())
                .containsExactlyInAnyOrder(LabelPersistence.MANUAL, LabelPersistence.INFERRED);
    }

    @Test
    void workItemLabel_Entity_manualConstructor_setsAllFields() {
        var label = new WorkItemLabelEntity("legal/contracts", LabelPersistence.MANUAL, "alice");
        assertThat(label.path).isEqualTo("legal/contracts");
        assertThat(label.persistence).isEqualTo(LabelPersistence.MANUAL);
        assertThat(label.appliedBy).isEqualTo("alice");
    }

    @Test
    void workItemLabel_Entity_inferredConstructor_nullAppliedBy() {
        var label = new WorkItemLabelEntity("intake", LabelPersistence.INFERRED, null);
        assertThat(label.path).isEqualTo("intake");
        assertThat(label.persistence).isEqualTo(LabelPersistence.INFERRED);
        assertThat(label.appliedBy).isNull();
    }

    @Test
    void workItemLabel_Entity_singleSegmentPath_isValid() {
        var label = new WorkItemLabelEntity("legal", LabelPersistence.MANUAL, "bob");
        assertThat(label.path).isEqualTo("legal");
    }

    @Test
    void workItemLabel_Entity_equalsAndHashCode_sameFieldsAreEqual() {
        var a = new WorkItemLabelEntity("legal/contracts", LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabelEntity("legal/contracts", LabelPersistence.MANUAL, "alice");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void workItemLabel_Entity_equalsAndHashCode_differentPathNotEqual() {
        var a = new WorkItemLabelEntity("legal/contracts", LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabelEntity("legal/ip", LabelPersistence.MANUAL, "alice");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void workItemLabel_Entity_equalsAndHashCode_differentPersistenceNotEqual() {
        var a = new WorkItemLabelEntity("legal", LabelPersistence.MANUAL, "alice");
        var b = new WorkItemLabelEntity("legal", LabelPersistence.INFERRED, null);
        assertThat(a).isNotEqualTo(b);
    }
}
