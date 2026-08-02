package io.casehub.work.progress;

import java.util.List;

public record RollupContext(
        ProgressInstance parent,
        List<ProgressInstance> children
) {}
