package io.casehub.work.federation.subscription;

import java.util.List;

public record SubscriptionFilter(
        List<String> candidateGroups,
        List<String> candidateUsers,
        String tenancyId
) {
    public SubscriptionFilter {
        candidateGroups = candidateGroups != null ? List.copyOf(candidateGroups) : List.of();
        candidateUsers = candidateUsers != null ? List.copyOf(candidateUsers) : List.of();
    }
}
