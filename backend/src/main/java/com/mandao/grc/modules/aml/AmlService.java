package com.mandao.grc.modules.aml;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 反洗钱 AML 业务服务（GRC 合规管理视角）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 modules 包 → OrgScopeAspect 注入 visible_orgs，
 * RLS 裁剪 + 写校验；关键操作经 {@link HashChainService#append} 入按 org 分链的哈希链。
 *
 * 两大能力：
 *  1) 名单管理 + 筛查：登记制裁/PEP/内部黑名单，按名称/证件号筛查客户与交易对手（只匹配本组织 ACTIVE 名单）；
 *  2) 可疑交易报告 STR：DRAFT → SUBMITTED → REPORTED → CLOSED 生命周期（复用监管报送范式）。
 * 合规义务复用 obligation、机构风险自评复用 assessment（前端引用，不在此建模）。
 */
@Service
public class AmlService {

    private static final Set<String> LIST_TYPES = Set.of("SANCTION", "PEP", "INTERNAL");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MID", "HIGH");

    private final AmlWatchlistRepository watchlistRepository;
    private final StrReportRepository strRepository;
    private final HashChainService hashChainService;

    public AmlService(AmlWatchlistRepository watchlistRepository, StrReportRepository strRepository,
                      HashChainService hashChainService) {
        this.watchlistRepository = watchlistRepository;
        this.strRepository = strRepository;
        this.hashChainService = hashChainService;
    }

    // ===== 名单管理 =====

    @Transactional(readOnly = true)
    public List<AmlWatchlist> listWatchlist() {
        return watchlistRepository.findByOrderByIdDesc();
    }

    /** 登记名单条目（SANCTION/PEP/INTERNAL）。 */
    @Transactional
    public AmlWatchlist addWatchEntry(Long orgId, String listType, String name, String idNumber,
                                      String country, String source, String reason, String actor) {
        if (listType == null || !LIST_TYPES.contains(listType)) {
            throw new IllegalArgumentException("名单类型仅允许 SANCTION/PEP/INTERNAL：" + listType);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名单主体名称不能为空");
        }
        AmlWatchlist saved = watchlistRepository.save(
                new AmlWatchlist(orgId, listType, name.trim(), blankToNull(idNumber),
                        blankToNull(country), blankToNull(source), reason, actor));
        hashChainService.append(orgId, "AML_WATCH_ADD", actor, "AML_WATCH:" + saved.getId(),
                "登记名单 " + listType + " 主体=" + name);
        return saved;
    }

    /** 停用名单条目（ACTIVE → RETIRED，不再参与筛查，留档）。 */
    @Transactional
    public AmlWatchlist retireWatchEntry(Long id, String actor) {
        AmlWatchlist e = watchlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("名单条目不存在或不可见：id=" + id));
        if (!"ACTIVE".equals(e.getStatus())) {
            throw new IllegalStateException("仅启用(ACTIVE)名单条目可停用，当前：" + e.getStatus());
        }
        e.retire();
        AmlWatchlist saved = watchlistRepository.save(e);
        hashChainService.append(e.getOrgId(), "AML_WATCH_RETIRE", actor, "AML_WATCH:" + id, "停用名单条目");
        return saved;
    }

    /** 筛查命中项：条目 + 命中原因（NAME/ID）。 */
    public record ScreenHit(AmlWatchlist entry, String matchBy) {
    }

    /**
     * 名单筛查：对本组织 ACTIVE 名单，按名称子串（不区分大小写）或证件号精确匹配。
     * name/idNumber 至少给一个。只在可见域内筛（RLS）——不会命中他组织名单。
     */
    @Transactional(readOnly = true)
    public List<ScreenHit> screen(String name, String idNumber) {
        String nm = name == null ? "" : name.trim().toLowerCase();
        String idn = idNumber == null ? "" : idNumber.trim();
        if (nm.isEmpty() && idn.isEmpty()) {
            throw new IllegalArgumentException("筛查须至少提供 名称 或 证件号");
        }
        List<ScreenHit> hits = new java.util.ArrayList<>();
        for (AmlWatchlist e : watchlistRepository.findByStatusOrderByIdDesc("ACTIVE")) {
            if (!idn.isEmpty() && idn.equalsIgnoreCase(e.getIdNumber() == null ? "" : e.getIdNumber())) {
                hits.add(new ScreenHit(e, "ID"));
            } else if (!nm.isEmpty() && e.getName().toLowerCase().contains(nm)) {
                hits.add(new ScreenHit(e, "NAME"));
            }
        }
        return hits;
    }

    // ===== 可疑交易报告 STR =====

    @Transactional(readOnly = true)
    public List<StrReport> listStr() {
        return strRepository.findByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public StrReport getStr(Long id) {
        return strRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("STR 不存在或不可见：id=" + id));
    }

    /** 登记 STR（DRAFT）。 */
    @Transactional
    public StrReport createStr(Long orgId, String subject, BigDecimal amount, String riskLevel,
                               String reason, LocalDate occurredDate, String actor) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("可疑主体不能为空");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("可疑理由不能为空");
        }
        if (riskLevel != null && !RISK_LEVELS.contains(riskLevel)) {
            throw new IllegalArgumentException("风险等级仅允许 LOW/MID/HIGH：" + riskLevel);
        }
        StrReport saved = strRepository.save(
                new StrReport(orgId, subject.trim(), amount, riskLevel, reason, occurredDate, actor));
        appendStr(saved, "STR_CREATE", actor, "登记可疑交易报告 主体=" + subject + " 风险=" + saved.getRiskLevel());
        return saved;
    }

    /** 内部提交：DRAFT → SUBMITTED。 */
    @Transactional
    public StrReport submitStr(Long id, String actor) {
        StrReport s = getStr(id);
        transition(s, "DRAFT", "SUBMITTED");
        StrReport saved = strRepository.save(s);
        appendStr(saved, "STR_SUBMIT", actor, "内部提交 STR");
        return saved;
    }

    /** 报送反洗钱监测中心：SUBMITTED → REPORTED（记报送机构/回执号/报送日）。 */
    @Transactional
    public StrReport reportStr(Long id, String reportedTo, String reportNo, LocalDate reportedDate, String actor) {
        StrReport s = getStr(id);
        if (!"SUBMITTED".equals(s.getStatus())) {
            throw new IllegalStateException("仅已提交(SUBMITTED)的 STR 可报送，当前：" + s.getStatus());
        }
        if (reportNo == null || reportNo.isBlank()) {
            throw new IllegalArgumentException("报送须填写回执号");
        }
        s.report(blankToNull(reportedTo), reportNo.trim(), reportedDate == null ? LocalDate.now() : reportedDate);
        s.setStatus("REPORTED");
        StrReport saved = strRepository.save(s);
        appendStr(saved, "STR_REPORT", actor, "报送监测中心 回执=" + reportNo);
        return saved;
    }

    /** 了结：REPORTED → CLOSED（终态）。 */
    @Transactional
    public StrReport closeStr(Long id, String actor) {
        StrReport s = getStr(id);
        transition(s, "REPORTED", "CLOSED");
        StrReport saved = strRepository.save(s);
        appendStr(saved, "STR_CLOSE", actor, "STR 了结");
        return saved;
    }

    // ---------- 内部辅助 ----------

    private void transition(StrReport s, String from, String to) {
        if (!from.equals(s.getStatus())) {
            throw new IllegalStateException(
                    "非法状态流转：STR id=" + s.getId() + " 当前=" + s.getStatus() + "，仅允许 " + from + " → " + to);
        }
        s.setStatus(to);
    }

    private void appendStr(StrReport s, String action, String actor, String detail) {
        hashChainService.append(s.getOrgId(), action, actor, "STR:" + s.getId(), detail);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
