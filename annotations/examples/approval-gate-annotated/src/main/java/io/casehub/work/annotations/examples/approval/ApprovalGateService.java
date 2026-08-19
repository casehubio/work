package io.casehub.work.annotations.examples.approval;

import io.casehub.work.annotations.HumanApproval;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApprovalGateService {

    @HumanApproval(
        title = "Approve expense report",
        candidateGroups = {"finance-team"},
        priority = WorkItemPriority.HIGH,
        claimDeadline = "PT1H",
        expiresAt = "PT24H")
    public String approve(String reportJson) {
        return null;
    }
}
