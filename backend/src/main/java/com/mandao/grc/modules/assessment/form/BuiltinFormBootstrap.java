package com.mandao.grc.modules.assessment.form;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.assessment.AssessmentTemplate;
import com.mandao.grc.modules.assessment.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置表单启动引导（UAT 四轮 #3：内置模板开箱即用）。
 *
 * 应用就绪后，对集团（org 1）平台内置模板逐一检查：无 ACTIVE 报告表单的，
 * 用 {@link BuiltinRiskFormGenerator} 生成标准 GB/T 20984 章节 docx → 解析 → 启用。
 * 幂等（已有 ACTIVE 跳过）；单模板失败不阻断启动（记日志）。
 *
 * 注意：全部经 Service 层（@Transactional + OrgScopeAspect 注入 visible_orgs）访问——
 * 裸调 Repository 不触发切面，RLS 下会静默查空。
 */
@Component
public class BuiltinFormBootstrap {

    private static final Logger log = LoggerFactory.getLogger(BuiltinFormBootstrap.class);

    private final TemplateService templateService;
    private final AssessmentFormService formService;
    private final BuiltinRiskFormGenerator generator;

    public BuiltinFormBootstrap(TemplateService templateService,
                                AssessmentFormService formService, BuiltinRiskFormGenerator generator) {
        this.templateService = templateService;
        this.formService = formService;
        this.generator = generator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        IsolationContext.set(List.of(1L));
        try {
            List<AssessmentTemplate> templates = templateService.list().stream()
                    .filter(t -> "platform".equals(t.getOwner()))
                    .toList();
            int created = 0;
            for (AssessmentTemplate t : templates) {
                try {
                    boolean hasActive = formService.listForms(t.getId()).stream()
                            .anyMatch(f -> "ACTIVE".equals(f.getStatus()));
                    if (hasActive) {
                        continue;
                    }
                    byte[] docx = generator.generate(t.getName().replace("模板", "").trim() + " ");
                    TemplateForm form = formService.uploadForm(t.getId(), "内置标准表单", docx);
                    formService.activate(form.getId());
                    created++;
                } catch (RuntimeException e) {
                    log.warn("builtin-form bootstrap failed for {}: {}", t.getCode(), e.getMessage());
                }
            }
            log.info("builtin-form bootstrap done: templates={}, installed={}", templates.size(), created);
        } finally {
            IsolationContext.clear();
        }
    }
}
