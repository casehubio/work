package io.casehub.work.annotations;

import io.casehub.work.api.WorkItemPriority;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface HumanApproval {
    String title();
    String[] candidateGroups() default {};
    String[] candidateUsers() default {};
    WorkItemPriority priority() default WorkItemPriority.MEDIUM;
    String claimDeadline() default "";
    String expiresAt() default "";
    String description() default "";
    String[] types() default {};
    String[] labels() default {};
}
