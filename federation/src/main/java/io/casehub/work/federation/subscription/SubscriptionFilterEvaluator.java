package io.casehub.work.federation.subscription;

import io.casehub.work.api.WorkItem;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SubscriptionFilterEvaluator {

    private SubscriptionFilterEvaluator() {}

    public static boolean matches(SubscriptionFilter filter, WorkItem workItem) {
        if (!filter.tenancyId().equals(workItem.tenancyId())) {
            return false;
        }

        boolean groupMatch = !filter.candidateGroups().isEmpty()
                && intersects(filter.candidateGroups(), splitCsv(workItem.candidateGroups()));
        boolean userMatch = !filter.candidateUsers().isEmpty()
                && intersects(filter.candidateUsers(), splitCsv(workItem.candidateUsers()));

        if (filter.candidateGroups().isEmpty() && filter.candidateUsers().isEmpty()) {
            return true;
        }

        return groupMatch || userMatch;
    }

    private static boolean intersects(List<String> filterValues, Set<String> itemValues) {
        for (String value : filterValues) {
            if (itemValues.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
