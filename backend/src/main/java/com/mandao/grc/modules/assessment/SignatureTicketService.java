package com.mandao.grc.modules.assessment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 手机扫码签名服务（V57）。
 *
 * 流程：桌面签批 → createTicket（校验评估可见）→ 手机扫码开免登录页（ticketInfo 校验）→
 * submitSignature 存签名（PENDING→SIGNED）→ 桌面轮询 fetchSignature 取回（SIGNED→USED，一次性）。
 * 安全：token 随机 UUID + 5 分钟过期 + 取回即作废；签名图 ≤512KB；免登录端点不触达 RLS 业务表。
 */
@Service
public class SignatureTicketService {

    private static final int TTL_MINUTES = 5;
    private static final int MAX_BYTES = 512 * 1024;

    private final SignatureTicketRepository ticketRepo;
    private final AssessmentRepository assessmentRepo;

    public SignatureTicketService(SignatureTicketRepository ticketRepo, AssessmentRepository assessmentRepo) {
        this.ticketRepo = ticketRepo;
        this.assessmentRepo = assessmentRepo;
    }

    /** 桌面侧：为评估创建一次性签名令牌（评估须可见）。 */
    @Transactional
    public SignatureTicket createTicket(Long assessmentId, String actor) {
        Assessment a = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("评估不存在或不可见：id=" + assessmentId));
        SignatureTicket t = new SignatureTicket(UUID.randomUUID().toString(), assessmentId, a.getOrgId(),
                a.getTitle(), actor, OffsetDateTime.now().plusMinutes(TTL_MINUTES));
        return ticketRepo.save(t);
    }

    /** 手机端信息视图（不含签名字节）。 */
    public record TicketInfo(String status, String title, String expiresAt) {
    }

    /** 手机侧：令牌信息（打开签名页时校验）。 */
    @Transactional
    public TicketInfo ticketInfo(String token) {
        SignatureTicket t = getByToken(token);
        if (t.expired() && "PENDING".equals(t.getStatus())) {
            t.setStatus("EXPIRED");
            ticketRepo.save(t);
        }
        return new TicketInfo(t.getStatus(), t.getTitle(), t.getExpiresAt().toString());
    }

    /** 手机侧：提交手写签名（dataURL）。仅 PENDING 且未过期可签。 */
    @Transactional
    public void submitSignature(String token, String signatureDataUrl) {
        SignatureTicket t = getByToken(token);
        if (t.expired()) {
            t.setStatus("EXPIRED");
            ticketRepo.save(t);
            throw new IllegalStateException("签名令牌已过期（有效期 " + TTL_MINUTES + " 分钟），请在桌面端重新发起");
        }
        if (!"PENDING".equals(t.getStatus())) {
            throw new IllegalStateException("该令牌已使用或已签名");
        }
        if (signatureDataUrl == null || signatureDataUrl.isBlank()) {
            throw new IllegalArgumentException("签名不能为空");
        }
        String b64 = signatureDataUrl;
        int comma = b64.indexOf(',');
        if (comma >= 0) {
            b64 = b64.substring(comma + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("签名图为空或过大（>512KB）");
        }
        t.sign(bytes);
        ticketRepo.save(t);
    }

    /** 桌面轮询结果。 */
    public record FetchResult(String status, String signatureDataUrl) {
    }

    /** 桌面侧：轮询取回签名（校验令牌属于该评估；SIGNED → 返回 dataURL 并置 USED，一次性）。 */
    @Transactional
    public FetchResult fetchSignature(Long assessmentId, String token) {
        SignatureTicket t = getByToken(token);
        if (!t.getAssessmentId().equals(assessmentId)) {
            throw new IllegalArgumentException("令牌与评估不匹配");
        }
        if ("SIGNED".equals(t.getStatus())) {
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(t.getSignature());
            t.setStatus("USED");
            // 安全加固包 A18：一次性取回后立即清除票据内签名字节（正本已入评估存证，票据无须留双份明文）
            t.clearSignature();
            ticketRepo.save(t);
            return new FetchResult("SIGNED", dataUrl);
        }
        if (t.expired() && "PENDING".equals(t.getStatus())) {
            t.setStatus("EXPIRED");
            ticketRepo.save(t);
        }
        return new FetchResult(t.getStatus(), null);
    }

    private SignatureTicket getByToken(String token) {
        return ticketRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("签名令牌不存在"));
    }
}
