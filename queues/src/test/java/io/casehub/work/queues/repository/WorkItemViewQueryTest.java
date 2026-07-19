package io.casehub.work.queues.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WorkItemViewQueryTest {

    @Inject
    SubjectViewQuery<WorkItem> viewQuery;

    @Inject
    WorkItemStore workItemStore;

    @Test
    @Transactional
    void findByView_returnsMatchingWorkItems() {
        var wi = createWorkItem("legal/contracts", "vq-tenant-1");
        workItemStore.put(wi);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Legal Queue",
                "vq-tenant-1", "legal/**", null, "createdAt", "ASC",
                null, Instant.now());

        var results = viewQuery.findByView(spec);
        assertThat(results).extracting(w -> w.id).contains(wi.id);
    }

    @Test
    @Transactional
    void findByView_excludesNonMatchingLabels() {
        var wi = createWorkItem("finance/invoices", "vq-tenant-2");
        workItemStore.put(wi);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Legal Queue",
                "vq-tenant-2", "legal/**", null, null, null, null, Instant.now());

        var results = viewQuery.findByView(spec);
        assertThat(results).extracting(w -> w.id).doesNotContain(wi.id);
    }

    @Test
    @Transactional
    void findByView_excludesDifferentTenant() {
        var wi = createWorkItem("legal/contracts", "vq-tenant-a");
        workItemStore.put(wi);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Legal Queue",
                "vq-tenant-b", "legal/**", null, null, null, null, Instant.now());

        var results = viewQuery.findByView(spec);
        assertThat(results).extracting(w -> w.id).doesNotContain(wi.id);
    }

    @Test
    @Transactional
    void countByView_returnsCorrectCount() {
        var wi1 = createWorkItem("legal/contracts", "vq-count-tenant");
        var wi2 = createWorkItem("legal/review", "vq-count-tenant");
        var wi3 = createWorkItem("finance/invoices", "vq-count-tenant");
        workItemStore.put(wi1);
        workItemStore.put(wi2);
        workItemStore.put(wi3);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Legal Queue",
                "vq-count-tenant", "legal/**", null, null, null, null, Instant.now());

        assertThat(viewQuery.countByView(spec)).isEqualTo(2);
    }

    @Test
    @Transactional
    void findByView_withPagination() {
        for (int i = 0; i < 5; i++) {
            var wi = createWorkItem("legal/item" + i, "vq-page-tenant");
            workItemStore.put(wi);
        }

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Legal Queue",
                "vq-page-tenant", "legal/**", null, "createdAt", "ASC",
                null, Instant.now());

        var page = viewQuery.findByView(spec, 0, 2);
        assertThat(page).hasSize(2);
    }

    @Test
    @Transactional
    void findByView_exactPatternMatch() {
        var wi = createWorkItem("legal", "vq-exact-tenant");
        workItemStore.put(wi);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Exact Queue",
                "vq-exact-tenant", "legal", null, null, null, null, Instant.now());

        var results = viewQuery.findByView(spec);
        assertThat(results).extracting(w -> w.id).contains(wi.id);
    }

    @Test
    @Transactional
    void findByView_singleWildcard() {
        var wi1 = createWorkItem("legal/contracts", "vq-wild-tenant");
        var wi2 = createWorkItem("legal/contracts/urgent", "vq-wild-tenant");
        workItemStore.put(wi1);
        workItemStore.put(wi2);

        var spec = new SubjectViewSpec(UUID.randomUUID(), "Single Wild",
                "vq-wild-tenant", "legal/*", null, null, null, null, Instant.now());

        var results = viewQuery.findByView(spec);
        assertThat(results).extracting(w -> w.id).contains(wi1.id);
        assertThat(results).extracting(w -> w.id).doesNotContain(wi2.id);
    }

    private WorkItem createWorkItem(String labelPath, String tenancyId) {
        var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.tenancyId = tenancyId;
        wi.title = "Test item " + labelPath;
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.candidateGroups = "test-group";
        wi.createdAt = Instant.now();
        wi.labels.add(new WorkItemLabel(labelPath, LabelPersistence.MANUAL, "test"));
        return wi;
    }
}
