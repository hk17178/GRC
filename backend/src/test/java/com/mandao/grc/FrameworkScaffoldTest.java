package com.mandao.grc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八体系评估模板脚手架种子冒烟测试（V45）。验证迁移后：
 *  1) 集团组织（org 1）内置 8 个 PUBLISHED 模板（等保/ISO27001/PCI/PBOC/27701/20000/22301/PIPL）；
 *  2) 每个模板都带条款项（克隆起步的最小内容）。
 * 直连 owner 校验（种子为数据基线，不依赖业务服务）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "grc.scheduler.enabled=false")
@Testcontainers
class FrameworkScaffoldTest {

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

    @Test
    void 八体系模板与条款项均已种入() throws Exception {
        try (Connection owner = DriverManager.getConnection(PG.getJdbcUrl(), "grc_owner", "owner_pw");
             Statement s = owner.createStatement()) {
            ResultSet rs = s.executeQuery(
                    "SELECT count(*) FROM assessment_template WHERE org_id = 1 AND owner = 'platform' AND status = 'PUBLISHED'");
            rs.next();
            assertEquals(8, rs.getInt(1), "应内置 8 个体系模板");

            ResultSet fw = s.executeQuery(
                    "SELECT count(DISTINCT framework) FROM assessment_template WHERE org_id = 1 AND owner = 'platform'");
            fw.next();
            assertEquals(8, fw.getInt(1), "8 个模板应覆盖 8 个不同体系");

            ResultSet items = s.executeQuery(
                    "SELECT count(*) FROM assessment_template t WHERE t.org_id = 1 AND t.owner = 'platform' "
                            + "AND NOT EXISTS (SELECT 1 FROM assessment_template_item i WHERE i.template_id = t.id)");
            items.next();
            assertTrue(items.getInt(1) == 0, "每个脚手架模板都应带条款项");
        }
    }
}
