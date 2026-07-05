package io.casehub.work.api;

import java.util.Locale;

public final class WorkCloudEventTypes {

    public static final String PREFIX = "io.casehub.work.workitem.";
    public static final String GROUP_PREFIX = "io.casehub.work.group.";

    // Inbound — not a lifecycle event, not in WorkEventType
    public static final String REQUESTED = PREFIX + "requested";

    // Lifecycle — one per WorkEventType value
    public static final String CREATED = PREFIX + "created";
    public static final String ASSIGNED = PREFIX + "assigned";
    public static final String STARTED = PREFIX + "started";
    public static final String COMPLETED = PREFIX + "completed";
    public static final String REJECTED = PREFIX + "rejected";
    public static final String FAULTED = PREFIX + "faulted";
    public static final String DELEGATED = PREFIX + "delegated";
    public static final String DELEGATION_ACCEPTED = PREFIX + "delegation_accepted";
    public static final String DELEGATION_DECLINED = PREFIX + "delegation_declined";
    public static final String RELEASED = PREFIX + "released";
    public static final String SUSPENDED = PREFIX + "suspended";
    public static final String RESUMED = PREFIX + "resumed";
    public static final String CANCELLED = PREFIX + "cancelled";
    public static final String OBSOLETE = PREFIX + "obsolete";
    public static final String EXPIRED = PREFIX + "expired";
    public static final String CLAIM_EXPIRED = PREFIX + "claim_expired";
    public static final String SPAWNED = PREFIX + "spawned";
    public static final String ESCALATED = PREFIX + "escalated";
    public static final String DEADLINE_EXTENDED = PREFIX + "deadline_extended";
    public static final String SLA_REASSIGNED = PREFIX + "sla_reassigned";
    public static final String SLA_EXTENDED = PREFIX + "sla_extended";
    public static final String SIGNAL_RECEIVED = PREFIX + "signal_received";
    public static final String MANUALLY_ESCALATED = PREFIX + "manually_escalated";
    public static final String PROGRESS_UPDATE = PREFIX + "progress_update";
    public static final String LABEL_ADDED = PREFIX + "label_added";
    public static final String LABEL_REMOVED = PREFIX + "label_removed";

    // Extension attribute names
    public static final String EXT_TENANCY_ID = "tenancyid";
    public static final String EXT_TEMPLATE_ID = "templateid";

    public static String forEventType(final WorkEventType type) {
        return PREFIX + type.name().toLowerCase(Locale.ROOT);
    }

    private WorkCloudEventTypes() {}
}
