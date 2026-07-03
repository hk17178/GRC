package com.mandao.grc;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.AssessmentTemplate;
import com.mandao.grc.modules.assessment.TemplateService;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R4 · 模板克隆集成测试：内置八体系脚手架（V45 种子在 org1）克隆到子组织——
 * 元数据/条款项全量复制，新模板为 DRAFT（可增改后发布）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class TemplateCloneTest {

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
    private TemplateService templateService;

    @AfterEach
    void clearContext() {
        IsolationContext.clear();
    }

    @Test
    void 克隆内置脚手架到子组织_条款项全量复制_新模板为草稿() {
        // 集团视角取 V45 内置等保模板（org1 种子）
        AssessmentTemplate mlps = asOrg(1L, () -> templateService.list().stream()
                .filter(t -> "TPL-MLPS".equals(t.getCode())).findFirst().orElseThrow());
        int srcItems = asOrg(1L, () -> templateService.listItems(mlps.getId())).size();

        // 集团管理员视角（可见域含子组织）克隆到 org12——RLS WITH CHECK 要求目标 org 在可见域内
        AssessmentTemplate copy = asOrgs(List.of(1L, 12L), () ->
                templateService.clone(mlps.getId(), 12L, "TPL-MLPS-PAY", "支付子公司等保自评模板", "c"));
        assertEquals("DRAFT", copy.getStatus().name(), "克隆件应为草稿");
        assertEquals(12L, copy.getOrgId());
        assertEquals(mlps.getFramework(), copy.getFramework());

        // 子组织视角可见且条款项全量复制
        int copiedItems = asOrg(12L, () -> templateService.listItems(copy.getId())).size();
        assertEquals(srcItems, copiedItems, "条款项应全量复制");
    }

    private <T> T asOrg(long orgId, Supplier<T> action) {
        return asOrgs(List.of(orgId), action);
    }

    private <T> T asOrgs(List<Long> orgIds, Supplier<T> action) {
        IsolationContext.set(orgIds);
        try {
            return action.get();
        } finally {
            IsolationContext.clear();
        }
    }
}
