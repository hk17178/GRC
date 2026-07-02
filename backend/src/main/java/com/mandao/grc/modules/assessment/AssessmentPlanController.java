package com.mandao.grc.modules.assessment;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 评估计划管理端点：/api/assessment-plans（需求 4.2.1）。
 *
 * 排期(PLANNED) → 启动(STARTED，生成关联评估并带入模板表单引擎) → 完成(DONE)。
 * 业务与隔离逻辑在 {@link AssessmentPlanService}（@Transactional 保证切面注入 visible_orgs）。写门控 "risk"。
 */
@RestController
@RequestMapping("/api/assessment-plans")
public class AssessmentPlanController {

    private final AssessmentPlanService service;

    public AssessmentPlanController(AssessmentPlanService service) {
        this.service = service;
    }

    /** 计划清单（按计划日期升序）。 */
    @GetMapping
    public List<AssessmentPlan> list() {
        return service.list();
    }

    /** 新建计划（排期）。 */
    @PostMapping
    @RequiresPermission("risk")
    public AssessmentPlan create(@RequestBody PlanRequest req) {
        return service.create(req.orgId(), req.title(), req.periodType(), req.plannedDate(), req.templateId());
    }

    /** 启动计划：生成关联评估，计划转 STARTED。 */
    @PostMapping("/{id}/start")
    @RequiresPermission("risk")
    public AssessmentPlan start(@PathVariable Long id) {
        return service.start(id, actor());
    }

    /** 完成计划。 */
    @PostMapping("/{id}/done")
    @RequiresPermission("risk")
    public AssessmentPlan done(@PathVariable Long id) {
        return service.done(id);
    }

    private String actor() {
        String u = CurrentUserContext.get();
        return u == null || u.isBlank() ? "anonymous" : u;
    }

    /** 计划请求体。 */
    public record PlanRequest(Long orgId, String title, String periodType, LocalDate plannedDate, Long templateId) {
    }
}
