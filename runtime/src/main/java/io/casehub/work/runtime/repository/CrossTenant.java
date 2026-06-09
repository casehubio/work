package io.casehub.work.runtime.repository;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * CDI qualifier for cross-tenant store variants.
 *
 * <p>Injection points annotated {@code @CrossTenant} receive a store
 * implementation that bypasses the default tenant filter, allowing
 * queries and writes across all tenants.  Only system-level services
 * (e.g.&nbsp;background jobs, admin endpoints) should use this qualifier.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface CrossTenant {
}
