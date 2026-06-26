package com.mandao.grc;

import com.mandao.grc.modules.settings.BrandingConfig;
import com.mandao.grc.modules.settings.BrandingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 登录页与品牌配置测试（真实 PG，全局单行表）。验证：
 *  1) 初始单行存在（迁移已插入 id=1）；
 *  2) 更新后整表回读一致（品牌名/标题/Logo/忘记密码链接）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class BrandingTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc")
            .withUsername("grc_owner")
            .withPassword("owner_pw")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "grc_app");
        registry.add("spring.datasource.password", () -> "grc_app_pw");
        registry.add("spring.flyway.url", PG::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "grc_owner");
        registry.add("spring.flyway.password", () -> "owner_pw");
    }

    @Autowired
    private BrandingService service;

    @Test
    void 品牌配置_初始存在并可整表更新回读() {
        assertNotNull(service.get(), "迁移应已插入单行 id=1");

        service.update("曼道集团 GRC", "治理·风险·合规", "曼", null,
                "统一治理\n一处掌控", "Unified Governance", "合规即基础设施", "Compliance as Infrastructure",
                "https://help.example/forgot", "admin");

        BrandingConfig c = service.get();
        assertEquals("曼道集团 GRC", c.getBrandName());
        assertEquals("曼", c.getLogoText());
        assertEquals("统一治理\n一处掌控", c.getLoginTitleZh());
        assertEquals("https://help.example/forgot", c.getForgotUrl());
        assertEquals("admin", c.getUpdatedBy());
    }
}
