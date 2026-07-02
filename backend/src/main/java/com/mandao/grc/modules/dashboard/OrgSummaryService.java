package com.mandao.grc.modules.dashboard;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按组织聚合的合规态势（驾驶舱热力矩阵/整改完成率的真值来源）。
 *
 * 六个"风险域"列全部来自真实对象计数（不臆造评分）：
 *  - risk     风险敞口：未闭环风险发现数（status ≠ DONE/VERIFIED）
 *  - data     数据合规：含个人信息或跨境的资产数（暴露面）
 *  - vendor   第三方：最近评估为 高/极高（HIGH/VERY_HIGH）的供应商数
 *  - reg      监管：未报送的监管事项数（≠ SUBMITTED/CLOSED）
 *  - audit    审计：未闭环审计发现数（≠ CLOSED）
 *  - remed    整改：未验证通过的整改单数（≠ VERIFIED）
 *
 * 整改完成率：VERIFIED / 总数；逾期 = due_date < 今天 且未 VERIFIED。
 *
 * 隔离：方法 @Transactional 且位于 modules 包 → 切面注入 visible_orgs，
 * 各表原生查询按 RLS 自动裁剪，返回的组织行即"当前可见组织"。
 */
@Service
public class OrgSummaryService {

    @PersistenceContext
    private EntityManager em;

    /** 每组织一行的六域计数 + 整改完成率。 */
    public record OrgRow(Long orgId, String orgName,
                         long risk, long data, long vendor, long reg, long audit, long remed,
                         long remedTotal, long remedDone, long remedOverdue, int remedPct) {
    }

    @Transactional(readOnly = true)
    public List<OrgRow> orgSummary() {
        // 可见组织（org 表无 RLS，这里以业务对象出现过的 org ∪ 可见树为准——直接取 visible_orgs 会话值裁剪的业务行，
        // 组织名单从 org 表按出现的 id 取名；为让"无数据的可见组织"也有行，以 app.visible_orgs 为行基准）
        @SuppressWarnings("unchecked")
        List<Object[]> orgRows = em.createNativeQuery(
                        "SELECT o.id, o.name FROM org o "
                                + "WHERE CAST(o.id AS text) = ANY(string_to_array(current_setting('app.visible_orgs', true), ',')) "
                                + "ORDER BY o.id")
                .getResultList();

        Map<Long, long[]> acc = new LinkedHashMap<>();       // orgId -> [risk,data,vendor,reg,audit,remed,total,done,overdue]
        Map<Long, String> names = new LinkedHashMap<>();
        for (Object[] r : orgRows) {
            long id = ((Number) r[0]).longValue();
            names.put(id, (String) r[1]);
            acc.put(id, new long[9]);
        }

        merge(acc, 0, "SELECT org_id, count(*) FROM risk_finding WHERE status NOT IN ('DONE','VERIFIED') GROUP BY org_id");
        merge(acc, 1, "SELECT org_id, count(*) FROM asset WHERE contains_pi OR cross_border GROUP BY org_id");
        merge(acc, 2, "SELECT org_id, count(*) FROM vendor WHERE risk_level IN ('HIGH','VERY_HIGH') GROUP BY org_id");
        merge(acc, 3, "SELECT org_id, count(*) FROM reg_filing WHERE status NOT IN ('SUBMITTED','CLOSED') GROUP BY org_id");
        merge(acc, 4, "SELECT org_id, count(*) FROM audit_finding WHERE status <> 'CLOSED' GROUP BY org_id");
        merge(acc, 5, "SELECT org_id, count(*) FROM remediation_order WHERE status <> 'VERIFIED' GROUP BY org_id");
        merge(acc, 6, "SELECT org_id, count(*) FROM remediation_order GROUP BY org_id");
        merge(acc, 7, "SELECT org_id, count(*) FROM remediation_order WHERE status = 'VERIFIED' GROUP BY org_id");
        merge(acc, 8, "SELECT org_id, count(*) FROM remediation_order WHERE status <> 'VERIFIED' AND due_date IS NOT NULL AND due_date < CURRENT_DATE GROUP BY org_id");

        List<OrgRow> out = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            int pct = v[6] == 0 ? 100 : (int) Math.round(v[7] * 100.0 / v[6]);
            out.add(new OrgRow(e.getKey(), names.get(e.getKey()),
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], pct));
        }
        return out;
    }

    /** 把一条 group by org_id 的计数查询并入累加器的第 idx 列（不可见 org 的行被 RLS 裁掉，天然对齐）。 */
    private void merge(Map<Long, long[]> acc, int idx, String sql) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        for (Object[] r : rows) {
            long[] v = acc.get(((Number) r[0]).longValue());
            if (v != null) {
                v[idx] = ((Number) r[1]).longValue();
            }
        }
    }
}
