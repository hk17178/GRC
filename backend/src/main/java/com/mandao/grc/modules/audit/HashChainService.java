package com.mandao.grc.modules.audit;

import com.mandao.grc.common.audit.HashChain;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 操作日志防篡改哈希链服务（按 org 分链）。
 *
 * 位于 modules 包且方法 @Transactional → 由 OrgScopeAspect 自动注入 visible_orgs，
 * 故只能对自己可见的组织追加/校验日志，隔离与留痕一致。
 *
 * 设计依据：D1-3 §8（ADR-C）、D1-2 §13、D1-9 H-02。
 */
@Service
public class HashChainService {

    @PersistenceContext
    private EntityManager em;

    /**
     * 追加一条操作日志并入链。
     *
     * 并发安全：同一 org 链的追加需串行，否则两条并发记录可能读到相同 prev_hash 造成分叉。
     * 这里用【事务级 advisory 锁】按 org 串行化（事务结束自动释放），保证 prev_hash 正确。
     */
    @Transactional
    public OperationLogEntry append(long orgId, String action, String actor, String entity, String detail) {
        // 1) 按 org 加事务级咨询锁，串行化同链追加（1000 为本用途的锁命名空间）
        em.createNativeQuery("SELECT pg_advisory_xact_lock(1000, CAST(:org AS integer))")
                .setParameter("org", orgId)
                .getResultList();

        // 2) 取该 org 链尖（最大 seq 的那条），决定本条的 seq 与 prev_hash
        List<Object[]> tip = em.createNativeQuery(
                        "SELECT seq, curr_hash FROM operation_log WHERE org_id = :org ORDER BY seq DESC LIMIT 1")
                .setParameter("org", orgId)
                .getResultList();

        long seq;
        String prevHash;
        if (tip.isEmpty()) {
            seq = 1;
            prevHash = HashChain.GENESIS;          // 链首
        } else {
            seq = ((Number) tip.get(0)[0]).longValue() + 1;
            prevHash = (String) tip.get(0)[1];
        }

        // 3) 计算本条哈希：SHA256(规范化内容 + prev_hash)
        long createdAtMs = Instant.now().toEpochMilli();
        String currHash = HashChain.sha256Hex(
                HashChain.canonical(orgId, seq, createdAtMs, action, actor, entity, detail, prevHash));

        // 4) 入库（RLS WITH CHECK 要求 orgId ∈ visible_orgs，与隔离一致）
        em.createNativeQuery(
                        "INSERT INTO operation_log(org_id, seq, action, actor, entity, detail, created_at_ms, prev_hash, curr_hash) "
                                + "VALUES (:org, :seq, :action, :actor, :entity, :detail, :ms, :prev, :curr)")
                .setParameter("org", orgId)
                .setParameter("seq", seq)
                .setParameter("action", action)
                .setParameter("actor", actor)
                .setParameter("entity", entity)
                .setParameter("detail", detail)
                .setParameter("ms", createdAtMs)
                .setParameter("prev", prevHash)
                .setParameter("curr", currHash)
                .executeUpdate();

        return new OperationLogEntry(orgId, seq, currHash);
    }

    /**
     * 重算并校验某 org 的整条链。
     * 从 GENESIS 起逐条重算 curr_hash 并比对，同时检查 seq 连续、prev_hash 衔接。
     * 任意一处不符即判定被篡改/断链，返回首个异常的 seq 与原因。
     *
     * 威胁模型：即便攻击者直连数据库（绕过应用、绕过仅追加授权）改了某行内容，
     * 其 curr_hash 不会同步且后续整条链的重算都会失配 → 必被发现。
     */
    @Transactional(readOnly = true)
    public ChainVerifyResult verify(long orgId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT seq, action, actor, entity, detail, created_at_ms, prev_hash, curr_hash "
                                + "FROM operation_log WHERE org_id = :org ORDER BY seq ASC")
                .setParameter("org", orgId)
                .getResultList();

        String expectedPrev = HashChain.GENESIS;
        long expectedSeq = 1;
        long checked = 0;

        for (Object[] r : rows) {
            long seq = ((Number) r[0]).longValue();
            String action = (String) r[1];
            String actor = (String) r[2];
            String entity = (String) r[3];
            String detail = (String) r[4];
            long createdAtMs = ((Number) r[5]).longValue();
            String storedPrev = (String) r[6];
            String storedCurr = (String) r[7];

            if (seq != expectedSeq) {
                return ChainVerifyResult.broken(orgId, checked, seq, "序号不连续（疑似删除/插入）");
            }
            if (!storedPrev.equals(expectedPrev)) {
                return ChainVerifyResult.broken(orgId, checked, seq, "prev_hash 断链");
            }
            String recomputed = HashChain.sha256Hex(
                    HashChain.canonical(orgId, seq, createdAtMs, action, actor, entity, detail, storedPrev));
            if (!recomputed.equals(storedCurr)) {
                return ChainVerifyResult.broken(orgId, checked, seq, "内容被篡改（curr_hash 不匹配）");
            }

            expectedPrev = storedCurr;
            expectedSeq++;
            checked++;
        }

        return ChainVerifyResult.ok(orgId, checked);
    }
}
