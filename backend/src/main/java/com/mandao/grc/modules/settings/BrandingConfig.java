package com.mandao.grc.modules.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 登录页与品牌配置（平台级单行，id 固定为 1；全局、无 RLS，登录前可读）。
 *
 * 字段可空：为空时前端回退 i18n 默认文案。logo 优先用 {@code logoImg}（URL/data URI），否则 {@code logoText}。
 */
@Entity
@Table(name = "branding_config")
public class BrandingConfig {

    @Id
    private Long id = 1L;

    @Column(name = "brand_name", columnDefinition = "TEXT")
    private String brandName;

    @Column(name = "brand_sub", columnDefinition = "TEXT")
    private String brandSub;

    @Column(name = "logo_text", length = 8)
    private String logoText;

    @Column(name = "logo_img", columnDefinition = "TEXT")
    private String logoImg;

    @Column(name = "login_title_zh", columnDefinition = "TEXT")
    private String loginTitleZh;

    @Column(name = "login_title_en", columnDefinition = "TEXT")
    private String loginTitleEn;

    @Column(name = "login_slogan_zh", columnDefinition = "TEXT")
    private String loginSloganZh;

    @Column(name = "login_slogan_en", columnDefinition = "TEXT")
    private String loginSloganEn;

    @Column(name = "forgot_url", columnDefinition = "TEXT")
    private String forgotUrl;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected BrandingConfig() {
    }

    /** 整体更新（界面表单一次性提交全部字段）。 */
    public void apply(String brandName, String brandSub, String logoText, String logoImg,
                      String loginTitleZh, String loginTitleEn, String loginSloganZh, String loginSloganEn,
                      String forgotUrl, String actor) {
        this.brandName = brandName;
        this.brandSub = brandSub;
        this.logoText = logoText;
        this.logoImg = logoImg;
        this.loginTitleZh = loginTitleZh;
        this.loginTitleEn = loginTitleEn;
        this.loginSloganZh = loginSloganZh;
        this.loginSloganEn = loginSloganEn;
        this.forgotUrl = forgotUrl;
        this.updatedBy = actor;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getBrandName() { return brandName; }
    public String getBrandSub() { return brandSub; }
    public String getLogoText() { return logoText; }
    public String getLogoImg() { return logoImg; }
    public String getLoginTitleZh() { return loginTitleZh; }
    public String getLoginTitleEn() { return loginTitleEn; }
    public String getLoginSloganZh() { return loginSloganZh; }
    public String getLoginSloganEn() { return loginSloganEn; }
    public String getForgotUrl() { return forgotUrl; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
