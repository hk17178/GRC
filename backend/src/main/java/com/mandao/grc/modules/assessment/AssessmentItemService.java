package com.mandao.grc.modules.assessment;

import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评估项业务服务（M2 评估-控件复用的使用侧）。
 *
 * 隔离/留痕范式同其它模块。对实例化得到的评估项逐条给出符合性结论 {@link AssessmentItemResult}。
 */
@Service
public class AssessmentItemService {

    private final AssessmentItemRepository itemRepository;
    private final HashChainService hashChainService;

    public AssessmentItemService(AssessmentItemRepository itemRepository,
                                 HashChainService hashChainService) {
        this.itemRepository = itemRepository;
        this.hashChainService = hashChainService;
    }

    /** 按序列出某评估的评估项（受 RLS 裁剪）。 */
    @Transactional(readOnly = true)
    public List<AssessmentItem> listByAssessment(Long assessmentId) {
        return itemRepository.findByAssessmentIdOrderBySeqAsc(assessmentId);
    }

    /** 按 id 取评估项（不可见则视为不存在）。 */
    @Transactional(readOnly = true)
    public AssessmentItem get(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评估项不存在或不可见：id=" + id));
    }

    /**
     * 评估单个评估项：回写结论与说明并留痕。
     *
     * @param result     符合性结论
     * @param conclusion 说明/证据（可空）
     * @param actor      操作人（留痕）
     */
    @Transactional
    public AssessmentItem assess(Long itemId, AssessmentItemResult result, String conclusion, String actor) {
        AssessmentItem item = get(itemId);
        item.assess(result, conclusion);
        AssessmentItem saved = itemRepository.save(item);
        hashChainService.append(item.getOrgId(), "ITEM_ASSESS", actor,
                "ASSESSMENT_ITEM:" + item.getId(),
                "评估项结论=" + result + " seq=" + item.getSeq()
                        + (item.getControlId() == null ? "" : " control=" + item.getControlId()));
        return saved;
    }
}
