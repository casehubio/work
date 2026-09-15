package io.casehub.work.rest.service;

import java.util.List;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.view.AuditEntryView;
import io.casehub.work.api.view.WorkItemLabelView;
import io.casehub.work.api.view.WorkItemLinkView;
import io.casehub.work.api.view.WorkItemNoteView;
import io.casehub.work.api.view.WorkItemRelationView;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.api.view.WorkItemWithAuditView;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.model.WorkItemRelation;

final class ViewMapper {

    private ViewMapper() {}

    static WorkItemView toView(WorkItem wi) {
        return new WorkItemView(
                wi.id(), wi.title(), wi.description(),
                wi.types() == null ? List.of() : List.copyOf(wi.types()),
                wi.formKey(),
                wi.status(), wi.priority(), wi.assigneeId(), wi.owner(),
                wi.candidateGroups(), wi.candidateUsers(), wi.requiredCapabilities(),
                wi.createdBy(), wi.delegationDeclineTarget(), wi.delegationChain(),
                wi.priorStatus(), wi.payload(), wi.resolution(),
                wi.claimDeadline(), wi.expiresAt(), wi.followUpDate(),
                wi.createdAt(), wi.updatedAt(), wi.assignedAt(), wi.startedAt(),
                wi.completedAt(), wi.suspendedAt(),
                wi.labels() == null ? List.of()
                        : wi.labels().stream().map(l -> new WorkItemLabelView(l.path(), l.persistence(), l.appliedBy())).toList(),
                wi.confidenceScore(), wi.callerRef(), wi.version(),
                wi.templateId(), wi.templateVersion(), wi.outcome(),
                OutcomeCodecs.decodePermittedOutcomes(wi.permittedOutcomes()),
                wi.inputDataSchema(), wi.outputDataSchema(),
                wi.excludedUsers(), wi.scope(),
                wi.candidateScores(), wi.routingExperiences(),
                wi.compensationStatus(), wi.compensatesWorkItemId());
    }

    static WorkItemWithAuditView toWithAuditView(WorkItem wi, List<AuditEntry> trail) {
        List<AuditEntryView> auditViews = trail.stream()
                .map(e -> new AuditEntryView(e.id, e.event, e.actor, e.detail, e.occurredAt))
                .toList();
        return new WorkItemWithAuditView(
                wi.id(), wi.title(), wi.description(),
                wi.types() == null ? List.of() : List.copyOf(wi.types()),
                wi.formKey(),
                wi.status(), wi.priority(), wi.assigneeId(), wi.owner(),
                wi.candidateGroups(), wi.candidateUsers(), wi.requiredCapabilities(),
                wi.createdBy(), wi.delegationDeclineTarget(), wi.delegationChain(),
                wi.priorStatus(), wi.payload(), wi.resolution(),
                wi.claimDeadline(), wi.expiresAt(), wi.followUpDate(),
                wi.createdAt(), wi.updatedAt(), wi.assignedAt(), wi.startedAt(),
                wi.completedAt(), wi.suspendedAt(),
                wi.labels() == null ? List.of()
                        : wi.labels().stream().map(l -> new WorkItemLabelView(l.path(), l.persistence(), l.appliedBy())).toList(),
                auditViews,
                wi.confidenceScore(), wi.callerRef(), wi.version(),
                wi.templateId(), wi.templateVersion(), wi.outcome(),
                OutcomeCodecs.decodePermittedOutcomes(wi.permittedOutcomes()),
                wi.inputDataSchema(), wi.outputDataSchema(),
                wi.excludedUsers(), wi.scope(),
                wi.candidateScores(), wi.routingExperiences(),
                wi.compensationStatus(), wi.compensatesWorkItemId());
    }

    static WorkItemNoteView toNoteView(WorkItemNote note) {
        return new WorkItemNoteView(note.id, note.author, note.content, note.createdAt, note.editedAt);
    }

    static WorkItemLinkView toLinkView(WorkItemLink link) {
        return new WorkItemLinkView(link.id, link.url, link.title, link.relationType, link.linkedBy, link.createdAt);
    }

    static WorkItemRelationView toRelationView(WorkItemRelation rel) {
        return new WorkItemRelationView(rel.id, rel.sourceId, rel.targetId, rel.relationType, rel.createdBy, rel.createdAt);
    }
}
