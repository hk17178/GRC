package com.mandao.grc.modules.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * AI 治理条目（V42，平台级，无 org_id 不挂 RLS，与 ai_provider_config 同型）。
 *
 * kind='MODEL_WHITELIST'：name=允许接入的模型 id，detail=备注；
 * kind='PROMPT_TEMPLATE'：name=模板名，detail=系统提示词正文。
 */
@Entity
@Table(name = "ai_governance")
public class AiGovernance {

    /** 白名单条目类型。 */
    public static final String KIND_MODEL_WHITELIST = "MODEL_WHITELIST";
    /** 提示词模板类型。 */
    public static final String KIND_PROMPT_TEMPLATE = "PROMPT_TEMPLATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String kind;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected AiGovernance() {
    }

    public AiGovernance(String kind, String name, String detail, String actor) {
        this.kind = kind;
        this.name = name;
        this.detail = detail;
        this.updatedBy = actor;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** 更新名称/正文（由 Service 校验后调用）。 */
    public void update(String name, String detail, String actor) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.detail = detail;
        this.updatedBy = actor;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public String getDetail() { return detail; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
