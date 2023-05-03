package com.lkd.annotations;

import java.lang.annotation.*;

/**
 * 协议处理类型注解
 *
 * @author Administrator
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ProcessType {
    String value();
}
