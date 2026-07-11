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

    /** 哈希链 HMAC 密钥（安全评审 H-1）：仅环境注入 GRC_HASHCHAIN_SECRET，缺失即启动 fail-fast。 */
    private final String hmacKey;

    public HashChainService(
            @org.springframework.beans.factory.annotation.Value("${grc.hashchain.secret:}") String hmacKey) {
        if (hmacKey == null || hmacKey.isBlank()) {
            throw new IllegalStateException(
                    "哈希链 HMAC 密钥未配置——请设置强随机 GRC_HASHCHAIN_SECRET 后再启动（安全评审 H-1）");
        }
        this.hmacKey = hmacKey;
    }

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

        // 3) 计算本条哈希：keyed-HMAC-SHA256(规范化内容 + prev_hash)（H-1，无密钥者不可伪造）
        long createdAtMs = Instant.now().toEpochMilli();
        String currHash = HashChain.hmacSha256Hex(hmacKey,
                HashChain.canonical(orgId, seq, createdAtMs, action, actor, entity, detail, prevHash));

        // 4) 入库（hash_algo 记 HMAC-SHA256；RLS WITH CHECK 要求 orgId ∈ visible_orgs，与隔离一致）
        em.createNativeQuery(
                        "INSERT INTO operation_log(org_id, seq, action, actor, entity, detail, created_at_ms, prev_hash, curr_hash, hash_algo) "
                                + "VALUES (:org, :seq, :action, :actor, :entity, :detail, :ms, :prev, :curr, 'HMAC-SHA256')")
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

        // 5) 更新链尖锚定（防链尾截断 M-4）：anchor_hmac = HMAC(key, org|seq|currHash)，无密钥不可伪造
        String anchorHmac = HashChain.hmacSha256Hex(hmacKey, orgId + "|" + seq + "|" + currHash);
        em.createNativeQuery(
                        "INSERT INTO operation_log_anchor(org_id, max_seq, tip_hash, anchor_hmac, updated_at) "
                                + "VALUES (:org, :seq, :tip, :hmac, now()) "
                                + "ON CONFLICT (org_id) DO UPDATE SET max_seq = EXCLUDED.max_seq, "
                                + "tip_hash = EXCLUDED.tip_hash, anchor_hmac = EXCLUDED.anchor_hmac, updated_at = now()")
                .setParameter("org", orgId)
                .setParameter("seq", seq)
                .setParameter("tip", currHash)
                .setParameter("hmac", anchorHmac)
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
                        "SELECT seq, action, actor, entity, detail, created_at_ms, prev_hash, curr_hash, hash_algo "
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
            // 按行算法重算：历史行 SHA256、新行 keyed-HMAC（H-1 兼容）
            String algo = (String) r[8];
            String canon = HashChain.canonical(orgId, seq, createdAtMs, action, actor, entity, detail, storedPrev);
            String recomputed = "HMAC-SHA256".equals(algo)
                    ? HashChain.hmacSha256Hex(hmacKey, canon)
                    : HashChain.sha256Hex(canon);
            if (!recomputed.equals(storedCurr)) {
                return ChainVerifyResult.broken(orgId, checked, seq, "内容被篡改（curr_hash 不匹配）");
            }

            expectedPrev = storedCurr;
            expectedSeq++;
            checked++;
        }

        // 链尾截断检测（M-4）：与链尖锚定比对。无密钥者删除链尾后无法同步伪造 anchor_hmac，必被发现。
        List<Object[]> anchor = em.createNativeQuery(
                        "SELECT max_seq, tip_hash, anchor_hmac FROM operation_log_anchor WHERE org_id = :org")
                .setParameter("org", orgId)
                .getResultList();
        if (!anchor.isEmpty()) {
            long anchoredSeq = ((Number) anchor.get(0)[0]).longValue();
            String anchoredTip = (String) anchor.get(0)[1];
            String anchoredHmac = (String) anchor.get(0)[2];
            // 先验锚定自身未被伪造（无密钥不可重算）
            String expectAnchorHmac = HashChain.hmacSha256Hex(hmacKey, orgId + "|" + anchoredSeq + "|" + anchoredTip);
            if (!expectAnchorHmac.equals(anchoredHmac)) {
                return ChainVerifyResult.broken(orgId, checked, anchoredSeq, "链尖锚定签名无效（anchor 被篡改）");
            }
            long actualMaxSeq = expectedSeq - 1;   // 循环后 expectedSeq 为下一个期望 seq
            if (actualMaxSeq < anchoredSeq) {
                return ChainVerifyResult.broken(orgId, checked, actualMaxSeq,
                        "链尾被截断（实际末条 seq " + actualMaxSeq + " < 锚定 " + anchoredSeq + "）");
            }
            if (actualMaxSeq == anchoredSeq && !expectedPrev.equals(anchoredTip)) {
                return ChainVerifyResult.broken(orgId, checked, actualMaxSeq, "链尖哈希与锚定不一致");
            }
        }

        return ChainVerifyResult.ok(orgId, checked);
    }
}
