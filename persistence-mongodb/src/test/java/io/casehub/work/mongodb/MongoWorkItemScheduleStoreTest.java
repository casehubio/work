package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemScheduleStoreTest {

    @Inject
    WorkItemScheduleStore store;

    @Inject
    MutableCurrentPrincipal principal;

    private String tenantA;
    private String tenantB;

    @BeforeEach
    void setUp() {
        principal.reset();
        tenantA = "tenant-" + UUID.randomUUID();
        tenantB = "tenant-" + UUID.randomUUID();

        // Clean up all schedules before each test
        MongoWorkItemScheduleDocument.deleteAll();
    }

    @Test
    void put_assignsIdAndTimestamps() {
        principal.setTenancyId(tenantA);

        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Daily Report";
        schedule.templateId = UUID.randomUUID();
        schedule.cronExpression = "0 0 9 * * ?";
        schedule.active = true;
        schedule.createdBy = "user1";

        final WorkItemSchedule saved = store.put(schedule);

        assertThat(saved.id).isNotNull();
        assertThat(saved.createdAt).isNotNull();
        assertThat(saved.tenancyId).isEqualTo(tenantA);
        assertThat(saved.version).isEqualTo(0L);
    }

    @Test
    void put_and_get_roundtrip() {
        principal.setTenancyId(tenantA);

        final UUID templateId = UUID.randomUUID();
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextFire = now.plus(1, ChronoUnit.HOURS);

        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Weekly Summary";
        schedule.templateId = templateId;
        schedule.cronExpression = "0 0 9 ? * MON";
        schedule.active = true;
        schedule.createdBy = "user1";
        schedule.createdAt = now;
        schedule.nextFireAt = nextFire;

        final WorkItemSchedule saved = store.put(schedule);
        final Optional<WorkItemSchedule> retrieved = store.get(saved.id);

        assertThat(retrieved).isPresent();
        final WorkItemSchedule loaded = retrieved.get();
        assertThat(loaded.id).isEqualTo(saved.id);
        assertThat(loaded.name).isEqualTo("Weekly Summary");
        assertThat(loaded.templateId).isEqualTo(templateId);
        assertThat(loaded.cronExpression).isEqualTo("0 0 9 ? * MON");
        assertThat(loaded.active).isTrue();
        assertThat(loaded.createdBy).isEqualTo("user1");
        assertThat(loaded.createdAt).isEqualTo(now);
        assertThat(loaded.nextFireAt).isEqualTo(nextFire);
        assertThat(loaded.lastFiredAt).isNull();
        assertThat(loaded.version).isEqualTo(0L);
    }

    @Test
    void scanAll_orderedByName() {
        principal.setTenancyId(tenantA);

        final WorkItemSchedule s1 = new WorkItemSchedule();
        s1.name = "Zebra";
        s1.templateId = UUID.randomUUID();
        s1.cronExpression = "0 0 9 * * ?";
        s1.active = true;
        s1.createdBy = "user1";
        store.put(s1);

        final WorkItemSchedule s2 = new WorkItemSchedule();
        s2.name = "Alpha";
        s2.templateId = UUID.randomUUID();
        s2.cronExpression = "0 0 9 * * ?";
        s2.active = true;
        s2.createdBy = "user1";
        store.put(s2);

        final WorkItemSchedule s3 = new WorkItemSchedule();
        s3.name = "Bravo";
        s3.templateId = UUID.randomUUID();
        s3.cronExpression = "0 0 9 * * ?";
        s3.active = true;
        s3.createdBy = "user1";
        store.put(s3);

        final List<WorkItemSchedule> all = store.scanAll();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).name).isEqualTo("Alpha");
        assertThat(all.get(1).name).isEqualTo("Bravo");
        assertThat(all.get(2).name).isEqualTo("Zebra");
    }

    @Test
    void findDue_returnsOnlyActivePastSchedules() {
        principal.setTenancyId(tenantA);

        final Instant now = Instant.now();
        final Instant past = now.minus(1, ChronoUnit.HOURS);
        final Instant future = now.plus(1, ChronoUnit.HOURS);

        // Schedule 1: due (active, past)
        final WorkItemSchedule due = new WorkItemSchedule();
        due.name = "Due";
        due.templateId = UUID.randomUUID();
        due.cronExpression = "0 0 9 * * ?";
        due.active = true;
        due.createdBy = "user1";
        due.nextFireAt = past;
        store.put(due);

        // Schedule 2: future (active, but not yet due)
        final WorkItemSchedule notDue = new WorkItemSchedule();
        notDue.name = "Future";
        notDue.templateId = UUID.randomUUID();
        notDue.cronExpression = "0 0 9 * * ?";
        notDue.active = true;
        notDue.createdBy = "user1";
        notDue.nextFireAt = future;
        store.put(notDue);

        // Schedule 3: inactive (past, but inactive)
        final WorkItemSchedule inactive = new WorkItemSchedule();
        inactive.name = "Inactive";
        inactive.templateId = UUID.randomUUID();
        inactive.cronExpression = "0 0 9 * * ?";
        inactive.active = false;
        inactive.createdBy = "user1";
        inactive.nextFireAt = past;
        store.put(inactive);

        final List<WorkItemSchedule> dueSchedules = store.findDue(now);

        assertThat(dueSchedules).hasSize(1);
        assertThat(dueSchedules.get(0).name).isEqualTo("Due");
    }

    @Test
    void put_update_incrementsVersion() {
        principal.setTenancyId(tenantA);

        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Test";
        schedule.templateId = UUID.randomUUID();
        schedule.cronExpression = "0 0 9 * * ?";
        schedule.active = true;
        schedule.createdBy = "user1";

        final WorkItemSchedule saved = store.put(schedule);
        assertThat(saved.version).isEqualTo(0L);

        final Optional<WorkItemSchedule> loaded = store.get(saved.id);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().version).isEqualTo(0L);

        // Update the schedule
        loaded.get().name = "Updated";
        final WorkItemSchedule updated = store.put(loaded.get());
        assertThat(updated.version).isEqualTo(1L);

        // Verify persisted version
        final Optional<WorkItemSchedule> reloaded = store.get(saved.id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().version).isEqualTo(1L);
        assertThat(reloaded.get().name).isEqualTo("Updated");
    }

    @Test
    void put_throwsOptimisticLockException_onStaleVersion() {
        principal.setTenancyId(tenantA);

        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Concurrent";
        schedule.templateId = UUID.randomUUID();
        schedule.cronExpression = "0 0 9 * * ?";
        schedule.active = true;
        schedule.createdBy = "user1";

        final WorkItemSchedule saved = store.put(schedule);

        // Two readers get the same version
        final Optional<WorkItemSchedule> reader1 = store.get(saved.id);
        final Optional<WorkItemSchedule> reader2 = store.get(saved.id);

        assertThat(reader1).isPresent();
        assertThat(reader2).isPresent();
        assertThat(reader1.get().version).isEqualTo(0L);
        assertThat(reader2.get().version).isEqualTo(0L);

        // Reader 1 updates successfully
        reader1.get().name = "Updated by Reader 1";
        final WorkItemSchedule updated1 = store.put(reader1.get());
        assertThat(updated1.version).isEqualTo(1L);

        // Reader 2 attempts to update with stale version — should fail
        reader2.get().name = "Updated by Reader 2";
        assertThatThrownBy(() -> store.put(reader2.get()))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Version conflict");
    }

    @Test
    void delete_removesAndReturnsBoolean() {
        principal.setTenancyId(tenantA);

        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Delete Me";
        schedule.templateId = UUID.randomUUID();
        schedule.cronExpression = "0 0 9 * * ?";
        schedule.active = true;
        schedule.createdBy = "user1";

        final WorkItemSchedule saved = store.put(schedule);

        // Delete should return true
        final boolean deleted = store.delete(saved.id);
        assertThat(deleted).isTrue();

        // Get should return empty
        final Optional<WorkItemSchedule> retrieved = store.get(saved.id);
        assertThat(retrieved).isEmpty();

        // Second delete should return false
        final boolean deletedAgain = store.delete(saved.id);
        assertThat(deletedAgain).isFalse();
    }

    @Test
    void tenantIsolation() {
        // Create schedule in tenant A
        principal.setTenancyId(tenantA);

        final Instant now = Instant.now();
        final WorkItemSchedule schedule = new WorkItemSchedule();
        schedule.name = "Tenant A Schedule";
        schedule.templateId = UUID.randomUUID();
        schedule.cronExpression = "0 0 9 * * ?";
        schedule.active = true;
        schedule.createdBy = "user1";
        schedule.nextFireAt = now.minus(1, ChronoUnit.HOURS);

        final WorkItemSchedule saved = store.put(schedule);

        // Switch to tenant B — should not see tenant A's schedule
        principal.setTenancyId(tenantB);

        final Optional<WorkItemSchedule> retrieved = store.get(saved.id);
        assertThat(retrieved).isEmpty();

        final List<WorkItemSchedule> all = store.scanAll();
        assertThat(all).isEmpty();

        final List<WorkItemSchedule> due = store.findDue(now);
        assertThat(due).isEmpty();

        final boolean deleted = store.delete(saved.id);
        assertThat(deleted).isFalse();

        // Switch back to tenant A — should still exist
        principal.setTenancyId(tenantA);

        final Optional<WorkItemSchedule> stillThere = store.get(saved.id);
        assertThat(stillThere).isPresent();
    }
}
