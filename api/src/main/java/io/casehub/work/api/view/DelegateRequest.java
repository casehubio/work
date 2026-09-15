package io.casehub.work.api.view;

import io.casehub.work.api.DeclineTarget;

public record DelegateRequest(String to, DeclineTarget declineTarget) {
}
