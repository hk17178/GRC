package com.mandao.grc.modules.audit.management;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 证书有效期台账 REST 端点：/api/certificates（收口批 B24）。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 登录态优先。
 * 到期提醒由内核到期扫描统一调度，本端点只做 CRUD。
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService service;

    public CertificateController(CertificateService service) {
        this.service = service;
    }

    @GetMapping
    public List<Certificate> list() {
        return service.list();
    }

    @PostMapping
    @RequiresPermission("extaudit")
    public Certificate create(@RequestBody CreateRequest req,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.name(), req.framework(), req.certNo(), req.issuer(),
                req.issuedDate(), req.expiryDate(), actor(user));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission("extaudit")
    public Certificate revoke(@PathVariable Long id,
                              @RequestHeader(value = "X-User", required = false) String user) {
        return service.revoke(id, actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user);
    }

    /** 登记证书请求体。 */
    public record CreateRequest(Long orgId, String name, String framework, String certNo, String issuer,
                                LocalDate issuedDate, LocalDate expiryDate) {
    }
}
