package com.mandao.grc.modules.rbac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * RBAC 启动引导（增强③ R6 · 让新功能权限丝滑融入）。
 *
 * 启动时：
 *  1) 把 {@link ResourceCatalog} 幂等同步到 resource 表（新增菜单/按钮只改代码目录、免迁移；超管自动覆盖、矩阵自动收录）；
 *  2) 扫描所有控制器的 {@link RequiresPermission}，校验其 code 都已登记到目录——
 *     否则<b>启动失败</b>（fail-fast），把"漏登记资源 → 连超管都被 403"的坑挡在上线前。
 */
@Component
public class RbacBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacBootstrap.class);

    private final RbacResourceSync sync;
    private final ApplicationContext ctx;

    public RbacBootstrap(RbacResourceSync sync, ApplicationContext ctx) {
        this.sync = sync;
        this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = sync.upsertAll();
        log.info("RBAC 资源目录已同步：{} 项资源", n);

        Set<String> catalog = ResourceCatalog.codes();
        List<String> missing = new ArrayList<>();
        for (Object bean : ctx.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> cls = AopUtils.getTargetClass(bean);
            for (Method m : cls.getMethods()) {
                RequiresPermission rp = m.getAnnotation(RequiresPermission.class);
                if (rp != null && !catalog.contains(rp.value())) {
                    missing.add(cls.getSimpleName() + "#" + m.getName() + " → \"" + rp.value() + "\"");
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "@RequiresPermission 引用了未登记到 ResourceCatalog 的资源（会导致超管也被 403）：\n - "
                            + String.join("\n - ", missing)
                            + "\n请在 ResourceCatalog.ALL 补登记对应资源。");
        }
        log.info("RBAC 注解资源校验通过：所有 @RequiresPermission 的 code 均已登记");
    }
}
