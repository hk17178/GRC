package com.mandao.grc.modules.asset;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 资产台账业务服务（M6 资产台账，含资产合规属性 CR-002）。
 *
 * 隔离：方法 @Transactional 且位于 com.mandao.grc.modules 包 → {@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 在事务内自动注入 app.visible_orgs，RLS 裁剪数据并校验写入（WITH CHECK，V9 已为 asset 建）。
 *
 * 状态机：ACTIVE → RETIRED；非法流转抛 {@link IllegalStateException}。
 *
 * 留痕：登记/变更/停用后 {@link HashChainService#append} 写入按 org 分链的防篡改哈希链（entity="ASSET:{id}"）。
 *
 * 合规属性筛查（containsPi/crossBorder/containsChd/mlpsFiled/classification）由 Repository 派生查询提供，
 * 同样受 RLS 裁剪，仅返回当前可见组织内匹配的资产。
 *
 * 设计依据：需求文档 M6、CR-002、D1-2、D1-3 §5.1/§8、D2-5。
 */
@Service
public class AssetService {

    private final AssetRepository repository;
    private final HashChainService hashChainService;

    public AssetService(AssetRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Asset> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Asset get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资产不存在或不可见：id=" + id));
    }

    // ---------- 合规属性筛查 ----------

    /** 查含个人信息（PI）的资产。 */
    @Transactional(readOnly = true)
    public List<Asset> listContainingPi() {
        return repository.findByContainsPiTrue();
    }

    /** 查涉及数据跨境的资产。 */
    @Transactional(readOnly = true)
    public List<Asset> listCrossBorder() {
        return repository.findByCrossBorderTrue();
    }

    /** 查含持卡人数据（CHD）的资产。 */
    @Transactional(readOnly = true)
    public List<Asset> listContainingChd() {
        return repository.findByContainsChdTrue();
    }

    /** 查已等保备案（MLPS）的资产。 */
    @Transactional(readOnly = true)
    public List<Asset> listMlpsFiled() {
        return repository.findByMlpsFiledTrue();
    }

    /** 按分类分级筛查。 */
    @Transactional(readOnly = true)
    public List<Asset> listByClassification(AssetClassification classification) {
        return repository.findByClassification(classification);
    }

    // ---------- 写操作 ----------

    /**
     * 登记资产（ACTIVE 态，含合规属性）。
     *
     * @param orgId 归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param actor 操作人（用于留痕）
     */
    @Transactional
    public Asset register(Long orgId, String name, String assetType, String owner,
                          AssetClassification classification, boolean containsPi, boolean crossBorder,
                          boolean mlpsFiled, boolean containsChd, String criticality, String actor) {
        Asset a = new Asset(orgId, name, assetType, owner, classification,
                containsPi, crossBorder, mlpsFiled, containsChd, criticality);
        Asset saved = repository.save(a);
        appendLog(saved, "ASSET_REGISTER", actor,
                "登记资产 name=" + name + " type=" + assetType
                        + " class=" + saved.getClassification() + " pi=" + containsPi
                        + " crossBorder=" + crossBorder + " mlps=" + mlpsFiled + " chd=" + containsChd);
        return saved;
    }

    /**
     * 更新资产基础信息与合规属性（仅 ACTIVE 资产可改；RETIRED 资产不可再编辑）。
     */
    @Transactional
    public Asset update(Long id, String name, String assetType, String owner,
                        AssetClassification classification, boolean containsPi, boolean crossBorder,
                        boolean mlpsFiled, boolean containsChd, String criticality, String actor) {
        Asset a = get(id);
        if (a.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalStateException(
                    "资产已停用不可编辑：id=" + id + " 当前状态=" + a.getStatus());
        }
        if (name != null) {
            a.setName(name);
        }
        a.setAssetType(assetType);
        a.setOwner(owner);
        if (classification != null) {
            a.setClassification(classification);
        }
        a.setContainsPi(containsPi);
        a.setCrossBorder(crossBorder);
        a.setMlpsFiled(mlpsFiled);
        a.setContainsChd(containsChd);
        a.setCriticality(criticality);
        Asset saved = repository.save(a);
        appendLog(saved, "ASSET_UPDATE", actor,
                "更新资产合规属性 class=" + saved.getClassification() + " pi=" + containsPi
                        + " crossBorder=" + crossBorder + " mlps=" + mlpsFiled + " chd=" + containsChd);
        return saved;
    }

    /** 停用资产：ACTIVE → RETIRED（终态）。 */
    @Transactional
    public Asset retire(Long id, String actor) {
        Asset a = get(id);
        transition(a, AssetStatus.ACTIVE, AssetStatus.RETIRED);
        Asset saved = repository.save(a);
        appendLog(saved, "ASSET_RETIRE", actor, "停用资产");
        return saved;
    }

    // ---------- 内部辅助 ----------

    private void transition(Asset a, AssetStatus expectedFrom, AssetStatus to) {
        if (a.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：资产 id=" + a.getId()
                            + " 当前状态=" + a.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        a.setStatus(to);
    }

    private void appendLog(Asset a, String action, String actor, String detail) {
        hashChainService.append(a.getOrgId(), action, actor, "ASSET:" + a.getId(), detail);
    }
}
