package io.casehub.work.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface SkillMatch {
    String strategy() default "";
    String[] requiredCapabilities() default {};
    double minimumScore() default -1.0;
}
