package io.casehub.work.api.view;

import java.util.UUID;

public record SyncLinksResult(int synced, UUID workItemId) {
}
