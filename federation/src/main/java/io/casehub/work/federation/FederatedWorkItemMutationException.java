package io.casehub.work.federation;

import java.util.UUID;

public class FederatedWorkItemMutationException extends RuntimeException {

    private final UUID workItemId;

    public FederatedWorkItemMutationException(UUID workItemId) {
        super("Cannot mutate federated shadow WorkItem " + workItemId
                + " without FederationSyncContext — use FederationReceiver for shadow updates");
        this.workItemId = workItemId;
    }

    public UUID workItemId() {
        return workItemId;
    }
}
