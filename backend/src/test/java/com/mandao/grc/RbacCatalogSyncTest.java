package com.mandao.grc;

import com.mandao.grc.modules.rbac.ResourceCatalog;
import com.mandao.grc.modules.rbac.ResourceRepository;
import com.mandao.grc.modules.rbac.RbacResourceSync;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 资源目录自管理测试（增强③ R6，真实 PG）。验证：
 *  1) 启动后 resource 表已含目录全部资源（RbacBootstrap 同步生效）；
 *  2) upsertAll 幂等（再次同步不报错、条数不变）。
 *  context 能成功加载本身即证明"注解 code 全部已登记目录"的启动校验通过。
 *
 * 设计依据：用户增强诉求③ R6（新功能权限丝滑融入）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class RbacCatalogSyncTest {

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
    private ResourceRepository resourceRepo;
    @Autowired
    private RbacResourceSync sync;

    @Test
    void 启动后目录已同步入库() {
        long dbCount = resourceRepo.count();
        assertTrue(dbCount >= ResourceCatalog.ALL.size(),
                "resource 表应含目录全部资源，目录 " + ResourceCatalog.ALL.size() + "，库 " + dbCount);
        assertTrue(resourceRepo.findAll().stream().anyMatch(r -> "risk.create".equals(r.getCode())),
                "应含目录中的 risk.create");
    }

    @Test
    void 再次同步_幂等() {
        long before = resourceRepo.count();
        int n = sync.upsertAll();
        assertEquals(ResourceCatalog.ALL.size(), n, "应处理全部目录项");
        assertEquals(before, resourceRepo.count(), "幂等同步不应改变资源条数");
    }
}
