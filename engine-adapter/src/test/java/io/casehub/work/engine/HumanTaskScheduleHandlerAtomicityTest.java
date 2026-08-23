/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.work.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.HumanTaskTarget;
import io.casehub.api.model.TaskStatus;
import io.casehub.engine.common.internal.model.PlanItemRecord;
import io.casehub.engine.common.spi.HumanTaskScheduleRequest;
import io.casehub.engine.common.spi.PlanItemStore;
import io.casehub.engine.planning.plan.PlanItem;
import io.casehub.engine.planning.registry.BlackboardRegistry;
import io.casehub.ledger.testing.NoOpLedgerEntryRepository;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
import io.casehub.persistence.memory.InMemoryCaseMetaModelRepository;
import io.casehub.persistence.memory.InMemoryEventLogRepository;
import io.casehub.persistence.memory.InMemoryPlanItemStore;
import io.casehub.persistence.memory.InMemorySubCaseGroupRepository;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a WorkItem creation failure leaves PlanItem PENDING and does not write a RUNNING
 * status to the store (atomicity guarantee, engine#273). Runs under its own QuarkusTestProfile so
 * FailingWorkItemStore is activated only for this class.
 *
 * <p>Refs engine#282, engine#273.
 */
@QuarkusTest
@TestProfile(HumanTaskScheduleHandlerAtomicityTest.Profile.class)
class HumanTaskScheduleHandlerAtomicityTest {

  private static final String TENANCY_ID = "test-tenant";

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
      return Set.of(
          FailingWorkItemStore.class,
          InMemoryCaseInstanceRepository.class,
          InMemoryCaseMetaModelRepository.class,
          InMemoryEventLogRepository.class,
          InMemorySubCaseGroupRepository.class,
          InMemoryPlanItemStore.class,
          NoOpLedgerEntryRepository.class,
          NoOpPreferenceProvider.class);
    }
  }

  @ApplicationScoped
  @Alternative
  @Priority(2)
  public static class FailingWorkItemStore implements WorkItemStore {

    public static final AtomicBoolean shouldFail = new AtomicBoolean(false);

    private final java.util.Map<UUID, WorkItem> store = new ConcurrentHashMap<>();

    public void clear() {
      store.clear();
    }

    @Override
    public WorkItem put(WorkItem w) {
      if (shouldFail.get()) throw new RuntimeException("Simulated WorkItemStore failure");
      WorkItem stored = w;
      if (stored.id() == null) stored = stored.toBuilder().id(UUID.randomUUID()).build();
      store.put(stored.id(), stored);
      return stored;
    }

    @Override
    public Optional<WorkItem> get(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<WorkItem> scan(WorkItemQuery query) {
      return new ArrayList<>(store.values());
    }
  }

  @Inject BlackboardRegistry registry;
  @Inject HumanTaskScheduleHandler handler;
  @Inject WorkItemStore workItemStore;
  @Inject PlanItemStore planItemStore;

  private UUID caseId;
  private PlanItem planItem;

  @BeforeEach
  @Transactional
  void setUp() {
    assertThat(workItemStore).isInstanceOf(FailingWorkItemStore.class);
    FailingWorkItemStore failing = (FailingWorkItemStore) workItemStore;
    failing.clear();
    FailingWorkItemStore.shouldFail.set(false);
    if (planItemStore instanceof InMemoryPlanItemStore mem) {
      mem.clear();
    }
    caseId = UUID.randomUUID();
    planItem =
        PlanItem.create(
            "irb-binding", io.casehub.api.model.ExecutorRef.of("unused-worker"), 5);
    assertThat(planItem.tryMarkDispatching()).isTrue();
    registry.getOrCreate(caseId, "test-tenant").addPlanItem(planItem);
  }

  @AfterEach
  void tearDown() {
    registry.evict(caseId);
  }

  @Test
  void inlineMode_workItemCreationFails_planItemStaysPending_storeNotUpdated() {
    HumanTaskTarget target = HumanTaskTarget.inline().title("Review").build();

    FailingWorkItemStore.shouldFail.set(true);
    try {
      handler.schedule(
          new HumanTaskScheduleRequest(
              caseId, TENANCY_ID, "irb-binding", target, Map.of(), null, null, null, null, null,
              null, null, null, null, null));

      assertThat(planItem.getStatus()).isEqualTo(TaskStatus.PENDING);
      assertThat(workItemStore.scanAll()).isEmpty();
      List<PlanItemRecord> records = planItemStore.findByCaseId(caseId, "test-tenant");
      assertThat(records)
          .noneMatch(
              r ->
                  r.planItemId().equals(planItem.getPlanItemId())
                      && r.status() == TaskStatus.RUNNING);
    } finally {
      FailingWorkItemStore.shouldFail.set(false);
    }
  }
}
