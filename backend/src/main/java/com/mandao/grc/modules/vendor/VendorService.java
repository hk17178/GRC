package com.mandao.grc.modules.vendor;

import com.mandao.grc.modules.assessment.RiskLevel;
import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 第三方供应商业务服务（准入 / 评估 / 监测）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 状态机 ONBOARDING → ACTIVE ⇄ SUSPENDED → TERMINATED。
 * ===== 准入门控（红线）=====
 * 启用 {@link #activate}（ONBOARDING → ACTIVE）须该供应商已完成【至少一次风险评估】，否则禁止启用。
 *
 * 设计依据：需求文档 M·第三方供应商（准入/评估/监测）、D2-5。
 */
@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final VendorAssessmentRepository assessmentRepository;
    private final HashChainService hashChainService;

    public VendorService(VendorRepository vendorRepository,
                         VendorAssessmentRepository assessmentRepository,
                         HashChainService hashChainService) {
        this.vendorRepository = vendorRepository;
        this.assessmentRepository = assessmentRepository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<Vendor> list() {
        return vendorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Vendor get(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在或不可见：id=" + id));
    }

    @Transactional(readOnly = true)
    public List<VendorAssessment> listAssessments(Long vendorId) {
        get(vendorId); // 可见性校验
        return assessmentRepository.findByVendorIdOrderByIdDesc(vendorId);
    }

    /** 登记供应商（ONBOARDING）。 */
    @Transactional
    public Vendor register(Long orgId, String code, String name, String category,
                           String contact, String criticality, String actor) {
        Vendor saved = vendorRepository.save(new Vendor(orgId, code, name, category, contact, criticality));
        hashChainService.append(orgId, "VENDOR_REGISTER", actor, "VENDOR:" + saved.getId(),
                "登记供应商 code=" + code + " 类别=" + category);
        return saved;
    }

    /** 评估供应商：登记一次评估并回写最近风险等级；留痕。 */
    @Transactional
    public VendorAssessment assess(Long vendorId, RiskLevel riskLevel, Integer score,
                                   String conclusion, String actor) {
        Vendor v = get(vendorId);
        VendorAssessment saved = assessmentRepository.save(
                new VendorAssessment(v.getOrgId(), vendorId, riskLevel, score, actor, conclusion));
        v.setRiskLevel(riskLevel);
        vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_ASSESS", actor, "VENDOR:" + vendorId,
                "供应商评估 风险=" + riskLevel + " 得分=" + score);
        return saved;
    }

    /**
     * 启用供应商：ONBOARDING → ACTIVE。
     * 【准入门控红线】须已有至少一次风险评估，否则抛 {@link IllegalStateException}。
     */
    @Transactional
    public Vendor activate(Long vendorId, String actor) {
        Vendor v = get(vendorId);
        if (v.getStatus() != VendorStatus.ONBOARDING) {
            throw new IllegalStateException("仅准入中(ONBOARDING)供应商可启用，当前状态：" + v.getStatus());
        }
        if (!assessmentRepository.existsByVendorId(vendorId)) {
            throw new IllegalStateException("供应商 id=" + vendorId + " 未完成风险评估，不得启用（准入门控）");
        }
        v.setStatus(VendorStatus.ACTIVE);
        Vendor saved = vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_ACTIVATE", actor, "VENDOR:" + vendorId, "启用供应商（已过准入评估）");
        return saved;
    }

    /** 暂停供应商：ACTIVE → SUSPENDED（监测发现问题）。 */
    @Transactional
    public Vendor suspend(Long vendorId, String reason, String actor) {
        Vendor v = get(vendorId);
        if (v.getStatus() != VendorStatus.ACTIVE) {
            throw new IllegalStateException("仅合作中(ACTIVE)供应商可暂停，当前状态：" + v.getStatus());
        }
        v.setStatus(VendorStatus.SUSPENDED);
        Vendor saved = vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_SUSPEND", actor, "VENDOR:" + vendorId,
                "暂停供应商：" + (reason == null ? "" : reason));
        return saved;
    }

    /** 恢复供应商：SUSPENDED → ACTIVE。 */
    @Transactional
    public Vendor reactivate(Long vendorId, String actor) {
        Vendor v = get(vendorId);
        if (v.getStatus() != VendorStatus.SUSPENDED) {
            throw new IllegalStateException("仅已暂停(SUSPENDED)供应商可恢复，当前状态：" + v.getStatus());
        }
        v.setStatus(VendorStatus.ACTIVE);
        Vendor saved = vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_REACTIVATE", actor, "VENDOR:" + vendorId, "恢复供应商");
        return saved;
    }

    /** 终止供应商：任意非终止态 → TERMINATED（终态）。 */
    @Transactional
    public Vendor terminate(Long vendorId, String reason, String actor) {
        Vendor v = get(vendorId);
        if (v.getStatus() == VendorStatus.TERMINATED) {
            throw new IllegalStateException("供应商已终止，无需重复终止");
        }
        v.setStatus(VendorStatus.TERMINATED);
        Vendor saved = vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_TERMINATE", actor, "VENDOR:" + vendorId,
                "终止供应商：" + (reason == null ? "" : reason));
        return saved;
    }
}
