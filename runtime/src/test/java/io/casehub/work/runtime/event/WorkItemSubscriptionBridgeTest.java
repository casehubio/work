package io.casehub.work.runtime.event;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.path.Path;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItem;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorkItemSubscriptionBridgeTest {

    @Test
    void onWorkItemEvent_insertsIntoDataSource() {
        var added = new ArrayList<>();
        @SuppressWarnings("unchecked")
        DataSource<Object> ds = mock(DataSource.class);
        doAnswer(inv -> { added.add(inv.getArgument(0)); return null; })
                .when(ds).add(any());

        var registry = mock(DataSourceRegistry.class);
        when(registry.resolveSource(eq(NOTIFICATION_DATASOURCE_PATH), eq(PLATFORM_TENANT_ID)))
                .thenReturn(Optional.of(ds));

        var bridge = new WorkItemSubscriptionBridge();
        setField(bridge, "dataSourceRegistryInstance", satisfiedInstance(registry));

        var event = sampleEvent("COMPLETED");
        bridge.onWorkItemEvent(event);

        assertThat(added).hasSize(1);
        assertThat(added.get(0)).isSameAs(event);
    }

    @Test
    void onWorkItemEvent_noOpWhenRegistryUnsatisfied() {
        var bridge = new WorkItemSubscriptionBridge();
        setField(bridge, "dataSourceRegistryInstance", unsatisfiedInstance());

        bridge.onWorkItemEvent(sampleEvent("CREATED"));
    }

    @Test
    void onWorkItemEvent_catchesAndLogsExceptions() {
        var registry = mock(DataSourceRegistry.class);
        when(registry.resolveSource(any(Path.class), any(String.class)))
                .thenThrow(new RuntimeException("DataSource unavailable"));

        var bridge = new WorkItemSubscriptionBridge();
        setField(bridge, "dataSourceRegistryInstance", satisfiedInstance(registry));

        bridge.onWorkItemEvent(sampleEvent("ASSIGNED"));
    }

    private WorkItemLifecycleEvent sampleEvent(final String name) {
        var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.IN_PROGRESS;
        wi.tenancyId = "test-tenant";
        return WorkItemLifecycleEvent.of(name, wi, "test", null);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> satisfiedInstance(final T value) {
        var instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(value);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> unsatisfiedInstance() {
        var instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        return instance;
    }

    private void setField(final Object target, final String fieldName, final Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
