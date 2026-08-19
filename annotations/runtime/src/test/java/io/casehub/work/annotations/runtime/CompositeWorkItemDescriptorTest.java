package io.casehub.work.annotations.runtime;

import io.casehub.work.api.OnThresholdReached;
import io.casehub.work.api.WorkItemPriority;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompositeWorkItemDescriptorTest {

    @Test
    void singleApprovalDescriptor() {
        var approval = new HumanApprovalDescriptor(
            "Review", List.of("team-a"), List.of(), WorkItemPriority.HIGH,
            "PT1H", "PT24H", "", "approve", "com.example.Svc", "com.example.Decision");
        var composite = new CompositeWorkItemDescriptor(
            approval, null, null, null, "approve", "com.example.Svc", "com.example.Decision");
        assertThat(composite.approval()).isNotNull();
        assertThat(composite.quorum()).isNull();
        assertThat(composite.escalation()).isNull();
        assertThat(composite.skillMatch()).isNull();
    }

    @Test
    void fullCompositeDescriptor() {
        var approval = new HumanApprovalDescriptor(
            "Review", List.of("team-a"), List.of(), WorkItemPriority.HIGH,
            "", "", "", "review", "com.example.Svc", "com.example.Outcome");
        var quorum = new QuorumDescriptor(3, 2, List.of(), OnThresholdReached.KEEP, false);
        var escalation = new EscalationDescriptor("managers", "", "PT4H", true);
        var skillMatch = new SkillMatchDescriptor("semantic", List.of("analysis"), 0.7);
        var composite = new CompositeWorkItemDescriptor(
            approval, quorum, escalation, skillMatch,
            "review", "com.example.Svc", "com.example.Outcome");
        assertThat(composite.approval()).isNotNull();
        assertThat(composite.quorum().instances()).isEqualTo(3);
        assertThat(composite.escalation().onExpiry()).isEqualTo("managers");
        assertThat(composite.skillMatch().minimumScore()).isEqualTo(0.7);
    }
}
