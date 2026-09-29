package io.casehub.work.api.view;

import java.util.List;

public record SlaBreachReportView(List<SlaBreachItemView> items, SlaSummaryView summary) {
}
