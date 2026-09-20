package io.casehub.work.mongodb;

import com.mongodb.client.MongoDatabase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.spi.CrossTenantWorkItemStore;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.mongodb.core.MongoAuditEntryStoreCore;
import io.casehub.work.mongodb.core.MongoCrossTenantRoutingCursorStoreCore;
import io.casehub.work.mongodb.core.MongoCrossTenantWorkItemScheduleStoreCore;
import io.casehub.work.mongodb.core.MongoCrossTenantWorkItemStoreCore;
import io.casehub.work.mongodb.core.MongoIssueLinkStoreCore;
import io.casehub.work.mongodb.core.MongoLabelDefinitionStoreCore;
import io.casehub.work.mongodb.core.MongoLabelRuleStoreCore;
import io.casehub.work.mongodb.core.MongoLabelVocabularyStoreCore;
import io.casehub.work.mongodb.core.MongoRoutingCursorStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemLinkStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemNoteStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemRelationStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemScheduleStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemSpawnGroupStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemStoreCore;
import io.casehub.work.mongodb.core.MongoWorkItemTemplateStoreCore;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import io.casehub.work.runtime.repository.LabelRuleStore;
import io.casehub.work.runtime.repository.LabelVocabularyStore;
import io.casehub.work.runtime.repository.WorkItemLinkStore;
import io.casehub.work.runtime.repository.WorkItemNoteStore;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PersistenceMongoProducers {

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemStore workItemStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public CrossTenantWorkItemStore crossTenantWorkItemStore(MongoDatabase db) {
        return new MongoCrossTenantWorkItemStoreCore(db);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public AuditEntryStore auditEntryStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoAuditEntryStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemScheduleStore scheduleStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemScheduleStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public CrossTenantWorkItemScheduleStore crossTenantScheduleStore(MongoDatabase db) {
        return new MongoCrossTenantWorkItemScheduleStoreCore(db);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemTemplateStore templateStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemTemplateStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public RoutingCursorStore routingCursorStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoRoutingCursorStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public CrossTenantRoutingCursorStore crossTenantRoutingCursorStore(MongoDatabase db) {
        return new MongoCrossTenantRoutingCursorStoreCore(db);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemNoteStore noteStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemNoteStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemRelationStore relationStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemRelationStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemLinkStore linkStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemLinkStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemSpawnGroupStore spawnGroupStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoWorkItemSpawnGroupStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public IssueLinkStore issueLinkStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoIssueLinkStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public LabelDefinitionStore labelDefinitionStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoLabelDefinitionStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public LabelRuleStore labelRuleStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoLabelRuleStoreCore(db, p);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public LabelVocabularyStore vocabularyStore(MongoDatabase db, CurrentPrincipal p) {
        return new MongoLabelVocabularyStoreCore(db, p);
    }
}
