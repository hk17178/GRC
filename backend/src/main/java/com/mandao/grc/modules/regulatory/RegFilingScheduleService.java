package com.mandao.grc.modules.regulatory;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 周期性报送计划服务（M11 B34）。
 *
 * 隔离/留痕范式同其它监管服务：@Transactional → 切面注入 visible_orgs，RLS 裁剪 + WITH CHECK；
 * 计划增改留痕入链（entity="REG_FILING_SCHEDULE:{id}"）。
 *
 * 到期生成由内核 ExpiryScanService 跨租户统一调度（见 scanOnce），本服务只管 CRUD 与启停。
 */
@Service
public class RegFilingScheduleService {

    private final RegFilingScheduleRepository repository;
    private final HashChainService hashChainService;

    public RegFilingScheduleService(RegFilingScheduleRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public List<RegFilingSchedule> list() {
        return repository.findAllByOrderByNextDueAsc();
    }

    @Transactional(readOnly = true)
    public RegFilingSchedule get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("报送计划不存在或不可见：id=" + id));
    }

    /** 新建周期性报送计划。 */
    @Transactional
    public RegFilingSchedule create(Long orgId, String title, String regulator,
                                    RegFilingSchedule.Period period, int leadDays, LocalDate nextDue, String actor) {
        if (nextDue == null) {
            throw new IllegalArgumentException("首次到期日必填");
        }
        RegFilingSchedule s = new RegFilingSchedule(orgId, title, regulator, period, leadDays, nextDue, actor);
        RegFilingSchedule saved = repository.save(s);
        appendLog(saved, "REG_FILING_SCHEDULE_CREATE", actor,
                "新建周期报送计划 title=" + title + " period=" + period + " next=" + nextDue);
        return saved;
    }

    /** 启用/停用计划（停用后到期扫描不再生成实例）。 */
    @Transactional
    public RegFilingSchedule setEnabled(Long id, boolean enabled, String actor) {
        RegFilingSchedule s = get(id);
        s.setEnabled(enabled);
        RegFilingSchedule saved = repository.save(s);
        appendLog(saved, "REG_FILING_SCHEDULE_TOGGLE", actor, (enabled ? "启用" : "停用") + "周期报送计划");
        return saved;
    }

    private void appendLog(RegFilingSchedule s, String action, String actor, String detail) {
        hashChainService.append(s.getOrgId(), action, actor, "REG_FILING_SCHEDULE:" + s.getId(), detail);
    }
}
