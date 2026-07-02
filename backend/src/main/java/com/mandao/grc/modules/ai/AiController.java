package com.mandao.grc.modules.ai;

import com.mandao.grc.common.auth.CurrentUserContext;
import com.mandao.grc.modules.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 智能问答 / 知识库 REST 端点：/api/ai。
 *
 * 隔离：可见范围由 X-User 头经 IsolationFilter 解析；本控制器不处理 org 过滤。
 * 知识库摄入/检索/问答均在可见组织范围内（RLS 兜底）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final KnowledgeBaseService kb;
    private final AiQaService qa;
    private final EmbeddingProvider embedding;
    private final AiConfigService configService;
    private final AiMaterialService materialService;

    public AiController(KnowledgeBaseService kb, AiQaService qa, EmbeddingProvider embedding,
                        AiConfigService configService, AiMaterialService materialService) {
        this.kb = kb;
        this.qa = qa;
        this.embedding = embedding;
        this.configService = configService;
        this.materialService = materialService;
    }

    /** 列出可见知识源文档。 */
    @GetMapping("/documents")
    public List<KbDocument> listDocuments() {
        return kb.list();
    }

    /** 摄入一篇知识源文档（登记 → 切块 → 嵌入 → 置 INDEXED）。 */
    @PostMapping("/documents")
    @RequiresPermission("ai")
    public KbDocument ingest(@RequestBody IngestRequest req) {
        return kb.ingest(req.orgId(), req.title(), req.sourceType(), req.sourceRef(), req.content());
    }

    /** 某文档的切块。 */
    @GetMapping("/documents/{id}/chunks")
    public List<KbChunk> chunks(@PathVariable Long id) {
        return kb.chunks(id);
    }

    /** 检索增强问答。 */
    @PostMapping("/ask")
    @RequiresPermission("ai")
    public AiQaService.AiAnswer ask(@RequestBody AskRequest req) {
        return qa.ask(req.question(), req.topK());
    }

    /** 当前 AI 配置状态（提供方/模型/嵌入维度），供「模型接入」页展示。 */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(qa.provider(), qa.model(), embedding.dim(), "local".equals(qa.provider()));
    }

    /** 取大模型接入配置（掩码，不回显密钥），供「模型接入」页编辑。 */
    @GetMapping("/config")
    public AiConfigService.ConfigView getConfig() {
        return configService.view();
    }

    /** 保存大模型接入配置（密钥加密落库；apiKey 留空表示不改）。写门控 "ai"。 */
    @PutMapping("/config")
    @RequiresPermission("ai")
    public AiConfigService.ConfigView updateConfig(@RequestBody ConfigRequest req,
                                                   @RequestHeader(value = "X-User", required = false) String user) {
        String actor = CurrentUserContext.get() != null ? CurrentUserContext.get()
                : (user == null || user.isBlank() ? "anonymous" : user);
        return configService.update(req.provider(), req.baseUrl(), req.model(),
                req.maxTokens() == null ? 1024 : req.maxTokens(),
                req.enabled() == null || req.enabled(), req.apiKey(), actor);
    }

    /** 大模型接入配置请求体（apiKey 为空=保持原密钥不变）。 */
    public record ConfigRequest(String provider, String baseUrl, String model, Integer maxTokens,
                                Boolean enabled, String apiKey) {
    }

    /** 生成报送/汇报材料初稿（需求 7.5.1；产出须人工复核）。 */
    @PostMapping("/generate")
    @RequiresPermission("ai")
    public AiMaterialService.Material generate(@RequestBody GenerateRequest req) {
        return materialService.generate(req.type());
    }

    /** 材料生成请求体（FILING_DRAFT 报送稿 / MGMT_BRIEF 管理层简报）。 */
    public record GenerateRequest(String type) {
    }

    /** 摄入请求体。 */
    public record IngestRequest(Long orgId, String title, KbSourceType sourceType, String sourceRef, String content) {
    }

    /** 提问请求体。 */
    public record AskRequest(String question, Integer topK) {
    }

    /** AI 状态：当前提供方、模型、嵌入维度、是否本地离线模式。 */
    public record AiStatus(String provider, String model, int embeddingDim, boolean offline) {
    }
}
