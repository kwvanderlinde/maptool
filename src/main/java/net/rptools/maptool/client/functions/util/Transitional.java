package net.rptools.maptool.client.functions.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a @MacroFunction that takes a List<Object> as parameters and returns Object.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Transitional {
    int minParameters() default 0;
    int maxParameters() default -1;
}
