package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评估项 REST 端点：/api/assessment-items。
 *
 * 隔离：可见范围由 X-User 头决定；actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/assessment-items")
public class AssessmentItemController {

    private final AssessmentItemService service;

    public AssessmentItemController(AssessmentItemService service) {
        this.service = service;
    }

    /** 列出某评估的评估项。 */
    @GetMapping
    @RequiresPermission("risk")
    public List<AssessmentItem> listByAssessment(@RequestParam Long assessmentId) {
        return service.listByAssessment(assessmentId);
    }

    /** 取单个评估项。 */
    @GetMapping("/{id}")
    @RequiresPermission("risk")
    public AssessmentItem get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 评估单个评估项（回写符合性结论）。 */
    @PostMapping("/{id}/assess")
    @RequiresPermission("risk")
    public AssessmentItem assess(@PathVariable Long id,
                                 @RequestBody AssessRequest req,
                                 @RequestHeader(value = "X-User", required = false) String user) {
        return service.assess(id, req.result(), req.conclusion(),
                com.mandao.grc.common.auth.ActorResolver.resolve(user)); // 七轮 7-4：登录态优先
    }

    /** 评估请求体。 */
    public record AssessRequest(AssessmentItemResult result, String conclusion) {
    }
}
