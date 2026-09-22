package io.casehub.work.api.view;

import java.util.Map;

public record ActorReportView(String actorId, long totalAssigned, long totalCompleted,
                               long totalRejected, Double avgCompletionMinutes,
                               Map<String, Long> byType) {
}
