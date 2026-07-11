package com.mandao.grc.modules.assessment;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机扫码签名 REST 端点（V57）。
 *
 * 桌面侧（登录态）：创建令牌 / 轮询取回签名；
 * 手机侧（免登录，token 即凭证）：/api/sign/{token} 信息与提交——不触达任何 RLS 业务表。
 */
@RestController
@RequestMapping("/api")
public class SignatureTicketController {

    private final SignatureTicketService service;

    public SignatureTicketController(SignatureTicketService service) {
        this.service = service;
    }

    /** 桌面：创建一次性签名令牌（5 分钟有效）。 */
    @PostMapping("/assessments/{id}/sign-ticket")
    @RequiresPermission("risk")
    public SignatureTicket create(@PathVariable Long id,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.createTicket(id, actorOf(user));
    }

    /** 桌面：轮询取回手机签名（SIGNED → 返回 dataURL 并作废令牌）。 */
    @GetMapping("/assessments/{id}/sign-ticket/{token}")
    @RequiresPermission("risk")
    public SignatureTicketService.FetchResult fetch(@PathVariable Long id, @PathVariable String token) {
        return service.fetchSignature(id, token);
    }

    /** 手机（免登录）：令牌信息（签名页打开时校验与展示）。 */
    @GetMapping("/sign/{token}")
    public SignatureTicketService.TicketInfo info(@PathVariable String token) {
        return service.ticketInfo(token);
    }

    /** 手机（免登录）：提交手写签名。 */
    @PostMapping("/sign/{token}")
    public void submit(@PathVariable String token, @RequestBody SignSubmitRequest req) {
        service.submitSignature(token, req.signatureDataUrl());
    }

    private String actorOf(String user) {
        String current = CurrentUserContext.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 手机签名提交体。 */
    public record SignSubmitRequest(String signatureDataUrl) {
    }
}
