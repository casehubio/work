package io.casehub.work.runtime.migration;

import io.quarkus.test.junit.QuarkusTest;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CloudEventIdempotencyIndexTest {

    @Inject WorkItemService workItemService;
    @Inject WorkItemStore workItemStore;

    @Test
    void applicationLevelIdempotency_findByCallerRef_preventsDuplicate() {
        final String callerRef = "ce-" + UUID.randomUUID();
        final WorkItemCreateRequest request = WorkItemCreateRequest.builder()
                .title("Idempotency test")
                .callerRef(callerRef)
                .createdBy("cloudevent:/test")
                .build();

        workItemService.create(request);

        assertThat(workItemService.findByCallerRef(callerRef)).isPresent();

        final long countBefore = workItemStore.scanAll().stream()
                .filter(wi -> callerRef.equals(wi.callerRef))
                .count();
        assertThat(countBefore).isEqualTo(1);
    }
}
