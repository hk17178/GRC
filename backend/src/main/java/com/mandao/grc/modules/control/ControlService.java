package com.mandao.grc.modules.control;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 统一控件库业务服务（M2 统一控件库）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，
 * {@link com.mandao.grc.common.isolation.OrgScopeAspect} 在事务内注入 app.visible_orgs，RLS 裁剪 + 写校验；
 * 关键操作经 {@link HashChainService#append} 入按 org 分链的防篡改哈希链。
 *
 * 价值：一个控制项映射多个框架条款（一次定义、多框架复用）；停用保留历史不再新引用。
 *
 * 设计依据：D1-2 数据模型（统一控件库/框架映射）、D1-7（风险评估·统一控件库）、D2-5。
 */
@Service
public class ControlService {

    private final ControlRepository controlRepository;
    private final ControlFrameworkRefRepository refRepository;
    private final HashChainService hashChainService;

    public ControlService(ControlRepository controlRepository,
                          ControlFrameworkRefRepository refRepository,
                          HashChainService hashChainService) {
        this.controlRepository = controlRepository;
        this.refRepository = refRepository;
        this.hashChainService = hashChainService;
    }

    /** 列出当前可见组织范围内的全部控制项。 */
    @Transactional(readOnly = true)
    public List<Control> list() {
        return controlRepository.findAll();
    }

    /** 按 id 取控制项（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public Control get(Long id) {
        return controlRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("控制项不存在或不可见：id=" + id));
    }

    /** 列出某控制项的框架映射。 */
    @Transactional(readOnly = true)
    public List<ControlFrameworkRef> listMappings(Long controlId) {
        get(controlId); // 触发可见性校验
        return refRepository.findByControlId(controlId);
    }

    /**
     * 定义一个控制项。
     *
     * @param orgId 归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     */
    @Transactional
    public Control create(Long orgId, String code, String name, String description,
                          String domain, String owner, String actor) {
        Control control = new Control(orgId, code, name, description, domain, owner);
        Control saved = controlRepository.save(control);
        hashChainService.append(orgId, "CONTROL_CREATE", actor, "CONTROL:" + saved.getId(),
                "定义控制项 code=" + code + " 域=" + domain);
        return saved;
    }

    /**
     * 为控制项新增一条框架映射（同框架同条款不重复登记）。仅 ACTIVE 控制项可加映射。
     */
    @Transactional
    public ControlFrameworkRef addMapping(Long controlId, ControlFramework framework, String clause, String actor) {
        Control control = get(controlId);
        if (control.getStatus() != ControlStatus.ACTIVE) {
            throw new IllegalStateException("仅启用(ACTIVE)控制项可新增框架映射，当前状态：" + control.getStatus());
        }
        if (refRepository.existsByControlIdAndFrameworkAndClause(controlId, framework, clause)) {
            throw new IllegalStateException("该控制项已存在映射 " + framework + " " + clause);
        }
        ControlFrameworkRef ref = new ControlFrameworkRef(control.getOrgId(), controlId, framework, clause);
        ControlFrameworkRef saved = refRepository.save(ref);
        hashChainService.append(control.getOrgId(), "CONTROL_MAP", actor, "CONTROL:" + controlId,
                "映射到框架 " + framework + " 条款 " + clause);
        return saved;
    }

    /** 停用控制项：ACTIVE → RETIRED（保留历史，不再新引用）。 */
    @Transactional
    public Control retire(Long controlId, String actor) {
        Control control = get(controlId);
        if (control.getStatus() != ControlStatus.ACTIVE) {
            throw new IllegalStateException("仅启用(ACTIVE)控制项可停用，当前状态：" + control.getStatus());
        }
        control.retire();
        Control saved = controlRepository.save(control);
        hashChainService.append(control.getOrgId(), "CONTROL_RETIRE", actor, "CONTROL:" + controlId, "停用控制项");
        return saved;
    }
}
