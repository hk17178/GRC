package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.rbac.RequiresPermission;
import com.mandao.grc.modules.control.ControlFramework;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评估模板库 REST 端点：/api/assessment-templates。
 *
 * 隔离：可见范围由请求经 {@link com.mandao.grc.common.isolation.IsolationFilter} 解析的 X-User 头决定；
 * actor 取 X-User，缺省 anonymous。
 */
@RestController
@RequestMapping("/api/assessment-templates")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    /** 列出当前主体可见组织范围内的模板。 */
    @GetMapping
    public List<AssessmentTemplate> list() {
        return service.list();
    }

    /** 取单个模板。 */
    @GetMapping("/{id}")
    public AssessmentTemplate get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 按序列出某模板的检查项。 */
    @GetMapping("/{id}/items")
    public List<AssessmentTemplateItem> items(@PathVariable Long id) {
        return service.listItems(id);
    }

    /** 定义模板。 */
    @PostMapping
    @RequiresPermission("risk")
    public AssessmentTemplate create(@RequestBody CreateTemplateRequest req,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.create(req.orgId(), req.code(), req.name(), req.framework(),
                req.description(), req.owner(), actor(user));
    }

    /** 追加模板检查项。 */
    @PostMapping("/{id}/items")
    @RequiresPermission("risk")
    public AssessmentTemplateItem addItem(@PathVariable Long id,
                                          @RequestBody AddItemRequest req,
                                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.addItem(id, req.controlId(), req.clause(), req.requirement(), actor(user));
    }

    /** 发布模板（DRAFT → PUBLISHED）。 */
    @PostMapping("/{id}/publish")
    @RequiresPermission("risk")
    public AssessmentTemplate publish(@PathVariable Long id,
                                      @RequestHeader(value = "X-User", required = false) String user) {
        return service.publish(id, actor(user));
    }

    /** 停用模板。 */
    @PostMapping("/{id}/retire")
    @RequiresPermission("risk")
    public AssessmentTemplate retire(@PathVariable Long id,
                                     @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    /** 删除模板（六轮 #1）：内置/被评估引用的不可删（409 口径由全局异常映射），自建/克隆可物理删除。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @RequiresPermission("risk")
    public void delete(@PathVariable Long id,
                       @RequestHeader(value = "X-User", required = false) String user) {
        service.delete(id, actor(user));
    }

    /** 克隆模板到目标组织（R4：内置脚手架克隆起步；新模板为 DRAFT）。 */
    @PostMapping("/{id}/clone")
    @RequiresPermission("risk")
    public AssessmentTemplate clone(@PathVariable Long id,
                                    @RequestBody CloneRequest req,
                                    @RequestHeader(value = "X-User", required = false) String user) {
        return service.clone(id, req.orgId(), req.code(), req.name(), actor(user));
    }

    /** 克隆请求体（R4）。 */
    public record CloneRequest(Long orgId, String code, String name) {
    }

    /** 实例化为一次评估（返回新建的评估）。 */
    @PostMapping("/{id}/instantiate")
    @RequiresPermission("risk")
    public Assessment instantiate(@PathVariable Long id,
                                  @RequestBody InstantiateRequest req,
                                  @RequestHeader(value = "X-User", required = false) String user) {
        return service.instantiate(id, req.title(), req.assessor(), req.period(), actor(user));
    }

    private String actor(String user) {
        return com.mandao.grc.common.auth.ActorResolver.resolve(user); // 七轮 7-4：登录态优先，消除 anonymous 归因
    }

    /** 新建模板请求体。 */
    public record CreateTemplateRequest(Long orgId, String code, String name, ControlFramework framework,
                                        String description, String owner) {
    }

    /** 追加模板项请求体。 */
    public record AddItemRequest(Long controlId, String clause, String requirement) {
    }

    /** 实例化请求体。 */
    public record InstantiateRequest(String title, String assessor, String period) {
    }
}
