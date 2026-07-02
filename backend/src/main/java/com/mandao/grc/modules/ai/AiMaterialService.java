package com.mandao.grc.modules.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.dashboard.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 生成报送/汇报材料（需求 7.5.1）。
 *
 * 把当前可见组织的真实合规统计（DashboardService.summary，受 RLS 裁剪）作为上下文，
 * 交给 LlmProvider 生成 监管报送稿初稿 / 管理层合规简报。产出标注：
 *  - 数据时点（dataAsOf）；
 *  - "AI 生成初稿，须人工复核后使用"（needsReview 恒 true，前端强制展示提示）。
 * 本地离线 Provider 会返回检索式说明并诚实标注；接入通用大模型后为真生成式初稿。
 */
@Service
public class AiMaterialService {

    private final LlmProvider llm;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;

    public AiMaterialService(LlmProvider llm, DashboardService dashboardService, ObjectMapper objectMapper) {
        this.llm = llm;
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成材料初稿。
     *
     * @param type FILING_DRAFT 监管报送稿 / MGMT_BRIEF 管理层合规简报
     */
    @Transactional(readOnly = true)
    public Material generate(String type) {
        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(dashboardService.summary());
        } catch (Exception e) {
            statsJson = "{}";
        }
        String ask;
        if ("MGMT_BRIEF".equals(type)) {
            ask = "请基于以下真实合规统计，为管理层撰写一份简明的合规态势简报（中文，分要点：总体态势/主要风险/整改与审计/建议，400 字内）。";
        } else {
            ask = "请基于以下真实合规统计，起草一份对监管机构的定期报送材料初稿（中文，正式公文口吻，含 总体情况/风险与整改/合规义务履行 三节，500 字内）。";
        }
        String question = ask + "\n【统计数据(JSON)】" + statsJson;
        String draft = llm.generate(question, List.of("当前合规统计：" + statsJson));
        return new Material(type, draft, OffsetDateTime.now().toString(), true, llm.name());
    }

    /** 生成结果：草稿/数据时点/须人工复核标记/提供方。 */
    public record Material(String type, String draft, String dataAsOf, boolean needsReview, String provider) {
    }
}
