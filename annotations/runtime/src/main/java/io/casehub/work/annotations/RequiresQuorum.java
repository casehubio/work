package io.casehub.work.annotations;

import io.casehub.work.api.OnThresholdReached;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface RequiresQuorum {
    int instances();
    int required();
    String[] candidateGroups() default {};
    OnThresholdReached onThresholdReached() default OnThresholdReached.KEEP;
    boolean allowSameAssignee() default false;
}
