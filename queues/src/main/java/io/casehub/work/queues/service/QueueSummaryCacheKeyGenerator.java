package io.casehub.work.queues.service;

import io.casehub.platform.api.view.SubjectViewSpec;
import io.quarkus.cache.CacheKeyGenerator;
import io.quarkus.cache.CompositeCacheKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Method;

@ApplicationScoped
public class QueueSummaryCacheKeyGenerator implements CacheKeyGenerator {

    @Override
    public Object generate(Method method, Object... params) {
        SubjectViewSpec spec = (SubjectViewSpec) params[0];
        return new CompositeCacheKey(spec.id(), spec.tenancyId());
    }
}
