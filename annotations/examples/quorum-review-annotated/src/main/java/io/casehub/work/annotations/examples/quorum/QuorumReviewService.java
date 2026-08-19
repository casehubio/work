package io.casehub.work.annotations.examples.quorum;

import io.casehub.work.annotations.Escalate;
import io.casehub.work.annotations.HumanApproval;
import io.casehub.work.annotations.RequiresQuorum;
import io.casehub.work.annotations.SkillMatch;
import io.casehub.work.api.OnThresholdReached;
import io.casehub.work.api.WorkItemPriority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuorumReviewService {

    @HumanApproval(
        title = "Compliance review",
        candidateGroups = {"compliance-team"},
        priority = WorkItemPriority.HIGH)
    @RequiresQuorum(
        instances = 3,
        required = 2,
        onThresholdReached = OnThresholdReached.CANCEL)
    @Escalate(
        onExpiry = "compliance-managers",
        deadline = "PT8H",
        generateSummary = true)
    @SkillMatch(
        strategy = "semantic",
        requiredCapabilities = {"regulatory-analysis"},
        minimumScore = 0.7)
    public String review(String documentJson) {
        return null;
    }
}
