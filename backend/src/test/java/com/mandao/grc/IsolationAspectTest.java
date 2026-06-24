package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.Assessment;
import com.mandao.grc.modules.assessment.AssessmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切面级隔离集成测试：验证 {@code OrgScopeAspect} 在【业务代码不手动注入】的情况下，
 * 仅凭 @Transactional 即自动完成组织隔离。
 *
 * 与 {@link IsolationRlsTest}（纯 JDBC 验证 DB 层 RLS）互补：本测试验证"应用切面 + RLS"
 * 的整链路自动隔离。应用数据源以非 owner 角色 grc_app 连接，故 RLS 生效。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class IsolationAspectTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("grc")
            .withUsername("grc_owner")              // 容器默认用户＝owner，跑迁移
            .withPassword("owner_pw")
            .withInitScript("testcontainers-init.sql"); // 迁移前创建 grc_app 角色

    /** 应用数据源用 grc_app（受 RLS）；Flyway 用 grc_owner（owner，灌迁移与种子）。 */
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
    private AssessmentService assessmentService;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 切面自动隔离_支付用户仅见本子公司() {
        IsolationContext.set(List.of(12L));
        List<Assessment> result = assessmentService.list();   // 未手动注入，全靠切面
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> a.getOrgId().equals(12L)));
    }

    @Test
    void 切面自动隔离_消金用户仅见本子公司() {
        IsolationContext.set(List.of(13L));
        List<Assessment> result = assessmentService.list();
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> a.getOrgId().equals(13L)));
    }

    @Test
    void 切面自动隔离_集团可见全集团() {
        IsolationContext.set(List.of(1L, 12L, 13L));
        assertEquals(4, assessmentService.list().size());
    }

    @Test
    void 红线_无隔离上下文默认拒绝() {
        // 不设置上下文 → 切面注入 '-1' → RLS 命中 0 行
        assertEquals(0, assessmentService.list().size());
    }
}
