package com.mandao.grc.modules.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 看板与留痕 REST 端点：/api/audit-trail。
 *
 * 隔离：可见范围由 X-User 头决定（切面注入 visible_orgs）；查询仅覆盖可见组织的留痕。
 */
@RestController
@RequestMapping("/api/audit-trail")
public class AuditTrailController {

    private final AuditTrailService service;

    public AuditTrailController(AuditTrailService service) {
        this.service = service;
    }

    /** 查询操作留痕（可选按 对象/动作/操作人 过滤；新→旧）。 */
    @GetMapping
    public List<OperationLogView> query(@RequestParam(required = false) String entity,
                                        @RequestParam(required = false) String action,
                                        @RequestParam(required = false) String actor,
                                        @RequestParam(required = false) Integer limit) {
        return service.query(entity, action, actor, limit);
    }

    /** 校验某 org 整条链的完整性（防篡改）。 */
    @GetMapping("/verify")
    public ChainVerifyResult verify(@RequestParam Long orgId) {
        return service.verify(orgId);
    }
}
