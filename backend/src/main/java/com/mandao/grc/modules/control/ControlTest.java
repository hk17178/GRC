package com.mandao.grc.modules.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 控件有效性测试记录（B20 控件测试复用），映射 V79 control_test 表。
 *
 * 一个控制项可被反复测试，每条记结论 + 有效期；「有效且未过期」的 EFFECTIVE 结论可被
 * 新的审计/评估复用（不必重测）。携 org_id 隔离锚点，RLS 按 app.visible_orgs 裁剪。
 */
@Entity
@Table(name = "control_test")
public class ControlTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "control_id", nullable = false, updatable = false)
    private Long controlId;

    /** DESIGN（设计有效性）/ OPERATING（运行有效性）。 */
    @Column(name = "test_type", nullable = false, length = 16)
    private String testType;

    /** EFFECTIVE / DEFICIENT / PARTIAL。 */
    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "tested_by", length = 64)
    private String testedBy;

    @Column(name = "tested_at", updatable = false)
    private OffsetDateTime testedAt;

    /** 结论有效期上界（复用窗口；NULL 不参与复用）。 */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    protected ControlTest() {
    }

    public ControlTest(Long orgId, Long controlId, String testType, String result,
                       String testedBy, LocalDate validUntil, String note) {
        this.orgId = orgId;
        this.controlId = controlId;
        this.testType = testType;
        this.result = result;
        this.testedBy = testedBy;
        this.validUntil = validUntil;
        this.note = note;
        this.testedAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.testedAt == null) {
            this.testedAt = now;
        }
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getControlId() { return controlId; }
    public String getTestType() { return testType; }
    public String getResult() { return result; }
    public String getTestedBy() { return testedBy; }
    public OffsetDateTime getTestedAt() { return testedAt; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
