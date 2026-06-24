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
        Kri kri = get(kriId);
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
}
