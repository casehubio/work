package io.casehub.work.runtime.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * CDI qualifier for the system-level {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 *
 * <p>Beans qualified with {@code @WorkSystem} represent the work-items
 * subsystem acting on its own behalf (background jobs, timer callbacks,
 * startup recovery) rather than on behalf of a human user.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface WorkSystem {
}
