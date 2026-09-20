package com.kiftd.aop;

import java.lang.annotation.*;

/**
 * Skip HTTP access logging on a controller, method, or parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Inherited
@Documented
public @interface HttpLoggingIgnore {
}
