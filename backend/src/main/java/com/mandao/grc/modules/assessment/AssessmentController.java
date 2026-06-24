package com.mandao.grc.modules.assessment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 演示端点：GET /api/assessments
 * 携带请求头 X-User: pay_user  → 仅见支付子公司数据
 *          X-User: cf_user   → 仅见消费金融数据
 *          X-User: group_admin → 见全集团
 *          无 X-User          → 默认拒绝（空）
 */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Assessment> list() {
        return service.list();
    }
}
