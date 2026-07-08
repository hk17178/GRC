package com.mandao.grc.modules.classification;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据分级引擎 REST 端点：/api/data-classification（B30 · 合规二期）。
 *
 *  · GET /my-clearance —— 当前登录者的数据密级（前端据此提示"敏感字段将脱敏显示"）；任意登录者可查自身；
 *  · GET /access-log   —— 敏感数据访问留痕（审计视角，最近 N 条）；门控 org.viewSensitive（看敏感访问史本身是敏感操作）。
 *
 * 隔离：留痕查询在服务层 @Transactional（RLS），仅回当前可见域。
 */
@RestController
@RequestMapping("/api/data-classification")
public class DataClassificationController {

    private final DataClassificationService service;

    public DataClassificationController(DataClassificationService service) {
        this.service = service;
    }

    /** 当前登录者的数据密级。 */
    @GetMapping("/my-clearance")
    public Map<String, Object> myClearance() {
        DataLevel level = service.clearanceOf(CurrentUserContext.get());
        return Map.of(
                "clearance", level.name(),
                "canViewSensitive", level.atLeast(DataLevel.SENSITIVE));
    }

    /** 敏感数据访问留痕（审计）。 */
    @GetMapping("/access-log")
    @RequiresPermission("org.viewSensitive")
    public List<Map<String, Object>> accessLog(@RequestParam(defaultValue = "50") int limit) {
        return service.recentLog(limit);
    }
}
