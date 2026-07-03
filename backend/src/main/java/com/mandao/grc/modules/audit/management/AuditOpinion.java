package com.mandao.grc.modules.audit.management;

/**
 * 审计意见四级（V47，报告定稿必选）。
 *
 * 参照内部审计通行做法按总体评价分级；分级标准由各机构内审制度细化。
 */
public enum AuditOpinion {
    /** 满意：内控健全有效，未见重大缺陷。 */
    SATISFACTORY,
    /** 基本满意：内控总体有效，存在个别需完善事项。 */
    GENERALLY_SATISFACTORY,
    /** 需改进：存在较重要缺陷，须限期整改。 */
    NEEDS_IMPROVEMENT,
    /** 不满意：存在重大缺陷或系统性失效。 */
    UNSATISFACTORY
}
