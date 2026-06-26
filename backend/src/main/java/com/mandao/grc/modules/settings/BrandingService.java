package com.mandao.grc.modules.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录页与品牌配置服务（平台级单行）。
 *
 * 读：登录前公开可读（全局表无 RLS）。写：受控（控制器门控 "settings"），整表单次提交。
 */
@Service
public class BrandingService {

    private final BrandingConfigRepository repo;

    public BrandingService(BrandingConfigRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public BrandingConfig get() {
        // 迁移已插入 id=1；orElseGet 仅为防御
        return repo.findById(1L).orElseGet(() -> repo.save(new BrandingConfig()));
    }

    @Transactional
    public BrandingConfig update(String brandName, String brandSub, String logoText, String logoImg,
                                 String loginTitleZh, String loginTitleEn, String loginSloganZh,
                                 String loginSloganEn, String forgotUrl, String actor) {
        BrandingConfig c = get();
        c.apply(brandName, brandSub, logoText, logoImg, loginTitleZh, loginTitleEn,
                loginSloganZh, loginSloganEn, forgotUrl, actor);
        return repo.save(c);
    }
}
