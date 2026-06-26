package com.mandao.grc.modules.asset;

import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产台账 REST 端点：/api/assets。
 *
 * 隔离/actor：可见范围由 X-User 头决定（切面注入 visible_orgs），actor 取 X-User。
 * 合规属性筛查：GET /api/assets?filter=pi|crossBorder|chd|mlps 或 ?classification=SENSITIVE。
 * 状态机：ACTIVE → RETIRED。
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    /**
     * 列出资产，支持合规属性筛查：
     * ?filter=pi / crossBorder / chd / mlps，或 ?classification=PUBLIC|INTERNAL|SENSITIVE。
     */
    @GetMapping
    public List<Asset> list(@RequestParam(required = false) String filter,
                            @RequestParam(required = false) AssetClassification classification) {
        if (classification != null) {
            return service.listByClassification(classification);
        }
        if (filter == null) {
            return service.list();
        }
        return switch (filter) {
            case "pi" -> service.listContainingPi();
            case "crossBorder" -> service.listCrossBorder();
            case "chd" -> service.listContainingChd();
            case "mlps" -> service.listMlpsFiled();
            default -> service.list();
        };
    }

    @GetMapping("/{id}")
    public Asset get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @RequiresPermission("org")
    public Asset register(@RequestBody AssetRequest req,
                          @RequestHeader(value = "X-User", required = false) String user) {
        return service.register(req.orgId(), req.name(), req.assetType(), req.owner(),
                req.classification(), req.containsPi(), req.crossBorder(), req.mlpsFiled(),
                req.containsChd(), req.criticality(), actor(user));
    }

    @PutMapping("/{id}")
    @RequiresPermission("org")
    public Asset update(@PathVariable Long id, @RequestBody AssetRequest req,
                        @RequestHeader(value = "X-User", required = false) String user) {
        return service.update(id, req.name(), req.assetType(), req.owner(),
                req.classification(), req.containsPi(), req.crossBorder(), req.mlpsFiled(),
                req.containsChd(), req.criticality(), actor(user));
    }

    @PostMapping("/{id}/retire")
    @RequiresPermission("org")
    public Asset retire(@PathVariable Long id,
                        @RequestHeader(value = "X-User", required = false) String user) {
        return service.retire(id, actor(user));
    }

    private String actor(String user) {
        return (user == null || user.isBlank()) ? "anonymous" : user;
    }

    /** 资产登记/更新请求体（含资产合规属性 CR-002）。 */
    public record AssetRequest(Long orgId, String name, String assetType, String owner,
                               AssetClassification classification, boolean containsPi, boolean crossBorder,
                               boolean mlpsFiled, boolean containsChd, String criticality) {
    }
}
