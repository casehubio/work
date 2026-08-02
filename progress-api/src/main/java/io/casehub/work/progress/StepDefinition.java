package io.casehub.work.progress;

import java.util.List;

public record StepDefinition(
        String name,
        boolean optional,
        List<String> dependsOn,
        String condition
) {
    public StepDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Step name is required");
        }
        if (dependsOn == null) {
            dependsOn = List.of();
        }
    }
}
