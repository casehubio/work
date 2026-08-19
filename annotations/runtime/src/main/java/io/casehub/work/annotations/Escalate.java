package io.casehub.work.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface Escalate {
    String onExpiry() default "";
    String onClaimDeadline() default "";
    String deadline() default "";
    boolean generateSummary() default true;
}
