package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemScheduleStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemScheduleStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemScheduleStore store;

    @Inject
    WorkItemTemplateStore templateStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a minimal template in the current tenant for FK satisfaction. */
    private WorkItemTemplate createTemplate() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "template-" + UUID.randomUUID();
        t.createdBy = "test";
        t.createdAt = Instant.now();
        return templateStore.put(t);
    }

    private WorkItemSchedule newSchedule(String name, UUID templateId) {
        WorkItemSchedule s = new WorkItemSchedule();
        s.name = name;
        s.templateId = templateId;
        s.cronExpression = "0 0 9 * * ?";
        s.active = true;
        s.createdBy = "test";
        s.createdAt = Instant.now();
        s.nextFireAt = Instant.now().plusSeconds(3600);
        return s;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate t = createTemplate();

        WorkItemSchedule s = newSchedule("stamp-test", t.id);
        assertThat(s.tenancyId).isNull();

        store.put(s);

        assertThat(s.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantSchedule() {
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate t = createTemplate();
        WorkItemSchedule s = newSchedule("get-isolation", t.id);
        store.put(s);
        UUID id = s.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void scanAll_returnsOnlyCurrentTenantSchedules() {
        String nameA = "scan-a-" + UUID.randomUUID();
        String nameB = "scan-b-" + UUID.randomUUID();

        principal.setTenancyId(TENANT_A);
        WorkItemTemplate tA = createTemplate();
        store.put(newSchedule(nameA, tA.id));

        principal.setTenancyId(TENANT_B);
        WorkItemTemplate tB = createTemplate();
        store.put(newSchedule(nameB, tB.id));

        List<WorkItemSchedule> resultB = store.scanAll();
        assertThat(resultB).extracting("name").contains(nameB);
        assertThat(resultB).extracting("name").doesNotContain(nameA);

        principal.setTenancyId(TENANT_A);
        List<WorkItemSchedule> resultA = store.scanAll();
        assertThat(resultA).extracting("name").contains(nameA);
        assertThat(resultA).extracting("name").doesNotContain(nameB);
    }

    @Test
    void findDue_returnsOnlyCurrentTenantSchedules() {
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate tA = createTemplate();
        WorkItemSchedule sA = newSchedule("due-a", tA.id);
        sA.nextFireAt = Instant.now().minusSeconds(60); // due
        store.put(sA);

        principal.setTenancyId(TENANT_B);
        WorkItemTemplate tB = createTemplate();
        WorkItemSchedule sB = newSchedule("due-b", tB.id);
        sB.nextFireAt = Instant.now().minusSeconds(60); // also due
        store.put(sB);

        // As tenant B, should only see B's due schedule
        List<WorkItemSchedule> dueB = store.findDue(Instant.now());
        assertThat(dueB).extracting("name").contains("due-b");
        assertThat(dueB).extracting("name").doesNotContain("due-a");

        principal.setTenancyId(TENANT_A);
        List<WorkItemSchedule> dueA = store.findDue(Instant.now());
        assertThat(dueA).extracting("name").contains("due-a");
        assertThat(dueA).extracting("name").doesNotContain("due-b");
    }

    @Test
    void delete_cannotDeleteAnotherTenantSchedule() {
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate t = createTemplate();
        WorkItemSchedule s = newSchedule("delete-isolation", t.id);
        store.put(s);
        UUID id = s.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}
