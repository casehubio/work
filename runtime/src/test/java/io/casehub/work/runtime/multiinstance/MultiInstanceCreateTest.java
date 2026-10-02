package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.model.WorkItemEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import jakarta.persistence.EntityManager;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MultiInstanceCreateTest {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void clearTemplates() {
        em.createQuery("DELETE FROM WorkItemTemplate").executeUpdate();
    }

    @Test
    @Transactional
    void instantiatingMultiInstanceTemplateCreatesParentAndNChildren() {
        WorkItemTemplate template = new WorkItemTemplate();
        template.name = "Approval";
        template.typePaths = "[\"approval\"]";
        template.candidateGroups = "reviewers";
        template.createdBy = "test";
        template.instanceCount = 3;
        template.requiredCount = 2;
        template.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        em.persist(template);

        final var request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .createdBy("test")
                .build();
        WorkItem parent = templateService.createFromTemplate(request);

        assertThat(parent.parentId()).isNull(); // parent has no parent
        assertThat(parent.id()).isNotNull();

        // Three children should exist
        List<WorkItemEntity> children = em.createQuery("FROM WorkItemEntity WHERE parentId = ?1", WorkItemEntity.class).setParameter(1, parent.id()).getResultList();
        assertThat(children).hasSize(3);

        // All children have PART_OF relation to parent
        children.forEach(child -> {
            assertThat(child.parentId).isEqualTo(parent.id());
            long relations = em.createQuery("SELECT COUNT(r) FROM WorkItemRelation r WHERE r.sourceId = ?1 AND r.targetId = ?2 AND r.relationType = ?3", Long.class).setParameter(1, child.id).setParameter(2, parent.id()).setParameter(3, WorkItemRelationType.PART_OF).getSingleResult();
            assertThat(relations).isEqualTo(1);
        });

        // Spawn group created with policy
        WorkItemSpawnGroup group = em.createQuery("FROM WorkItemSpawnGroup WHERE workItemId = ?1 AND requiredCount IS NOT NULL", WorkItemSpawnGroup.class).setParameter(1, parent.id()).getResultStream().findFirst().orElse(null);
        assertThat(group).isNotNull();
        assertThat(group.instanceCount).isEqualTo(3);
        assertThat(group.requiredCount).isEqualTo(2);
        assertThat(group.completedCount).isZero();
        assertThat(group.policyTriggered).isFalse();
    }

    @Test
    @Transactional
    void nonMultiInstanceTemplateCreatesOneWorkItem() {
        WorkItemTemplate template = new WorkItemTemplate();
        template.name = "Simple Task";
        template.createdBy = "test";
        template.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        em.persist(template);

        final var request = WorkItemCreateRequest.builder()
                .templateId(template.id)
                .createdBy("test")
                .build();
        WorkItem item = templateService.createFromTemplate(request);

        assertThat(item.parentId()).isNull();
        assertThat(em.createQuery("SELECT COUNT(e) FROM WorkItemEntity e WHERE e.parentId = ?1", Long.class).setParameter(1, item.id()).getSingleResult()).isZero();
    }
}
