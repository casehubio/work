package io.casehub.work.mongodb.spring;

import com.mongodb.client.MongoClient;
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
import io.casehub.work.mongodb.core.MongoDatabaseFactory;
import io.casehub.work.mongodb.core.MongoIndexInitializer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MongoClient.class)
public class WorkPersistenceMongoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "workMongoDatabase")
    public MongoDatabase workMongoDatabase(MongoClient client,
            @Value("${spring.data.mongodb.database}") String dbName) {
        return MongoDatabaseFactory.withPojoCodecs(client.getDatabase(dbName));
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemStore.class)
    public WorkItemStore workItemStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(CrossTenantWorkItemStore.class)
    public CrossTenantWorkItemStore crossTenantWorkItemStore(MongoDatabase workMongoDatabase) {
        return new MongoCrossTenantWorkItemStoreCore(workMongoDatabase);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEntryStore.class)
    public AuditEntryStore auditEntryStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoAuditEntryStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemScheduleStore.class)
    public WorkItemScheduleStore scheduleStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemScheduleStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(CrossTenantWorkItemScheduleStore.class)
    public CrossTenantWorkItemScheduleStore crossTenantScheduleStore(MongoDatabase workMongoDatabase) {
        return new MongoCrossTenantWorkItemScheduleStoreCore(workMongoDatabase);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemTemplateStore.class)
    public WorkItemTemplateStore templateStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemTemplateStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(RoutingCursorStore.class)
    public RoutingCursorStore routingCursorStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoRoutingCursorStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(CrossTenantRoutingCursorStore.class)
    public CrossTenantRoutingCursorStore crossTenantRoutingCursorStore(MongoDatabase workMongoDatabase) {
        return new MongoCrossTenantRoutingCursorStoreCore(workMongoDatabase);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemNoteStore.class)
    public WorkItemNoteStore noteStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemNoteStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemRelationStore.class)
    public WorkItemRelationStore relationStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemRelationStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemLinkStore.class)
    public WorkItemLinkStore linkStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemLinkStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(WorkItemSpawnGroupStore.class)
    public WorkItemSpawnGroupStore spawnGroupStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoWorkItemSpawnGroupStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(IssueLinkStore.class)
    public IssueLinkStore issueLinkStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoIssueLinkStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(LabelDefinitionStore.class)
    public LabelDefinitionStore labelDefinitionStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoLabelDefinitionStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(LabelRuleStore.class)
    public LabelRuleStore labelRuleStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoLabelRuleStoreCore(workMongoDatabase, principal);
    }

    @Bean
    @ConditionalOnMissingBean(LabelVocabularyStore.class)
    public LabelVocabularyStore vocabularyStore(MongoDatabase workMongoDatabase, CurrentPrincipal principal) {
        return new MongoLabelVocabularyStoreCore(workMongoDatabase, principal);
    }

    @Bean
    public MongoIndexInitializer mongoIndexInitializer(MongoDatabase workMongoDatabase) {
        return new MongoIndexInitializer(workMongoDatabase);
    }

    @Bean
    public CommandLineRunner mongoIndexRunner(MongoIndexInitializer indexInitializer) {
        return args -> indexInitializer.init();
    }
}
