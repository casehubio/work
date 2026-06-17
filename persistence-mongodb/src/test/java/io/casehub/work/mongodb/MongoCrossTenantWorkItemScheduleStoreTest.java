package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoCrossTenantWorkItemScheduleStoreTest {

    @Inject
    MutableCurrentPrincipal principal;

    @Inject
    CrossTenantWorkItemScheduleStore store;

    @BeforeEach
    void setUp() {
        principal.reset();
        MongoWorkItemScheduleDocument.deleteAll();
    }

    @Test
    void findActive_returnsSchedulesAcrossTenants() {
        principal.setTenancyId("tenant-a");
        persistSchedule("Tenant A active", true);

        principal.setTenancyId("tenant-b");
        persistSchedule("Tenant B active", true);

        List<WorkItemSchedule> results = store.findActive();

        assertThat(results).hasSize(2)
                .extracting(s -> s.name)
                .containsExactlyInAnyOrder("Tenant A active", "Tenant B active");
    }

    @Test
    void findActive_excludesInactiveSchedules() {
        principal.setTenancyId("tenant-a");
        persistSchedule("Active", true);
        persistSchedule("Inactive", false);

        List<WorkItemSchedule> results = store.findActive();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name).isEqualTo("Active");
    }

    private void persistSchedule(String name, boolean active) {
        MongoWorkItemScheduleDocument doc = new MongoWorkItemScheduleDocument();
        doc.id = UUID.randomUUID().toString();
        doc.tenancyId = principal.tenancyId();
        doc.name = name;
        doc.templateId = UUID.randomUUID().toString();
        doc.cronExpression = "0 0 9 * * ?";
        doc.active = active;
        doc.createdBy = "test";
        doc.createdAt = Instant.now();
        doc.version = 0L;
        doc.persist();
    }
}
