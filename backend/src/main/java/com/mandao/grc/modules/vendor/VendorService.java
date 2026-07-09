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
    private final VendorSlaRepository slaRepository;
    private final VendorIncidentRepository incidentRepository;
    private final HashChainService hashChainService;

    public VendorService(VendorRepository vendorRepository,
                         VendorAssessmentRepository assessmentRepository,
                         VendorSlaRepository slaRepository,
                         VendorIncidentRepository incidentRepository,
                         HashChainService hashChainService) {
        this.vendorRepository = vendorRepository;
        this.assessmentRepository = assessmentRepository;
        this.slaRepository = slaRepository;
        this.incidentRepository = incidentRepository;
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
        return assess(vendorId, riskLevel, score, conclusion, "ONBOARDING", null, actor);
    }

    /** 评估供应商（带评估类型）。 */
    @Transactional
    public VendorAssessment assess(Long vendorId, RiskLevel riskLevel, Integer score,
                                   String conclusion, String assessType, String actor) {
        return assess(vendorId, riskLevel, score, conclusion, assessType, null, actor);
    }

    /**
     * 评估供应商（带评估类型 + 评估依据）。评估依据记录"通过什么评估表单/标准/维度得出结果"，
     * 与后续可挂载的评估表单原件一起构成可溯的评估过程（需求 9.3.2 + 用户反馈：评估须有过程留存）。
     */
    @Transactional
    public VendorAssessment assess(Long vendorId, RiskLevel riskLevel, Integer score,
                                   String conclusion, String assessType, String basis, String actor) {
        Vendor v = get(vendorId);
        VendorAssessment saved = assessmentRepository.save(
                new VendorAssessment(v.getOrgId(), vendorId, riskLevel, score, actor, conclusion, assessType, basis));
        v.setRiskLevel(riskLevel);
        vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_ASSESS", actor, "VENDOR:" + vendorId,
                "供应商评估 风险=" + riskLevel + " 得分=" + score + (basis != null && !basis.isBlank() ? "（附依据）" : ""));
        return saved;
    }

    /** 上传某次评估的评估表单/报告原件（过程文档留存 + sha256 固化）。 */
    @Transactional
    public VendorAssessment uploadAssessmentDoc(Long assessmentId, String filename, byte[] bytes, String actor) {
        VendorAssessment a = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估记录不存在或不可见：id=" + assessmentId));
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String sha256 = sha256Hex(bytes);
        a.attachDocument(filename, sha256, bytes);
        VendorAssessment saved = assessmentRepository.save(a);
        hashChainService.append(a.getOrgId(), "VENDOR_ASSESS_DOC", actor, "VENDOR:" + a.getVendorId(),
                "上传供应商评估原件 " + filename + "（sha256=" + sha256.substring(0, 12) + "…）");
        return saved;
    }

    /** 下载某次评估的原件。 */
    @Transactional(readOnly = true)
    public VendorAssessment getAssessmentWithDoc(Long assessmentId) {
        VendorAssessment a = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估记录不存在或不可见：id=" + assessmentId));
        if (a.getDocBytes() == null) {
            throw new IllegalArgumentException("该评估未上传原件");
        }
        return a;
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

    // ---------- M7 深度：技术安全/DPA / SLA 跟踪 / 事件触发复评 ----------

    /** 更新技术安全/DPA 合规属性（需求 9.3.1 两卡片）。 */
    @Transactional
    public Vendor updateCompliance(Long vendorId, String dataResidency, boolean pciScope, String certifications,
                                   boolean dpaSigned, boolean crossBorder, String subProcessing, String actor) {
        Vendor v = get(vendorId);
        v.updateCompliance(dataResidency, pciScope, certifications, dpaSigned, crossBorder, subProcessing);
        Vendor saved = vendorRepository.save(v);
        hashChainService.append(v.getOrgId(), "VENDOR_COMPLIANCE", actor, "VENDOR:" + vendorId,
                "更新技术安全/DPA 属性 驻留=" + dataResidency + " DPA=" + dpaSigned);
        return saved;
    }

    /** 某供应商的 SLA 项。 */
    @Transactional(readOnly = true)
    public List<VendorSla> listSla(Long vendorId) {
        return slaRepository.findByVendorIdOrderByIdAsc(vendorId);
    }

    /** 全部 SLA（跟踪页汇总视图，按到期升序）。 */
    @Transactional(readOnly = true)
    public List<VendorSla> listAllSla() {
        return slaRepository.findAllByOrderByDueDateAsc();
    }

    /** 新增 SLA 项。 */
    @Transactional
    public VendorSla addSla(Long vendorId, String item, String target, String actual,
                            java.time.LocalDate dueDate, boolean met, String actor) {
        Vendor v = get(vendorId);
        VendorSla saved = slaRepository.save(new VendorSla(v.getOrgId(), vendorId, item, target, actual, dueDate, met));
        hashChainService.append(v.getOrgId(), "VENDOR_SLA_ADD", actor, "VENDOR:" + vendorId,
                "新增 SLA 项 " + item + " 目标=" + target);
        return saved;
    }

    /** 回填 SLA 实际值与达标状态。 */
    @Transactional
    public VendorSla trackSla(Long slaId, String actual, boolean met, String actor) {
        VendorSla sla = slaRepository.findById(slaId)
                .orElseThrow(() -> new IllegalArgumentException("SLA 项不存在或不可见：id=" + slaId));
        sla.track(actual, met);
        VendorSla saved = slaRepository.save(sla);
        hashChainService.append(sla.getOrgId(), "VENDOR_SLA_TRACK", actor, "VENDOR:" + sla.getVendorId(),
                "SLA " + sla.getItem() + " 实际=" + actual + " 达标=" + met);
        return saved;
    }

    /** 全部外部事件（事件触发复评页）。 */
    @Transactional(readOnly = true)
    public List<VendorIncident> listIncidents() {
        return incidentRepository.findAllByOrderByIdDesc();
    }

    /** 登记外部负面事件（OPEN）。 */
    @Transactional
    public VendorIncident reportIncident(Long vendorId, String event, String source, String riskLevel, String actor) {
        Vendor v = get(vendorId);
        VendorIncident saved = incidentRepository.save(new VendorIncident(v.getOrgId(), vendorId, event, source, riskLevel));
        hashChainService.append(v.getOrgId(), "VENDOR_INCIDENT", actor, "VENDOR:" + vendorId,
                "登记外部事件：" + event + "（来源 " + source + "）");
        return saved;
    }

    /**
     * 事件触发复评：对该事件的供应商登记一次 EVENT 类型评估，事件转 REASSESSING。
     * 复评结论回写供应商最近风险等级（复用 assess）。
     */
    @Transactional
    public VendorIncident triggerReassess(Long incidentId, RiskLevel riskLevel, Integer score,
                                          String conclusion, String actor) {
        VendorIncident inc = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("事件不存在或不可见：id=" + incidentId));
        if (!VendorIncident.OPEN.equals(inc.getStatus())) {
            throw new IllegalStateException("仅 OPEN 事件可触发复评，当前状态：" + inc.getStatus());
        }
        assess(inc.getVendorId(), riskLevel, score, conclusion, "EVENT", actor);
        inc.markReassessing();
        return incidentRepository.save(inc);
    }

    /** 事件闭环：复评完成后关闭（REASSESSING → CLOSED）。 */
    @Transactional
    public VendorIncident closeIncident(Long incidentId, String actor) {
        VendorIncident inc = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("事件不存在或不可见：id=" + incidentId));
        if (!VendorIncident.REASSESSING.equals(inc.getStatus())) {
            throw new IllegalStateException("仅复评中(REASSESSING)事件可闭环，当前状态：" + inc.getStatus());
        }
        inc.close();
        VendorIncident saved = incidentRepository.save(inc);
        hashChainService.append(inc.getOrgId(), "VENDOR_INCIDENT_CLOSE", actor, "VENDOR:" + inc.getVendorId(),
                "外部事件复评闭环：" + inc.getEvent());
        return saved;
    }

    /** 原件 sha256（十六进制），与制度/证据同款固化口径。 */
    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
