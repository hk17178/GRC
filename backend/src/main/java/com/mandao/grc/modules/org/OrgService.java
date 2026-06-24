package com.mandao.grc.modules.org;

import com.mandao.grc.modules.audit.HashChainService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织树管理服务（M6 组织管理）。
 *
 * 与其它业务模块不同：本服务直接增改 V1 既有 org 表（组织字典）。
 * org 表【无 RLS】（供 {@link com.mandao.grc.common.isolation.VisibleOrgsService} 计算可见域），
 * 故本服务对 org 的读写不受 visible_orgs 裁剪——这是有意为之，不要给 org 加 RLS。
 * 本类位于 com.mandao.grc.modules 包，方法 @Transactional 仍会被 OrgScopeAspect 注入 visible_orgs，
 * 但因 org 无策略，注入对 org 读写无影响；而留痕（HashChainService 写 operation_log）受 RLS，
 * 故按【父组织 org_id】留痕（父必在当前可见域内，满足 operation_log 的 WITH CHECK）。
 *
 * 建子组织：org.id 为普通 BIGINT（无序列/默认值），取 MAX(id)+1 生成新 id（advisory 锁串行化，
 * 避免并发取到相同 id）；物化路径 path = 父path + '/' + 新id（与 V1 种子 '/1','/1/12' 一致）。
 *
 * 原生查询统一用 CAST（禁 `::`），与 M1~M3 范式一致。
 *
 * 设计依据：需求文档 M6 组织与资产、D1-2、D1-3 §5.1、D2-5。
 */
@Service
public class OrgService {

    @PersistenceContext
    private EntityManager em;

    private final HashChainService hashChainService;

    public OrgService(HashChainService hashChainService) {
        this.hashChainService = hashChainService;
    }

    /** 取单个组织（不存在返回 null）。 */
    @Transactional(readOnly = true)
    public OrgNode get(Long id) {
        List<OrgNode> rows = selectNodes("WHERE id = :id", "id", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 列出某根组织下的子树（含根自身），按物化路径前缀展开、按 path 排序。
     *
     * @param rootId 子树根组织 id（如 1=集团 返回全树，12=支付子公司 返回其子树）
     */
    @Transactional(readOnly = true)
    public List<OrgNode> listTree(Long rootId) {
        OrgNode root = get(rootId);
        if (root == null) {
            throw new IllegalArgumentException("组织不存在：id=" + rootId);
        }
        // path = root.path（根自身）或 path LIKE root.path + '/%'（子孙）
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, parent_id, org_type, code, name, path FROM org "
                                + "WHERE path = :p OR path LIKE :prefix ORDER BY path")
                .setParameter("p", root.path())
                .setParameter("prefix", root.path() + "/%")
                .getResultList();
        return rows.stream().map(OrgService::toNode).toList();
    }

    /**
     * 在父组织下新建子组织（DEPT/SUBSIDIARY 等）。
     *
     * 步骤：advisory 锁串行化 → 校验父存在、code 唯一 → 取 MAX(id)+1 生成新 id →
     * 插入 org（path 先占位）→ 回填 path = 父path + '/' + 新id → 按父 org 留痕。
     *
     * @param parentId 父组织 id（必须存在）
     * @param code     组织编码（全局唯一，命中重复抛 {@link DuplicateOrgCodeException}）
     * @param name     组织名称
     * @param orgType  组织类型（GROUP/SUBSIDIARY/DEPT）
     * @param actor    操作人（用于留痕）
     * @return 新建的子组织节点（含正确 path）
     */
    @Transactional
    public OrgNode createSubOrg(Long parentId, String code, String name, String orgType, String actor) {
        // 1) 全局 advisory 锁串行化 org 写入（命名空间 2000，区别于 HashChainService 的 1000）
        em.createNativeQuery("SELECT pg_advisory_xact_lock(2000, 0)").getResultList();

        // 2) 校验父组织存在
        OrgNode parent = get(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("父组织不存在：parentId=" + parentId);
        }

        // 3) 校验 code 唯一
        Number dup = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM org WHERE code = :code")
                .setParameter("code", code)
                .getSingleResult();
        if (dup.longValue() > 0) {
            throw new DuplicateOrgCodeException(code);
        }

        // 4) 生成新 id = MAX(id)+1（org.id 无序列，手动生成；已被 advisory 锁串行化）
        Number maxId = (Number) em.createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM org")
                .getSingleResult();
        long newId = maxId.longValue() + 1;
        String path = parent.path() + "/" + newId;

        // 5) 一步插入（path 直接计算好，无需两步回填）
        em.createNativeQuery(
                        "INSERT INTO org(id, parent_id, org_type, code, name, path) "
                                + "VALUES (:id, :pid, :type, :code, :name, :path)")
                .setParameter("id", newId)
                .setParameter("pid", parentId)
                .setParameter("type", orgType)
                .setParameter("code", code)
                .setParameter("name", name)
                .setParameter("path", path)
                .executeUpdate();

        // 6) 留痕：按【父 org】分链（父必在可见域内，满足 operation_log WITH CHECK）
        hashChainService.append(parentId, "ORG_CREATE_SUB", actor, "ORG:" + newId,
                "新建子组织 code=" + code + " name=" + name + " type=" + orgType + " path=" + path);

        return new OrgNode(newId, parentId, orgType, code, name, path);
    }

    // ---------- 内部辅助 ----------

    @SuppressWarnings("unchecked")
    private List<OrgNode> selectNodes(String whereClause, String paramName, Object paramValue) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, parent_id, org_type, code, name, path FROM org " + whereClause)
                .setParameter(paramName, paramValue)
                .getResultList();
        return rows.stream().map(OrgService::toNode).toList();
    }

    private static OrgNode toNode(Object[] r) {
        Long id = ((Number) r[0]).longValue();
        Long parentId = r[1] == null ? null : ((Number) r[1]).longValue();
        return new OrgNode(id, parentId, (String) r[2], (String) r[3], (String) r[4], (String) r[5]);
    }
}
