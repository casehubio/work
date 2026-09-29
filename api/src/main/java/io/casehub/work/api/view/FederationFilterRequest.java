package io.casehub.work.api.view;

import java.util.List;

public record FederationFilterRequest(List<String> candidateGroups, List<String> candidateUsers) {
}
