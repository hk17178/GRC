package com.mandao.grc.modules.settings;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录页与品牌配置端点：/api/branding。
 *
 * GET 公开（登录前可读，全局表无 RLS、不门控）；PUT 受控（门控 "settings"，actor 取登录态）。
 */
@RestController
@RequestMapping("/api/branding")
public class BrandingController {

    private final BrandingService service;

    public BrandingController(BrandingService service) {
        this.service = service;
    }

    /** 公开读取品牌配置（登录页渲染用）。 */
    @GetMapping
    public BrandingConfig get() {
        return service.get();
    }

    /** 保存品牌配置（门控 settings）。 */
    @PutMapping
    @RequiresPermission("settings")
    public BrandingConfig update(@RequestBody BrandingRequest req) {
        String actor = CurrentUserContext.get() == null ? "anonymous" : CurrentUserContext.get();
        return service.update(req.brandName(), req.brandSub(), req.logoText(), req.logoImg(),
                req.loginTitleZh(), req.loginTitleEn(), req.loginSloganZh(), req.loginSloganEn(),
                req.forgotUrl(), actor);
    }

    /** 品牌配置请求体（整表提交）。 */
    public record BrandingRequest(String brandName, String brandSub, String logoText, String logoImg,
                                  String loginTitleZh, String loginTitleEn, String loginSloganZh,
                                  String loginSloganEn, String forgotUrl) {
    }
}
