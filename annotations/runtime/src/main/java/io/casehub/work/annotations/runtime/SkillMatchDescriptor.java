package io.casehub.work.annotations.runtime;

import java.util.List;

public record SkillMatchDescriptor(
    String strategy,
    List<String> requiredCapabilities,
    double minimumScore
) {}
