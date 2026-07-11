package com.mandao.grc.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希链算法工具：定义"被哈希的规范化内容"与 SHA-256 计算。
 *
 * 规范化（canonical）是哈希链可重算、可验证的关键——append 与 verify 必须用完全相同的
 * 字节序列计算，否则验证恒失败。这里用 '|' 分隔字段，并对字段内的 '\' 与 '|' 转义，
 * 防止"字段内含分隔符导致歧义"（例如 a|b 与 a、b 拼出相同串）。
 */
public final class HashChain {

    /** 链首种子：每条 org 链的第一条记录其 prev_hash 取此值（64 个 0）。 */
    public static final String GENESIS = "0".repeat(64);

    private HashChain() {}

    /**
     * 生成被哈希的规范化字符串。字段顺序固定，不可随意调整。
     * 纳入哈希的字段：org_id、seq、入链时间(ms)、action、actor、entity、detail、prev_hash。
     */
    public static String canonical(long orgId, long seq, long createdAtMs,
                                   String action, String actor, String entity,
                                   String detail, String prevHash) {
        return orgId + "|" + seq + "|" + createdAtMs + "|"
                + esc(action) + "|" + esc(actor) + "|" + esc(entity) + "|"
                + esc(detail) + "|" + prevHash;
    }

    /** 计算 SHA-256 并以小写十六进制返回（64 字符）。 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，理论上不会发生
            throw new IllegalStateException("缺少 SHA-256 算法", e);
        }
    }

    /**
     * 计算 HMAC-SHA256 并以小写十六进制返回（64 字符）。安全评审 H-1：
     * 无密钥的裸 SHA-256 任何能直连库写入者都可自行重算整条链；改用 keyed-HMAC 后，
     * 不掌握密钥（GRC_HASHCHAIN_SECRET，仅环境注入）者无法伪造出与内容一致的 curr_hash。
     */
    public static String hmacSha256Hex(String key, String input) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    /** 转义分隔符，消除字段歧义；null 统一视为空串。 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }
}
