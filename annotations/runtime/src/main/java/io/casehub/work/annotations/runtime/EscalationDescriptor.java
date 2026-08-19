package io.casehub.work.annotations.runtime;

public record EscalationDescriptor(
    String onExpiry,
    String onClaimDeadline,
    String deadline,
    boolean generateSummary
) {}
