package com.socialhub.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link CurrentUser} controller parameter.
 * With {@code required = false} the parameter is null for anonymous callers,
 * which public read endpoints (for example watching a public video) use.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
    boolean required() default true;
}
