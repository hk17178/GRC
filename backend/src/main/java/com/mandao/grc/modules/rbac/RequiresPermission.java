package com.mandao.grc.modules.rbac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注写接口所需的资源读写权（增强③ R3 后端强制）。
 * 切面在方法执行前校验当前用户对该资源 = RW，否则 403。value 为资源 code(如 risk.create)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
