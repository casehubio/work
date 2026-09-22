package io.casehub.work.rest.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.spi.WorkItemAuditApi;
import io.casehub.work.api.view.AuditEntryView;
import io.casehub.work.api.view.AuditQueryResult;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;

@ApplicationScoped
public class DefaultWorkItemAuditApi implements WorkItemAuditApi {

    @Inject
    AuditEntryStore auditStore;

    @Override
    public AuditQueryResult query(String actorId, String from, String to, String event, String type,
                                  Integer pageIndex, Integer pageSize, String tenancyId) {
        final AuditQuery q = AuditQuery.builder()
                .actorId(actorId)
                .from(from != null ? Instant.parse(from) : null)
                .to(to != null ? Instant.parse(to) : null)
                .event(event)
                .type(type)
                .page(pageIndex != null ? pageIndex : 0)
                .size(pageSize != null ? pageSize : 20)
                .build();

        final List<AuditEntry> entries = auditStore.query(q);
        final long total = auditStore.count(q);

        return new AuditQueryResult(
                entries.stream()
                        .map(e -> new AuditEntryView(e.id, e.event, e.actor, e.detail, e.occurredAt))
                        .toList(),
                q.page(), q.size(), total);
    }
}
