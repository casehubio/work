package io.casehub.work.annotations.examples.queue;

import io.casehub.work.annotations.HumanApproval;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExpenseApprovalService {

    @HumanApproval(
        title = "Approve expense report",
        candidateGroups = {"finance-team"},
        priority = WorkItemPriority.MEDIUM,
        types = {"finance", "expense"},
        labels = {"finance/approval"})
    public String approveExpense(String reportJson) {
        return null;
    }

    @HumanApproval(
        title = "Approve urgent expense report",
        candidateGroups = {"finance-team"},
        priority = WorkItemPriority.URGENT,
        types = {"finance", "expense"},
        labels = {"finance/approval"})
    public String approveUrgentExpense(String reportJson) {
        return null;
    }
}
