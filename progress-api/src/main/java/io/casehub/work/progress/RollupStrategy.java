package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.routing.NamedStrategy;

public interface RollupStrategy extends NamedStrategy {
    JsonNode compute(RollupContext context);
}
