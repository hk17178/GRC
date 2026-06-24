package com.mandao.grc.modules.org;

/**
 * 组织编码重复业务异常（M6 组织管理）。
 *
 * org.code 在 V1 上有 UNIQUE 约束；OrgService 建子组织前预检 code 唯一，
 * 命中则抛本异常，给出清晰业务语义（而非裸 DataIntegrityViolationException）。
 */
public class DuplicateOrgCodeException extends RuntimeException {

    public DuplicateOrgCodeException(String code) {
        super("组织编码已存在：code=" + code);
    }
}
