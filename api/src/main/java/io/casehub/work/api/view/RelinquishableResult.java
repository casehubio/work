package io.casehub.work.api.view;

import java.util.UUID;

public record RelinquishableResult(UUID workItemId, boolean relinquishable) {
}
