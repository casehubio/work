package io.casehub.work.annotations.runtime;

public record CompositeWorkItemDescriptor(
    HumanApprovalDescriptor approval,
    QuorumDescriptor quorum,
    EscalationDescriptor escalation,
    SkillMatchDescriptor skillMatch,
    String methodName,
    String declaringClass,
    String returnTypeName
) {}
