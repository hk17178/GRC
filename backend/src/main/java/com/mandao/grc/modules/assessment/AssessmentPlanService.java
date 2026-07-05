package com.mandao.grc.modules.assessment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 评估计划服务（需求 4.2.1）。
 *
 * 隔离：方法 @Transactional 且位于 modules 包，OrgScopeAspect 注入 visible_orgs，RLS 自动裁剪/校验
 * ——计划的增改必须经本服务（控制器直连仓储会因未注入会话变量被 RLS 拒绝）。
 */
@Service
public class AssessmentPlanService {

    private final AssessmentPlanRepository repo;
    private final AssessmentService assessmentService;
    private final AssessmentRepository assessmentRepository;

    public AssessmentPlanService(AssessmentPlanRepository repo, AssessmentService assessmentService,
                                 AssessmentRepository assessmentRepository) {
        this.repo = repo;
        this.assessmentService = assessmentService;
        this.assessmentRepository = assessmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AssessmentPlan> list() {
        return repo.findAllByOrderByPlannedDateAsc();
    }

    /** 新建计划（排期）。 */
    @Transactional
    public AssessmentPlan create(Long orgId, String title, String periodType, LocalDate plannedDate, Long templateId) {
        return repo.save(new AssessmentPlan(orgId, title, periodType, plannedDate, templateId));
    }

    /** 启动计划：生成关联评估（带模板则进表单引擎），计划转 STARTED。 */
    @Transactional
    public AssessmentPlan start(Long id, String actor) {
        AssessmentPlan plan = get(id);
        if (!AssessmentPlan.PLANNED.equals(plan.getStatus())) {
            throw new IllegalStateException("仅排期(PLANNED)计划可启动，当前状态：" + plan.getStatus());
        }
        var a = assessmentService.create(plan.getOrgId(), plan.getTitle(), null,
                String.valueOf(plan.getPlannedDate() == null ? "" : plan.getPlannedDate().getYear()),
                plan.getTemplateId(), actor);
        // M2 深度包 B45：周期复评——同模板上一轮定稿评估的背景（范围/目的/依据/方法/准则/评估组）
        // 自动带入本轮草稿，评估人只需增量修订；起止日期属本轮周期，不复制。
        if (plan.getTemplateId() != null) {
            assessmentRepository.findFirstByTemplateIdAndStatusOrderByIdDesc(
                            plan.getTemplateId(), AssessmentStatus.COMPLETED)
                    .filter(prev -> !prev.getId().equals(a.getId()))
                    .ifPresent(prev -> assessmentService.setContext(a.getId(),
                            prev.getScope(), prev.getObjective(), prev.getBasis(), prev.getMethods(),
                            prev.getCriteria(), prev.getTeam(), null, null,
                            actor + "(复评带入上一轮背景)"));
        }
        plan.start(a.getId());
        return repo.save(plan);
    }

    /** 完成计划（仅 STARTED）。 */
    @Transactional
    public AssessmentPlan done(Long id) {
        AssessmentPlan plan = get(id);
        if (!AssessmentPlan.STARTED.equals(plan.getStatus())) {
            throw new IllegalStateException("仅已启动(STARTED)计划可完成，当前状态：" + plan.getStatus());
        }
        plan.markDone();
        return repo.save(plan);
    }

    private AssessmentPlan get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评估计划不存在或不可见：id=" + id));
    }
}
