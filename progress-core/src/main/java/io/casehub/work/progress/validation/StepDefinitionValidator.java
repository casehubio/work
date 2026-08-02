package io.casehub.work.progress.validation;

import io.casehub.work.progress.StepDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StepDefinitionValidator {

    public void validate(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Step definition must contain at least one step");
        }

        Set<String> names = new HashSet<>();
        for (StepDefinition step : steps) {
            if (!names.add(step.name())) {
                throw new IllegalArgumentException("Step definition contains duplicate step name: " + step.name());
            }
        }

        for (StepDefinition step : steps) {
            for (String dep : step.dependsOn()) {
                if (!names.contains(dep)) {
                    throw new IllegalArgumentException(
                            "Step '" + step.name() + "' depends on unknown step: " + dep);
                }
            }
        }

        boolean hasRequired = steps.stream().anyMatch(s -> !s.optional());
        if (!hasRequired) {
            throw new IllegalArgumentException(
                    "Step definition must have at least one required (non-optional) step");
        }

        detectCycles(steps, names);
    }

    private void detectCycles(List<StepDefinition> steps, Set<String> allNames) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (StepDefinition step : steps) {
            adjacency.put(step.name(), step.dependsOn());
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String name : allNames) {
            if (!visited.contains(name)) {
                if (hasCycleDfs(name, adjacency, visited, inStack)) {
                    throw new IllegalArgumentException(
                            "Step definition contains a cycle involving step: " + name);
                }
            }
        }
    }

    private boolean hasCycleDfs(String node, Map<String, List<String>> adjacency,
                                Set<String> visited, Set<String> inStack) {
        visited.add(node);
        inStack.add(node);

        List<String> deps = adjacency.getOrDefault(node, List.of());
        for (String dep : deps) {
            if (inStack.contains(dep)) {
                return true;
            }
            if (!visited.contains(dep) && hasCycleDfs(dep, adjacency, visited, inStack)) {
                return true;
            }
        }

        inStack.remove(node);
        return false;
    }
}
