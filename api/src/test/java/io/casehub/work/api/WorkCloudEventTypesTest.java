package io.casehub.work.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkCloudEventTypesTest {

    @Test
    void everyWorkEventType_hasMappedConstant() {
        for (final WorkEventType type : WorkEventType.values()) {
            final String expected = WorkCloudEventTypes.PREFIX + type.name().toLowerCase();
            assertThat(WorkCloudEventTypes.forEventType(type))
                    .as("Missing constant for WorkEventType.%s", type.name())
                    .isEqualTo(expected);
        }
    }

    @Test
    void requested_isNotInWorkEventType() {
        assertThat(WorkCloudEventTypes.REQUESTED)
                .isEqualTo("io.casehub.work.workitem.requested");
        for (final WorkEventType type : WorkEventType.values()) {
            assertThat(type.name()).isNotEqualToIgnoringCase("requested");
        }
    }

    @Test
    void prefixFormat() {
        assertThat(WorkCloudEventTypes.PREFIX).isEqualTo("io.casehub.work.workitem.");
        assertThat(WorkCloudEventTypes.GROUP_PREFIX).isEqualTo("io.casehub.work.group.");
    }

    @Test
    void extensionAttributeNames() {
        assertThat(WorkCloudEventTypes.EXT_TENANCY_ID).isEqualTo("tenancyid");
        assertThat(WorkCloudEventTypes.EXT_TEMPLATE_ID).isEqualTo("templateid");
    }
}
