package com.mandao.grc.modules.atv;

import com.mandao.grc.modules.asset.Asset;
import com.mandao.grc.modules.asset.AssetService;
import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A-T-V 业务服务（资产-威胁-脆弱风险识别，桥接 M2 风险与 M6 资产）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，切面注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入哈希链。
 *
 * 核心：资产 × 威胁 × 脆弱性 → 风险场景，固有等级由可能性×影响经风险矩阵派生（平台五级）。
 * 创建场景时经 {@link AssetService#get} 校验资产可见（桥接 M6 + RLS），并以资产所属组织作为场景组织。
 *
 * 设计依据：D1-2/D1-3（A-T-V、风险矩阵、五级）、D1-7（风险评估）、D2-5。
 */
@Service
public class AtvService {

    private final ThreatRepository threatRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final RiskScenarioRepository scenarioRepository;
    private final AssetService assetService;
    private final com.mandao.grc.modules.assessment.RiskFindingService riskFindingService;
    private final HashChainService hashChainService;

    public AtvService(ThreatRepository threatRepository,
                      VulnerabilityRepository vulnerabilityRepository,
                      RiskScenarioRepository scenarioRepository,
                      AssetService assetService,
                      com.mandao.grc.modules.assessment.RiskFindingService riskFindingService,
                      HashChainService hashChainService) {
        this.threatRepository = threatRepository;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.scenarioRepository = scenarioRepository;
        this.assetService = assetService;
        this.riskFindingService = riskFindingService;
        this.hashChainService = hashChainService;
    }

    // ---------- 威胁库 ----------

    @Transactional(readOnly = true)
    public List<Threat> listThreats() {
        return threatRepository.findAll();
    }

    @Transactional
    public Threat createThreat(Long orgId, String code, String name, String category, String description, String actor) {
        Threat saved = threatRepository.save(new Threat(orgId, code, name, category, description));
        hashChainService.append(orgId, "THREAT_CREATE", actor, "THREAT:" + saved.getId(),
                "登记威胁 code=" + code);
        return saved;
    }

    // ---------- 脆弱性库 ----------

    @Transactional(readOnly = true)
    public List<Vulnerability> listVulnerabilities() {
        return vulnerabilityRepository.findAll();
    }

    @Transactional
    public Vulnerability createVulnerability(Long orgId, String code, String name, String category,
                                             String description, String actor) {
        Vulnerability saved = vulnerabilityRepository.save(new Vulnerability(orgId, code, name, category, description));
        hashChainService.append(orgId, "VULN_CREATE", actor, "VULN:" + saved.getId(),
                "登记脆弱性 code=" + code);
        return saved;
    }

    // ---------- A-T-V 风险场景 ----------

    @Transactional(readOnly = true)
    public List<RiskScenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<RiskScenario> listScenariosByAsset(Long assetId) {
        return scenarioRepository.findByAssetId(assetId);
    }

    @Transactional(readOnly = true)
    public RiskScenario getScenario(Long id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("风险场景不存在或不可见：id=" + id));
    }

    /**
     * 登记一个 A-T-V 风险场景：校验资产可见（桥接 M6）、威胁/脆弱存在，组合判重，
     * 固有等级由可能性×影响派生；以资产所属组织为场景组织。
     */
    @Transactional
    public RiskScenario createScenario(Long assetId, Long threatId, Long vulnerabilityId,
                                       int likelihood, int impact, String description, String actor) {
        validateScore(likelihood, "可能性");
        validateScore(impact, "影响");

        Asset asset = assetService.get(assetId); // 桥接 M6 + 可见性(RLS)校验，不可见即视为不存在
        threatRepository.findById(threatId)
                .orElseThrow(() -> new IllegalArgumentException("威胁不存在或不可见：id=" + threatId));
        vulnerabilityRepository.findById(vulnerabilityId)
                .orElseThrow(() -> new IllegalArgumentException("脆弱性不存在或不可见：id=" + vulnerabilityId));

        if (scenarioRepository.existsByAssetIdAndThreatIdAndVulnerabilityId(assetId, threatId, vulnerabilityId)) {
            throw new IllegalStateException("该资产-威胁-脆弱组合的风险场景已存在");
        }

        RiskScenario scenario = new RiskScenario(asset.getOrgId(), assetId, threatId, vulnerabilityId,
                likelihood, impact, description);
        RiskScenario saved = scenarioRepository.save(scenario);
        hashChainService.append(asset.getOrgId(), "SCENARIO_CREATE", actor, "SCENARIO:" + saved.getId(),
                "登记风险场景 asset=" + assetId + " 可能性=" + likelihood + " 影响=" + impact
                        + " 固有=" + saved.getInherentLevel());
        return saved;
    }

    /** 重评风险场景：更新可能性/影响并重算固有等级；留痕。 */
    @Transactional
    public RiskScenario reassess(Long scenarioId, int likelihood, int impact, String actor) {
        validateScore(likelihood, "可能性");
        validateScore(impact, "影响");
        RiskScenario scenario = getScenario(scenarioId);
        scenario.reassess(likelihood, impact);
        RiskScenario saved = scenarioRepository.save(scenario);
        hashChainService.append(scenario.getOrgId(), "SCENARIO_REASSESS", actor, "SCENARIO:" + scenarioId,
                "重评风险场景 可能性=" + likelihood + " 影响=" + impact + " 固有=" + saved.getInherentLevel());
        return saved;
    }

    /**
     * A-T-V 场景一键生成风险发现（V48 风险登记册：识别→分析→评价打通）。
     *
     * 标题自动组装为「资产：威胁（脆弱性）」，固有等级取场景派生等级；
     * 同一评估同一场景防重复由 RiskFindingService 校验。
     */
    @Transactional
    public com.mandao.grc.modules.assessment.RiskFinding toFinding(Long scenarioId, Long assessmentId, String actor) {
        RiskScenario s = getScenario(scenarioId);
        Asset asset = assetService.get(s.getAssetId());
        String threatName = threatRepository.findById(s.getThreatId()).map(Threat::getName).orElse("威胁#" + s.getThreatId());
        String vulnName = vulnerabilityRepository.findById(s.getVulnerabilityId())
                .map(Vulnerability::getName).orElse("脆弱性#" + s.getVulnerabilityId());
        String title = asset.getName() + "：" + threatName + "（" + vulnName + "）";
        com.mandao.grc.modules.assessment.RiskFinding saved = riskFindingService.createFromScenario(
                s.getOrgId(), assessmentId, title, s.getInherentLevel(), scenarioId, actor);
        hashChainService.append(s.getOrgId(), "SCENARIO_TO_FINDING", actor, "SCENARIO:" + scenarioId,
                "场景生成风险发现 finding=" + saved.getId() + " assessment=" + assessmentId);
        return saved;
    }

    /** 校验可能性/影响取值在 1–5。 */
    private void validateScore(int v, String field) {
        if (v < 1 || v > 5) {
            throw new IllegalArgumentException(field + "取值须在 1–5，当前：" + v);
        }
    }
}
