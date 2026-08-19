package io.casehub.work.annotations;

import io.casehub.work.api.WorkItemPriority;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

class HumanApprovalTest {

    @HumanApproval(title = "Test approval", candidateGroups = {"team-a", "team-b"},
                    priority = WorkItemPriority.HIGH, claimDeadline = "PT1H")
    void annotatedMethod() {}

    @Test
    void annotationAttributesAccessible() throws Exception {
        Method method = getClass().getDeclaredMethod("annotatedMethod");
        HumanApproval ann = method.getAnnotation(HumanApproval.class);
        assertThat(ann.title()).isEqualTo("Test approval");
        assertThat(ann.candidateGroups()).containsExactly("team-a", "team-b");
        assertThat(ann.priority()).isEqualTo(WorkItemPriority.HIGH);
        assertThat(ann.claimDeadline()).isEqualTo("PT1H");
        assertThat(ann.expiresAt()).isEmpty();
        assertThat(ann.candidateUsers()).isEmpty();
        assertThat(ann.description()).isEmpty();
    }
}
