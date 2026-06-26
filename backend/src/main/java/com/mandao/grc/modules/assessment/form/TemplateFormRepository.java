package com.mandao.grc.modules.assessment.form;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 表单模板仓储（查询自动受 RLS 裁剪）。 */
public interface TemplateFormRepository extends JpaRepository<TemplateForm, Long> {

    /** 取某评估模板下的全部表单版本（按版本号倒序）。 */
    List<TemplateForm> findByTemplateIdOrderByVersionNoDesc(Long templateId);

    /** 取某评估模板当前 ACTIVE 的表单（运行期渲染/启动绑定用）。 */
    Optional<TemplateForm> findFirstByTemplateIdAndStatus(Long templateId, String status);
}
