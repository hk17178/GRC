package com.mandao.grc.modules.kri;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * KRI 业务服务（M2 风险持续监测）。
 *
 * 隔离/留痕范式同其它模块：方法 @Transactional 且位于 com.mandao.grc.modules 包，
 * {@link com.mandao.grc.common.isolation.OrgScopeAspect} 在事务内注入 app.visible_orgs，RLS 裁剪 + 写校验；
 * 关键操作经 {@link HashChainService#append} 入按 org 分链的防篡改哈希链。
 *
 * 监测语义：每次 {@link #record} 测量按指标方向与双阈值评定状态（NORMAL/WARNING/CRITICAL），
 * 落一条时序测量并回写指标"最近值/最近状态"；CRITICAL 即红线触发，留痕中标注。
 *
 * 设计依据：D1-2 数据模型（KRI/阈值/监测）、D1-7（风险评估·KRI 监控）、D2-5。
 */
@Service
public class KriService {

    private final KriRepository kriRepository;
    private final KriMeasurementRepository measurementRepository;
    private final HashChainService hashChainService;

    public KriService(KriRepository kriRepository,
                      KriMeasurementRepository measurementRepository,
                      HashChainService hashChainService) {
        this.kriRepository = kriRepository;
        this.measurementRepository = measurementRepository;
        this.hashChainService = hashChainService;
    }

    /** 列出当前可见组织范围内的全部 KRI（靠切面 + RLS，无手写 org 过滤）。 */
    @Transactional(readOnly = true)
    public List<Kri> list() {
        return kriRepository.findAll();
    }

    /** 按 id 取 KRI（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public Kri get(Long id) {
        return kriRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KRI 不存在或不可见：id=" + id));
    }

    /** 列出某 KRI 的测量历史（最新在前）。 */
    @Transactional(readOnly = true)
    public List<KriMeasurement> listMeasurements(Long kriId) {
        get(kriId); // 触发可见性校验：不可见的 KRI 视为不存在
        return measurementRepository.findByKriIdOrderByIdDesc(kriId);
    }

    /**
     * 定义一个 KRI。
     *
     * @param orgId 归属组织（须在 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     */
    @Transactional
    public Kri create(Long orgId, String code, String name, String unit, KriDirection direction,
                      BigDecimal thresholdWarning, BigDecimal thresholdCritical, String owner, String actor) {
        Kri kri = new Kri(orgId, code, name, unit, direction, thresholdWarning, thresholdCritical, owner);
        Kri saved = kriRepository.save(kri);
        hashChainService.append(orgId, "KRI_CREATE", actor, "KRI:" + saved.getId(),
                "定义 KRI code=" + code + " 方向=" + direction
                        + " 预警=" + thresholdWarning + " 严重=" + thresholdCritical);
        return saved;
    }

    /**
     * 记录一次测量：按所属 KRI 的方向/阈值评定状态，落时序记录并回写指标最近值/状态；留痕。
     *
     * @param value 测量值
     * @param note  备注（数据来源/说明，可空）
     * @param actor 操作人（留痕）
     * @return 本次测量记录（含评定状态）
     */
    @Transactional
    public KriMeasurement record(Long kriId, BigDecimal value, String note, String actor) {
        return doRecord(get(kriId), value, note, actor);
    }

    /** 记录核心（供 record 与 B39 批量推送复用，避免二次可见性查询）：评定→落时序→回写最近态→留痕。 */
    private KriMeasurement doRecord(Kri kri, BigDecimal value, String note, String actor) {
        KriStatus status = kri.evaluate(value);

        KriMeasurement m = new KriMeasurement(kri.getOrgId(), kri.getId(), value, status, note);
        KriMeasurement saved = measurementRepository.save(m);

        // 回写指标最近值/状态（监测态势看板据此展示）
        kri.applyCurrent(value, status);
        kriRepository.save(kri);

        boolean breach = status == KriStatus.WARNING || status == KriStatus.CRITICAL;
        hashChainService.append(kri.getOrgId(), "KRI_MEASURE", actor, "KRI:" + kri.getId(),
                "测量 code=" + kri.getCode() + " 值=" + value + " 评定=" + status
                        + (breach ? "（越界触发）" : ""));
        return saved;
    }

    // ===== B39：KRI 外部推送接口（M2M 摄入） =====

    /** 批量推送的单条：按 KRI code 推送一个测量值。 */
    public record PushItem(String code, BigDecimal value, String note) {
    }

    /** 单条推送结果：ok 时带评定状态，否则带错误原因。 */
    public record PushResult(String code, boolean ok, String status, String error) {
    }

    /**
     * 外部监测系统按 code 批量推送 KRI 测量（B39·M2M 摄入）。
     *
     * 逐条按 (orgId, code) 解析 KRI 并复用记录核心（评定/落库/回写/留痕）；找不到或缺值的条目
     * 记为失败但不影响其余（用 Optional 判存在而非抛异常——异常会把整批事务标脏回滚）。
     * 隔离：orgId 须在调用方可见域内，否则 RLS 裁剪致 findByOrgIdAndCode 为空 → 该条失败，绝不跨组织写。
     * 注：本接口当前复用会话认证；真正的机器对机器 API-Key 鉴权列入安全评审项。
     */
    @Transactional
    public List<PushResult> pushBatch(Long orgId, List<PushItem> items, String actor) {
        List<PushResult> out = new java.util.ArrayList<>();
        if (items == null) {
            return out;
        }
        for (PushItem it : items) {
            if (it.code() == null || it.code().isBlank()) {
                out.add(new PushResult(it.code(), false, null, "缺 KRI 编码"));
                continue;
            }
            if (it.value() == null) {
                out.add(new PushResult(it.code(), false, null, "缺测量值"));
                continue;
            }
            java.util.Optional<Kri> kri = kriRepository.findByOrgIdAndCode(orgId, it.code());
            if (kri.isEmpty()) {
                out.add(new PushResult(it.code(), false, null, "KRI 不存在或不可见"));
                continue;
            }
            KriMeasurement m = doRecord(kri.get(), it.value(), it.note(), actor);
            out.add(new PushResult(it.code(), true, m.getStatus().name(), null));
        }
        return out;
    }
}
