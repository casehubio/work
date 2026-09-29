package io.casehub.work.api.view;

import java.util.List;

public record AuditQueryResult(List<AuditEntryView> entries, int page, int size, long totalCount) {
}
