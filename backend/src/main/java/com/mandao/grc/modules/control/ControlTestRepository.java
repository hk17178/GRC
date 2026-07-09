package com.mandao.grc.modules.control;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 控件测试台账仓储（B20）。不写 org 过滤：RLS 自动裁剪可见行。 */
public interface ControlTestRepository extends JpaRepository<ControlTest, Long> {

    /** 某控件的测试历史（最新在前）。 */
    List<ControlTest> findByControlIdOrderByIdDesc(Long controlId);

    /**
     * 复用查询：某控件「有效（EFFECTIVE）且有效期未过（valid_until ≥ today）」的最近一条，
     * 取有效期最远者（复用窗口最长）。无则空。
     */
    Optional<ControlTest> findFirstByControlIdAndResultAndValidUntilGreaterThanEqualOrderByValidUntilDesc(
            Long controlId, String result, LocalDate today);
}
