package com.mandao.grc.modules.policy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 制度列表投影（七轮 7-8 / 评估报告 A6）：包含除 docBytes（docx 原件字节）外的全部字段。
 *
 * 背景：列表接口曾用 findAll() 加载实体——@JsonIgnore 只是不序列化，Hibernate 仍把每份
 * 制度原件（可达数 MB 的 bytea）拉进堆；制度到一定量级列表页/驾驶舱就是一次小型 OOM 演习。
 * 接口投影让 Hibernate 只 SELECT 这些列，原件字节永不出库（下载走专用端点）。
 */
public interface PolicySummary {

    Long getId();

    Long getOrgId();

    String getCode();

    String getTitle();

    String getContent();

    PolicyStatus getStatus();

    Integer getVersion();

    String getFramework();

    LocalDate getEffectiveDate();

    Integer getReviewCycleMonths();

    String getOwnerDept();

    String getOwner();

    String getDocName();

    String getDocSha256();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();
}
