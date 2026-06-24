package com.mandao.grc.modules.control;

/**
 * 合规框架（统一控件库的"映射目标"）。
 *
 * 一个控制项可同时映射到多个框架的条款，从而"一次定义、多框架复用"——这是统一控件库的核心价值。
 */
public enum ControlFramework {
    /** 网络安全等级保护（等保 2.0）。 */
    MLPS,
    /** ISO/IEC 27001 信息安全管理体系。 */
    ISO27001,
    /** PCI DSS 支付卡行业数据安全标准。 */
    PCI_DSS,
    /** 人民银行相关监管要求。 */
    PBOC
}
