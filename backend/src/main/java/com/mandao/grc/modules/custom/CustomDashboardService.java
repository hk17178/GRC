package com.mandao.grc.modules.custom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.grc.modules.audit.HashChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义看板服务（B12 Phase5 / D1-8 §五，收官）。
 *
 * 看板 = 一组组件（指标卡 KPI / 报表 REPORT）+ 布局。红线：**组件不自取数**——每个组件只能引用
 * 已登记的 {@link KpiDef} / {@link CustomReportDef}，数据源一律走标准聚合接口（受 M8 + visibleOrgs）；
 * 渲染时逐组件解析（KPI 求值 / REPORT 执行），各自经统一访问层 + RLS——看板无路径越过 visibleOrgs。
 *
 * 组件校验在登记时：refId 必须命中当前可见范围内的启用聚合源（RLS 裁剪，跨组织引用不可见即拒）。
 */
@Service
public class CustomDashboardService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DashboardDefRepository repository;
    private final KpiDefRepository kpiRepository;
    private final CustomReportDefRepository reportRepository;
    private final KpiDefService kpiService;
    private final CustomReportService reportService;
    private final HashChainService hashChainService;

    public CustomDashboardService(DashboardDefRepository repository, KpiDefRepository kpiRepository,
                                  CustomReportDefRepository reportRepository, KpiDefService kpiService,
                                  CustomReportService reportService, HashChainService hashChainService) {
        this.repository = repository;
        this.kpiRepository = kpiRepository;
        this.reportRepository = reportRepository;
        this.kpiService = kpiService;
        this.reportService = reportService;
        this.hashChainService = hashChainService;
    }

    /** 单个组件解析结果（error 非空表示该组件源已失效/不可见，看板其余组件不受影响）。 */
    public record WidgetData(String type, String title, Long refId, Object data, String error) {
    }

    /** 看板渲染结果。 */
    public record DashboardRender(Long id, String name, List<WidgetData> widgets) {
    }

    @Transactional(readOnly = true)
    public List<DashboardDef> list() {
        return repository.findByOrderByIdAsc();
    }

    /** 登记看板（校验每个组件绑定的聚合源存在且可见——不自取数红线）。 */
    @Transactional
    public DashboardDef create(Long orgId, String name, String layout, String actor) {
        validateLayout(layout);
        DashboardDef d = new DashboardDef(orgId, name, layout, actor);
        DashboardDef saved = repository.save(d);
        hashChainService.append(orgId, "DASHBOARD_CREATE", actor, "DASHBOARD:" + saved.getId(),
                "登记自定义看板 name=" + name);
        return saved;
    }

    @Transactional
    public DashboardDef retire(Long id, String actor) {
        DashboardDef d = get(id);
        d.setStatus("RETIRED");
        DashboardDef saved = repository.save(d);
        hashChainService.append(d.getOrgId(), "DASHBOARD_RETIRE", actor, "DASHBOARD:" + id, "停用自定义看板");
        return saved;
    }

    /** 渲染已登记看板（逐组件解析，各走 RLS；单组件失效不影响其余）。 */
    @Transactional(readOnly = true)
    public DashboardRender render(Long id) {
        DashboardDef d = get(id);
        return new DashboardRender(d.getId(), d.getName(), resolveWidgets(d.getLayout()));
    }

    /** 预览临时布局（不落库，供构建器即时查看）。 */
    @Transactional(readOnly = true)
    public DashboardRender preview(String name, String layout) {
        return new DashboardRender(null, name, resolveWidgets(layout));
    }

    // ---------- 组件解析 ----------

    /** 遍历 widgets，逐个解析为数据（KPI 求值 / REPORT 执行）；单组件异常降级为 error。 */
    private List<WidgetData> resolveWidgets(String layout) {
        JsonNode widgets = readWidgets(layout);
        List<WidgetData> out = new ArrayList<>();
        for (JsonNode w : widgets) {
            String type = w.path("type").asText("");
            String title = w.path("title").asText("");
            Long refId = w.path("refId").isNumber() ? w.get("refId").asLong() : null;
            try {
                Object data = resolveOne(type, refId);
                out.add(new WidgetData(type, title, refId, data, null));
            } catch (RuntimeException e) {
                // 源被停用/删除/不可见——降级显示，看板不崩
                out.add(new WidgetData(type, title, refId, null, e.getMessage()));
            }
        }
        return out;
    }

    private Object resolveOne(String type, Long refId) {
        if (refId == null) {
            throw new IllegalArgumentException("组件缺少 refId");
        }
        // 先用仓储探源是否存在/可见（不进嵌套 @Transactional 抛异常——否则会把共享事务标记 rollback-only，
        // 即便外层 catch 也会在提交时 UnexpectedRollback）；缺源在此本地抛出，由上层降级为组件 error。
        return switch (type) {
            // 数据源一律走标准聚合接口（受 visibleOrgs + RLS），组件不自取数
            case "KPI" -> {
                if (kpiRepository.findById(refId).isEmpty()) {
                    throw new IllegalArgumentException("KPI 源不存在或不可见：id=" + refId);
                }
                yield kpiService.evaluate(refId);
            }
            case "REPORT" -> {
                if (reportRepository.findById(refId).isEmpty()) {
                    throw new IllegalArgumentException("报表源不存在或不可见：id=" + refId);
                }
                yield reportService.execute(refId);
            }
            default -> throw new IllegalArgumentException("不支持的组件类型：" + type);
        };
    }

    /** 登记校验：每个组件类型合法 + 绑定的聚合源存在且可见（不自取数红线）。 */
    private void validateLayout(String layout) {
        JsonNode widgets = readWidgets(layout);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("看板至少需要一个组件");
        }
        for (JsonNode w : widgets) {
            String type = w.path("type").asText("");
            if (!w.path("refId").isNumber()) {
                throw new IllegalArgumentException("组件缺少 refId");
            }
            long refId = w.get("refId").asLong();
            switch (type) {
                case "KPI" -> {
                    if (kpiRepository.findById(refId).isEmpty()) {
                        throw new IllegalArgumentException("KPI 组件绑定的指标不存在或不可见：id=" + refId);
                    }
                }
                case "REPORT" -> {
                    if (reportRepository.findById(refId).isEmpty()) {
                        throw new IllegalArgumentException("报表组件绑定的报表不存在或不可见：id=" + refId);
                    }
                }
                default -> throw new IllegalArgumentException("不支持的组件类型：" + type);
            }
        }
    }

    private JsonNode readWidgets(String layout) {
        try {
            JsonNode root = MAPPER.readTree(layout == null || layout.isBlank() ? "{}" : layout);
            JsonNode widgets = root.path("widgets");
            if (!widgets.isArray()) {
                throw new IllegalArgumentException("看板 layout 缺少 widgets 数组");
            }
            return widgets;
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("看板 layout 不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    private DashboardDef get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("自定义看板不存在或不可见：id=" + id));
    }
}
