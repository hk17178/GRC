package com.mandao.grc.modules.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 流程绑定服务（D1-8 §八 自定义工作流 H-06）。
 *
 * 职责：按 (object_type + org_id + condition) 把单据类型分流到不同 Flowable 流程定义，并在单据发起时
 * 给出**不可变的版本快照**（process_def_key + version）供业务方固化到单据/审批实例上。
 *
 * 红线：
 *  1) 绑定携 org_id + RLS——org13 看不到 org12 的绑定，{@link #resolve} 在注入 visible_orgs 的会话里查，
 *     跨组织绑定永不命中；
 *  2) condition 为声明式谓词（field/op/value，AND；空=兜底），**在内存里对上下文求值**，不拼 SQL、不 eval；
 *  3) 版本快照固化：{@link ProcessSnapshot} 是不可变值，业务方在发起时持久化它，后续改绑定不影响在途单据；
 *  4) 本服务只"选流程"，审批节点仍走 M8（ApprovalFlow/WorkflowService 的四元组与职责分离，不可自批）——不绕权限。
 */
@Service
public class ProcessBindingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProcessBindingRepository repository;
    private final HashChainService hashChainService;

    public ProcessBindingService(ProcessBindingRepository repository, HashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    /**
     * 流程版本快照——单据发起时命中的绑定固化值。不可变；业务方持久化后即与后续绑定变更解耦。
     */
    public record ProcessSnapshot(Long bindingId, String objectType, String processDefKey, int processVersion) {
    }

    @Transactional(readOnly = true)
    public List<ProcessBinding> list(String objectType) {
        return repository.findByObjectTypeOrderBySeqAscIdAsc(objectType);
    }

    /** 登记绑定（校验 condition 合法 + key/version 合规，坏定义写入即拒）。 */
    @Transactional
    public ProcessBinding create(Long orgId, String objectType, String name, String condition,
                                 String processDefKey, int processVersion, int seq, String actor) {
        if (processDefKey == null || !processDefKey.matches("[A-Za-z][A-Za-z0-9_\\-]{0,63}")) {
            throw new IllegalArgumentException("流程定义 key 非法：" + processDefKey);
        }
        if (processVersion < 1) {
            throw new IllegalArgumentException("流程版本须 ≥ 1");
        }
        validateCondition(condition);   // 写入即验谓词结构
        ProcessBinding b = new ProcessBinding(orgId, objectType, name,
                condition == null || condition.isBlank() ? "{}" : condition, processDefKey, processVersion, seq, actor);
        ProcessBinding saved = repository.save(b);
        hashChainService.append(orgId, "PROCESS_BINDING_CREATE", actor, "PROCESS_BINDING:" + saved.getId(),
                "登记流程绑定 " + objectType + " → " + processDefKey + " v" + processVersion);
        return saved;
    }

    @Transactional
    public ProcessBinding retire(Long id, String actor) {
        ProcessBinding b = get(id);
        b.setStatus("RETIRED");
        ProcessBinding saved = repository.save(b);
        hashChainService.append(b.getOrgId(), "PROCESS_BINDING_RETIRE", actor, "PROCESS_BINDING:" + id, "停用流程绑定");
        return saved;
    }

    /**
     * 按单据上下文解析应走的流程：在当前可见组织的启用绑定里，按优先级取第一个"谓词全命中"者，
     * 返回其不可变版本快照。无匹配返回 null（业务方回落到其默认流程）。RLS 保证不跨组织命中。
     */
    @Transactional(readOnly = true)
    public ProcessSnapshot resolve(String objectType, Map<String, Object> context) {
        for (ProcessBinding b : repository.findByObjectTypeAndStatusOrderBySeqAscIdAsc(objectType, "ACTIVE")) {
            if (matches(b.getCondition(), context)) {
                return new ProcessSnapshot(b.getId(), b.getObjectType(), b.getProcessDefKey(), b.getProcessVersion());
            }
        }
        return null;
    }

    // ---------- 条件谓词（内存求值，不拼 SQL / 不 eval） ----------

    /** 校验 condition 结构合法（谓词数组，每条 field/op/value）。 */
    private void validateCondition(String condition) {
        JsonNode node = readCondition(condition);
        JsonNode preds = node.path("predicates");
        if (preds.isMissingNode() || preds.isNull()) {
            return;   // 无谓词=兜底，合法
        }
        if (!preds.isArray()) {
            throw new IllegalArgumentException("condition.predicates 必须是数组");
        }
        for (JsonNode p : preds) {
            if (p.path("field").asText("").isBlank()) {
                throw new IllegalArgumentException("谓词缺少 field");
            }
        }
    }

    /** condition 的所有谓词都命中 context 才算匹配；空谓词=兜底恒真。 */
    private boolean matches(String condition, Map<String, Object> context) {
        JsonNode preds = readCondition(condition).path("predicates");
        if (!preds.isArray() || preds.isEmpty()) {
            return true;   // 兜底默认绑定
        }
        for (JsonNode p : preds) {
            if (!matchOne(p, context)) {
                return false;   // AND：一条不中即不匹配
            }
        }
        return true;
    }

    private boolean matchOne(JsonNode pred, Map<String, Object> context) {
        String field = pred.path("field").asText("");
        String op = pred.path("op").asText("eq");
        JsonNode value = pred.path("value");
        Object ctxObj = context == null ? null : context.get(field);
        if (ctxObj == null) {
            return false;   // 上下文没这个字段 → 不命中
        }
        String ctx = String.valueOf(ctxObj);
        switch (op) {
            case "ne":
                return !ctx.equals(value.asText());
            case "in":
                if (value.isArray()) {
                    for (JsonNode v : value) {
                        if (ctx.equals(v.asText())) {
                            return true;
                        }
                    }
                }
                return false;
            case "gt":
            case "lt":
            case "gte":
            case "lte": {
                Double a = toNum(ctx);
                Double b = toNum(value.asText());
                if (a == null || b == null) {
                    return false;
                }
                return switch (op) {
                    case "gt" -> a > b;
                    case "lt" -> a < b;
                    case "gte" -> a >= b;
                    default -> a <= b;
                };
            }
            case "eq":
            default:
                return ctx.equals(value.asText());
        }
    }

    private Double toNum(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode readCondition(String condition) {
        try {
            return MAPPER.readTree(condition == null || condition.isBlank() ? "{}" : condition);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("绑定 condition 不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    private ProcessBinding get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流程绑定不存在或不可见：id=" + id));
    }
}
