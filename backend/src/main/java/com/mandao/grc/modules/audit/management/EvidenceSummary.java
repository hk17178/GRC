package com.mandao.grc.modules.audit.management;

import java.time.OffsetDateTime;

/**
 * 证据列表投影（七轮 7-8 / 评估报告 A6）：除文件字节（data bytea）外的全部字段。
 * 列表/取证台账只需要元数据与指纹；原件字节仅在下载/反向取证时按 id 单取。
 */
public interface EvidenceSummary {

    Long getId();

    Long getOrgId();

    Long getPlanId();

    Long getFindingId();

    Long getRemediationId();

    Long getFilingId();

    Long getIncidentId();

    String getName();

    String getFileName();

    String getContentType();

    String getSha256();

    String getUploadedBy();

    OffsetDateTime getUploadedAt();
}
