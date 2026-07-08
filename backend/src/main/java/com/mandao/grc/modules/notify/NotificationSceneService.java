package com.mandao.grc.modules.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自定义通知场景服务（D1-8 §九，收官）。
 *
 * 设计态场景库 {@link NotifSceneDef}（全局，事件集→场景种类）→ 运行态装配 {@link NotificationScene}
 * （各组织：事件集→角色/层级→模板→通道→org_scope）+ 升级链 {@link NotificationEscalation}。
 *
 * 红线：
 *  1) 新增场景无需改码——从场景库挑 def 装配即可；
 *  2) 装配携 org_id + RLS，org_scope 仅 SELF/SUBTREE（本组织及下级），接收人始终在本组织自有子树内——
 *     {@link #assemble} 只返回当前可见组织的场景，绝不跨子公司广播；
 *  3) M10 消费装配结果（接收角色 + 通道 + 升级链），不改内核硬编码路径即可扩展场景。
 */
@Service
public class NotificationSceneService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> CHANNELS = Set.of("INBOX", "WECOM");
    private static final Set<String> ORG_SCOPES = Set.of("SELF", "SUBTREE");   // 绝不含跨子公司选项

    private final NotifSceneDefRepository defRepo;
    private final NotificationSceneRepository sceneRepo;
    private final NotificationEscalationRepository escRepo;
    private final HashChainService hashChainService;

    public NotificationSceneService(NotifSceneDefRepository defRepo, NotificationSceneRepository sceneRepo,
                                    NotificationEscalationRepository escRepo, HashChainService hashChainService) {
        this.defRepo = defRepo;
        this.sceneRepo = sceneRepo;
        this.escRepo = escRepo;
        this.hashChainService = hashChainService;
    }

    /** 升级链一级（装配结果用）。 */
    public record EscalationStep(int level, int delayHours, String escalateToRole) {
    }

    /** 登记升级链输入（创建场景时随附）。 */
    public record EscalationInput(int level, int delayHours, String escalateToRole) {
    }

    /** 装配结果：M10 据此决定通知谁、走哪通道、如何升级。 */
    public record AssembledScene(Long sceneId, String name, String channelType, String orgScope,
                                 List<String> recipientRoles, String template, List<EscalationStep> escalation) {
    }

    @Transactional(readOnly = true)
    public List<NotifSceneDef> listDefs() {
        return defRepo.findByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<NotificationScene> listScenes() {
        return sceneRepo.findByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<NotificationEscalation> escalationsOf(Long sceneId) {
        return escRepo.findBySceneIdAndStatusOrderByLevelAsc(sceneId, "ACTIVE");
    }

    /** 从场景库装配一个运行态场景（校验 def 存在 + 角色/模板/通道/范围合规 + 升级链），随附升级链原子落库。 */
    @Transactional
    public NotificationScene createScene(Long orgId, Long sceneDefId, String name, List<String> recipientRoles,
                                         String template, String channelType, String orgScope,
                                         List<EscalationInput> escalations, String actor) {
        if (defRepo.findById(sceneDefId).isEmpty()) {
            throw new IllegalArgumentException("场景库不存在该场景种类：id=" + sceneDefId);
        }
        if (recipientRoles == null || recipientRoles.isEmpty()) {
            throw new IllegalArgumentException("接收角色不能为空");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("消息模板不能为空");
        }
        String ch = channelType == null ? "INBOX" : channelType;
        if (!CHANNELS.contains(ch)) {
            throw new IllegalArgumentException("不支持的通道：" + channelType);
        }
        String scope = orgScope == null ? "SELF" : orgScope;
        if (!ORG_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("org_scope 仅允许 SELF/SUBTREE（不跨子公司广播）：" + orgScope);
        }
        String rolesJson = toJson(recipientRoles);
        NotificationScene scene = new NotificationScene(orgId, sceneDefId, name, rolesJson, template, ch, scope, actor);
        NotificationScene saved = sceneRepo.save(scene);

        if (escalations != null) {
            for (EscalationInput e : escalations) {
                if (e.level() < 1 || e.delayHours() < 0 || e.escalateToRole() == null || e.escalateToRole().isBlank()) {
                    throw new IllegalArgumentException("升级链非法：level≥1、delayHours≥0、角色非空");
                }
                escRepo.save(new NotificationEscalation(orgId, saved.getId(), e.level(), e.delayHours(), e.escalateToRole()));
            }
        }
        hashChainService.append(orgId, "NOTIF_SCENE_CREATE", actor, "NOTIF_SCENE:" + saved.getId(),
                "装配通知场景 " + name + "（通道 " + ch + "，范围 " + scope + "）");
        return saved;
    }

    @Transactional
    public NotificationScene retireScene(Long id, String actor) {
        NotificationScene s = sceneRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知场景不存在或不可见：id=" + id));
        s.setStatus("RETIRED");
        NotificationScene saved = sceneRepo.save(s);
        hashChainService.append(s.getOrgId(), "NOTIF_SCENE_RETIRE", actor, "NOTIF_SCENE:" + id, "停用通知场景");
        return saved;
    }

    /**
     * 运行态装配：给定事件类型，返回当前可见组织下所有"涵盖该事件"的启用场景及其接收角色/通道/升级链。
     * 只返回本组织场景（RLS），org_scope 限自有子树——不跨子公司广播。M10 据此投递。
     */
    @Transactional(readOnly = true)
    public List<AssembledScene> assemble(String eventType) {
        // 1) 全局库里匹配该事件类型的场景种类
        Set<Long> defIds = new HashSet<>();
        for (NotifSceneDef d : defRepo.findByOrderByIdAsc()) {
            if (eventTypesContains(d.getEventTypes(), eventType)) {
                defIds.add(d.getId());
            }
        }
        if (defIds.isEmpty()) {
            return List.of();
        }
        // 2) 本组织启用场景里引用这些种类的（RLS 仅返回可见组织，跨子公司永不出现）
        List<AssembledScene> out = new ArrayList<>();
        for (NotificationScene s : sceneRepo.findByStatusOrderByIdAsc("ACTIVE")) {
            if (!defIds.contains(s.getSceneDefId())) {
                continue;
            }
            List<EscalationStep> chain = escRepo.findBySceneIdAndStatusOrderByLevelAsc(s.getId(), "ACTIVE").stream()
                    .map(e -> new EscalationStep(e.getLevel(), e.getDelayHours(), e.getEscalateToRole()))
                    .toList();
            out.add(new AssembledScene(s.getId(), s.getName(), s.getChannelType(), s.getOrgScope(),
                    parseRoles(s.getRecipientRoles()), s.getTemplate(), chain));
        }
        return out;
    }

    // ---------- helpers ----------

    private boolean eventTypesContains(String eventTypesJson, String eventType) {
        try {
            JsonNode arr = MAPPER.readTree(eventTypesJson == null ? "[]" : eventTypesJson);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    if (eventType.equals(n.asText())) {
                        return true;
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JacksonException ignore) {
            // 脏数据当作不匹配
        }
        return false;
    }

    private List<String> parseRoles(String rolesJson) {
        List<String> roles = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(rolesJson == null ? "[]" : rolesJson);
            if (arr.isArray()) {
                arr.forEach(n -> roles.add(n.asText()));
            }
        } catch (com.fasterxml.jackson.core.JacksonException ignore) {
            // 脏数据返回空
        }
        return roles;
    }

    private String toJson(List<String> list) {
        try {
            return MAPPER.writeValueAsString(list);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("角色序列化失败");
        }
    }
}
