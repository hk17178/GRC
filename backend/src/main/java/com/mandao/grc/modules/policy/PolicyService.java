package com.mandao.grc.modules.policy;

import com.mandao.grc.modules.audit.HashChainService;
import com.mandao.grc.modules.workflow.ApprovalDecision;
import com.mandao.grc.modules.workflow.WorkflowService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制度业务服务（M1 制度体系）。
 *
 * 隔离：本服务【不手动注入隔离上下文，也不手写 org 过滤】——只要方法带 @Transactional
 * 且位于 com.mandao.grc.modules 包，{@link com.mandao.grc.common.isolation.OrgScopeAspect}
 * 会在事务内自动注入 app.visible_orgs，随后 RLS 裁剪数据并校验写入（WITH CHECK）。
 *
 * 状态机：DRAFT → REVIEW → EFFECTIVE → DEPRECATED；REVIEW 可被驳回回 DRAFT。
 * 非法流转一律抛 {@link IllegalStateException}。
 *
 * 审批：提交评审时启动 Flowable 通用审批流（{@link WorkflowService}），审批人处置的结论
 * 驱动 REVIEW→EFFECTIVE（通过）或 REVIEW→DRAFT（驳回）。审批流转与制度状态流转同事务原子。
 *
 * 留痕：每次状态流转 / 签署后，调用 {@link HashChainService#append} 写入按 org 分链的
 * 防篡改哈希链（D1-3 §8 ADR-C）。HashChainService 自身 @Transactional，与本服务同事务/同连接，
 * 共享同一 visible_orgs 注入，故留痕与业务在同一组织范围内一致提交或一起回滚。
 *
 * 设计依据：D1-2 制度生命周期、D1-3 §5.1/§8、D2-5 编码规范。
 */
@Service
public class PolicyService {

    /** 制度审批的业务类型与审批候选组（Flowable 流程变量；候选组后续可映射 M8 角色）。 */
    public static final String BIZ_TYPE = "POLICY";
    public static final String APPROVER_GROUP = "POLICY_APPROVER";

    private final PolicyRepository policyRepository;
    private final PolicySignoffRepository signoffRepository;
    private final PolicyVersionRepository versionRepository;
    private final PolicyRefRepository refRepository;
    private final HashChainService hashChainService;
    private final WorkflowService workflowService;

    public PolicyService(PolicyRepository policyRepository,
                         PolicySignoffRepository signoffRepository,
                         PolicyVersionRepository versionRepository,
                         PolicyRefRepository refRepository,
                         HashChainService hashChainService,
                         WorkflowService workflowService) {
        this.policyRepository = policyRepository;
        this.signoffRepository = signoffRepository;
        this.versionRepository = versionRepository;
        this.refRepository = refRepository;
        this.hashChainService = hashChainService;
        this.workflowService = workflowService;
    }

    /**
     * 列出当前主体可见组织范围内的全部制度。
     * findAll() 不带任何 org 过滤——隔离完全由切面注入 + RLS 兜底保证。
     */
    @Transactional(readOnly = true)
    public List<Policy> list() {
        return policyRepository.findAll();
    }

    /**
     * 列表投影（七轮 7-8/A6）：不加载 docx 原件字节 + 分页护栏（单页上限 500，缺省 200）。
     * 列表页数据量在护栏内时前端无感；超限说明该上真分页 UI 了。
     */
    @Transactional(readOnly = true)
    public List<PolicySummary> listSummaries(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? 200 : Math.min(size, 500);
        return policyRepository.findAllProjectedByOrderByIdDesc(
                org.springframework.data.domain.PageRequest.of(p, s));
    }

    /** 按 id 取制度（仅能取到可见组织内的；不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public Policy get(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("制度不存在或不可见：id=" + id));
    }

    /**
     * 新建草稿制度。
     *
     * @param orgId  归属组织（必须在当前 visible_orgs 内，否则 RLS WITH CHECK 拒绝写入）
     * @param actor  操作人（用于留痕）
     */
    @Transactional
    public Policy create(Long orgId, String code, String title, String content, String actor) {
        Policy policy = new Policy(orgId, code, title, content);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_CREATE", actor, "新建制度草稿 code=" + code);
        return saved;
    }

    /**
     * 提交评审：DRAFT → REVIEW，并启动 Flowable 审批工作流。
     * 非 DRAFT 态调用属非法流转，抛 {@link IllegalStateException}。
     * 状态流转、留痕、启动审批流三者同事务原子提交/回滚。
     */
    @Transactional
    public Policy submitForApproval(Long id, String actor) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.DRAFT, PolicyStatus.REVIEW);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_SUBMIT", actor, "提交评审");
        // 启动通用审批流：审批人按候选组 POLICY_APPROVER 派单；结论驱动 EFFECTIVE/退回 DRAFT。
        workflowService.submit(BIZ_TYPE, saved.getId(), saved.getOrgId(), APPROVER_GROUP, actor);
        return saved;
    }

    /**
     * 审批通过：REVIEW → EFFECTIVE（经工作流处置）。
     */
    @Transactional
    public Policy approve(Long id, String actor) {
        return decide(id, ApprovalDecision.APPROVED, actor, null);
    }

    /**
     * 审批驳回：REVIEW → DRAFT（经工作流处置，退回起草人修改）。
     */
    @Transactional
    public Policy reject(Long id, String actor, String reason) {
        return decide(id, ApprovalDecision.REJECTED, actor, reason);
    }

    /**
     * 审批处置（M1 制度发布走 Flowable 审批的核心）：
     * 定位该制度进行中的审批任务 → 经 {@link WorkflowService#decide} 完成（推进流程 + 审批留痕）→
     * 按结论推进制度状态机（通过=生效 / 驳回=退回草稿）。审批与业务流转【同事务原子】。
     *
     * @param decision 审批结论（通过/驳回）
     * @param approver 审批人（留痕 actor）
     * @param comment  审批意见（驳回原因，可空）
     */
    @Transactional
    public Policy decide(Long id, ApprovalDecision decision, String approver, String comment) {
        Policy policy = get(id);
        if (policy.getStatus() != PolicyStatus.REVIEW) {
            throw new IllegalStateException(
                    "仅评审(REVIEW)态制度可审批，当前状态：" + policy.getStatus());
        }
        Task task = workflowService.activeTask(BIZ_TYPE, id);
        if (task == null) {
            throw new IllegalStateException("制度 id=" + id + " 无进行中的审批任务");
        }
        // 先完成审批任务（推进流程 + 审批留痕），再推进制度状态机——同事务，一致提交/回滚。
        workflowService.decide(task.getId(), decision, approver, comment);
        if (decision == ApprovalDecision.APPROVED) {
            transition(policy, PolicyStatus.REVIEW, PolicyStatus.EFFECTIVE);
            appendLog(policy, "POLICY_APPROVE", approver, "审批通过并生效"
                    + (comment == null || comment.isBlank() ? "" : "，意见：" + comment));
            ingestToKnowledgeBase(policy); // 八轮 8-4（B9/M1-9）：生效制度自动入知识库索引
        } else {
            transition(policy, PolicyStatus.REVIEW, PolicyStatus.DRAFT);
            appendLog(policy, "POLICY_REJECT", approver, "审批驳回，原因：" + (comment == null ? "" : comment));
        }
        return policyRepository.save(policy);
    }

    /**
     * 废止：EFFECTIVE → DEPRECATED（终态）。
     */
    @Transactional
    public Policy archive(Long id, String actor) {
        Policy policy = get(id);
        transition(policy, PolicyStatus.EFFECTIVE, PolicyStatus.DEPRECATED);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_ARCHIVE", actor, "废止");
        return saved;
    }

    /**
     * 签署确认：仅 EFFECTIVE 态的制度可被签署。
     *
     * 幂等/重复保护：同一制度同一签署人重复签署，由表上 UNIQUE(policy_id, signer) 在落库时拒绝
     * （抛出 DataIntegrityViolationException），事务回滚。
     */
    @Transactional
    public PolicySignoff signoff(Long policyId, String signer) {
        Policy policy = get(policyId);
        if (policy.getStatus() != PolicyStatus.EFFECTIVE) {
            throw new IllegalStateException(
                    "仅已生效(EFFECTIVE)制度可签署，当前状态：" + policy.getStatus());
        }
        PolicySignoff signoff = new PolicySignoff(policy.getId(), policy.getOrgId(), signer);
        PolicySignoff saved = signoffRepository.save(signoff);
        // 签署本身也是业务关键操作，入链留痕。
        hashChainService.append(policy.getOrgId(), "POLICY_SIGNOFF", signer,
                "POLICY:" + policy.getId(), "签署确认 signer=" + signer);
        return saved;
    }

    // ---------- M1 深度：元数据 / 版本历史 / 引用关系 / 签署明细 ----------

    /** 更新制度元数据（体系分类/生效日期/复审周期/责任部门/责任人）。 */
    @Transactional
    public Policy updateMeta(Long id, String framework, java.time.LocalDate effectiveDate,
                             Integer reviewCycleMonths, String ownerDept, String owner, String actor) {
        Policy policy = get(id);
        policy.updateMeta(framework, effectiveDate, reviewCycleMonths, ownerDept, owner);
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_META", actor, "更新元数据 framework=" + framework + " ownerDept=" + ownerDept);
        return saved;
    }

    /**
     * 修订制度：仅 EFFECTIVE 可修订。旧版(标题+正文)存快照 → 换入新内容、版本+1 →
     * 回到 REVIEW 重走审批（MAJOR 修订触发重新评审，需求 3.2.1/原型版本时间线语义）。
     */
    @Transactional
    public Policy revise(Long id, String newTitle, String newContent, String note, String actor) {
        Policy policy = get(id);
        if (policy.getStatus() != PolicyStatus.EFFECTIVE) {
            throw new IllegalStateException("仅已生效(EFFECTIVE)制度可修订，当前状态：" + policy.getStatus());
        }
        // 1) 旧版快照存档
        versionRepository.save(new PolicyVersion(policy.getOrgId(), policy.getId(), policy.getVersion(),
                policy.getTitle(), policy.getContent(), note, actor));
        // 2) 换入新内容、版本+1、回 REVIEW 重走审批
        policy.reviseTo(newTitle, newContent);
        policy.setStatus(PolicyStatus.REVIEW);
        Policy saved = policyRepository.save(policy);
        // 3) 重启审批工作流 + 留痕
        workflowService.submit(BIZ_TYPE, saved.getId(), saved.getOrgId(), APPROVER_GROUP, actor);
        appendLog(saved, "POLICY_REVISE", actor, "修订至 v" + saved.getVersion() + "，说明：" + (note == null ? "" : note));
        return saved;
    }

    /** 版本历史（新→旧）。 */
    @Transactional(readOnly = true)
    public List<PolicyVersion> versions(Long policyId) {
        return versionRepository.findByPolicyIdOrderByVersionNoDesc(policyId);
    }

    /**
     * 上传制度原件（六轮 #6）：接收 .docx，POI 提取全文写入 content（供 AI 符合度评估等下游使用），
     * 原件字节 + sha256 固化留档（与证据库同款防篡改口径）。不限制状态——草稿补全文、
     * 生效制度换文由 revise 走版本管控，此处仅作为"全文来源"挂载。
     */
    @Transactional
    public Policy uploadDocument(Long id, String filename, byte[] bytes, String actor) {
        Policy policy = get(id);
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("仅支持 .docx 制度文件（Word 2007+ 格式）");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String text;
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(bytes));
             var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            text = extractor.getText();
        } catch (Exception e) {
            throw new IllegalArgumentException("docx 解析失败，请确认文件未损坏：" + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文档未提取到任何文本内容，请确认不是纯图片扫描件");
        }
        String sha256 = sha256Hex(bytes);
        policy.attachDocument(filename, sha256, bytes, text.strip());
        Policy saved = policyRepository.save(policy);
        appendLog(saved, "POLICY_DOC_UPLOAD", actor,
                "上传制度原件 " + filename + "（" + bytes.length + " 字节，sha256=" + sha256.substring(0, 12)
                        + "…，提取全文 " + text.strip().length() + " 字）");
        return saved;
    }

    /** 下载制度原件（六轮 #6）。 */
    @Transactional(readOnly = true)
    public Policy getWithDocument(Long id) {
        Policy policy = get(id);
        if (policy.getDocBytes() == null) {
            throw new IllegalArgumentException("该制度未上传原件");
        }
        return policy;
    }

    /** 知识库（八轮 8-4：生效制度自动入索引，setter 注入避免模块环）。 */
    private com.mandao.grc.modules.ai.KnowledgeBaseService knowledgeBaseService;

    @org.springframework.beans.factory.annotation.Autowired
    void wireKnowledgeBase(com.mandao.grc.modules.ai.KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 制度自动入知识库（八轮 8-4/B9）：生效即索引（sourceRef=POLICY:{id} 幂等复摄入，
     * 修订再生效自动替换旧版切块）；无全文的仅元数据制度跳过；失败不阻断审批主流程。
     */
    private void ingestToKnowledgeBase(Policy policy) {
        try {
            if (policy.getContent() == null || policy.getContent().isBlank()) {
                return;
            }
            knowledgeBaseService.upsertBySource(policy.getOrgId(),
                    "制度：" + policy.getTitle() + "（v" + policy.getVersion() + "）",
                    com.mandao.grc.modules.ai.KbSourceType.POLICY,
                    "POLICY:" + policy.getId(), policy.getContent());
            appendLog(policy, "POLICY_KB_INGEST", "system", "制度全文已入知识库索引（条款切块）");
        } catch (RuntimeException e) {
            // 入索引失败不影响制度生效（知识库可后补，审批主链路优先）
        }
    }

    /** 计算 sha256 十六进制串。 */
    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 添加引用关系（policy 引用 refPolicy；同组织内可见性由 RLS 保证）。 */
    @Transactional
    public PolicyRef addRef(Long policyId, Long refPolicyId, String note, String actor) {
        Policy policy = get(policyId);
        Policy ref = get(refPolicyId);
        if (policy.getId().equals(ref.getId())) {
            throw new IllegalArgumentException("制度不能引用自身");
        }
        PolicyRef saved = refRepository.save(new PolicyRef(policy.getOrgId(), policyId, refPolicyId, note));
        appendLog(policy, "POLICY_REF_ADD", actor, "引用制度 #" + refPolicyId + "（" + ref.getTitle() + "）");
        return saved;
    }

    /** 引用关系：本制度引用了谁 + 谁引用了本制度。 */
    @Transactional(readOnly = true)
    public Map<String, List<PolicyRef>> refs(Long policyId) {
        Map<String, List<PolicyRef>> m = new LinkedHashMap<>();
        m.put("outgoing", refRepository.findByPolicyId(policyId));
        m.put("incoming", refRepository.findByRefPolicyId(policyId));
        return m;
    }

    /** 某制度的签署确认明细（统计看板/待确认展示用）。 */
    @Transactional(readOnly = true)
    public List<PolicySignoff> signoffs(Long policyId) {
        return signoffRepository.findByPolicyId(policyId);
    }

    // ---------- 内部辅助 ----------

    /**
     * 校验并执行一次合法流转：要求当前态 == 期望的 from，否则视为非法流转抛异常。
     * 将"校验 + 推进"收敛到一处，避免各方法重复判断。
     */
    private void transition(Policy policy, PolicyStatus expectedFrom, PolicyStatus to) {
        if (policy.getStatus() != expectedFrom) {
            throw new IllegalStateException(
                    "非法状态流转：制度 id=" + policy.getId()
                            + " 当前状态=" + policy.getStatus()
                            + "，仅允许从 " + expectedFrom + " 流转到 " + to);
        }
        policy.setStatus(to);
    }

    /**
     * 统一的留痕入口：把一次制度操作追加进该 org 的防篡改哈希链。
     * entity 统一格式 "POLICY:{id}"，便于审计按对象检索。
     */
    private void appendLog(Policy policy, String action, String actor, String detail) {
        hashChainService.append(policy.getOrgId(), action, actor,
                "POLICY:" + policy.getId(), detail);
    }
}
