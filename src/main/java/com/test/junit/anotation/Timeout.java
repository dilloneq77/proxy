package com.test.junit.anotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Timeout {
    int time();

    TimeUnit timeUnit() default TimeUnit.MILLISECOND;
}
