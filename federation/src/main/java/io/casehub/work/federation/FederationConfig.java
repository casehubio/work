package io.casehub.work.federation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.work.federation")
public interface FederationConfig {

    @WithDefault("default")
    String serviceId();

    @WithDefault("5")
    int proxyTimeoutSeconds();
}
