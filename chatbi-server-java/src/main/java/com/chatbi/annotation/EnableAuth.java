package com.chatbi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用认证注解
 * 用于标记需要token验证的方法或类
 * 如果标记在类上，则类下所有方法都需要认证
 * 如果标记在方法上，则仅该方法需要认证
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableAuth {
    /**
     * 是否必须认证（默认为true）
     * 如果为false，则token不存在时不会抛出异常，但仍会验证token的有效性
     */
    boolean required() default true;

    /**
     * 允许访问的角色名称列表（默认为空表示不限制角色）
     */
    String[] roleNames() default {};
}
