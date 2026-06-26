package com.mandao.grc.modules.regulation.crawler;

/**
 * 追踪源类型。
 *
 * SAMPLE：内置示例源——返回一批演示法规，不外联，默认即可用（类比 AI 的本地离线 Provider）。
 * HTTP：按配置的 URL + CSS 选择器抓取真实站点（由运营按合规要求自行配置目标站）。
 */
public enum SourceType {
    SAMPLE,
    HTTP
}
