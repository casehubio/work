package io.casehub.work.annotations.examples.escalation;

import io.casehub.work.annotations.Escalate;
import io.casehub.work.annotations.HumanApproval;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EscalationGateService {

    @HumanApproval(
        title = "Review contract terms",
        candidateUsers = {"alice", "bob"},
        priority = WorkItemPriority.HIGH,
        description = "Review the proposed contract terms for compliance",
        claimDeadline = "PT30M",
        expiresAt = "PT4H")
    @Escalate(
        onExpiry = "legal-managers",
        onClaimDeadline = "legal-team-leads",
        deadline = "PT8H",
        generateSummary = true)
    public String reviewContract(String contractJson) {
        return null;
    }

    @HumanApproval(
        title = "Approve emergency change",
        candidateGroups = {"on-call-team"},
        priority = WorkItemPriority.URGENT)
    @Escalate(onExpiry = "incident-commanders")
    public String approveEmergencyChange(String changeRequestJson) {
        return null;
    }
}
