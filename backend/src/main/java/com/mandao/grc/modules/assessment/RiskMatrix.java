package com.mandao.grc.modules.assessment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 风险矩阵五级定级的唯一事实源（M2 深度包 C3）。
 *
 * 此前阈值在 {@code ScoringService}（3/6/12/20/25）与 {@code RiskScenario}（4/8/12/16）两处
 * 硬编码且互不一致——同一个 4×5=20 在 ATV 台账定「极高」、在表单打分定「高」。本类收敛为单一
 * 静态档位，并支持从 system_setting 键 {@code risk.matrix.bands}（集团 org=1、系统锁定项）加载：
 * JSON 形如 {@code [{"max":4,"level":"VERY_LOW"},...,{"max":25,"level":"VERY_HIGH"}]}。
 *
 * 五级制为 V1.9 基线决策（级数固定），故设置项锁定 editable=false——调整阈值走迁移+重启，
 * 运行期无需监听变更；加载失败/缺失时回落内置默认档（与 ATV 历史口径一致，生产行为不变）。
 */
public final class RiskMatrix {

    /** 一档：风险值（可能性×影响，1~25）上界 → 平台五级。 */
    public record Band(int max, RiskLevel level) {
    }

    /** 内置默认档（≤4 极低 / ≤8 低 / ≤12 中 / ≤16 高 / 其余 极高）。 */
    public static final List<Band> DEFAULT_BANDS = List.of(
            new Band(4, RiskLevel.VERY_LOW),
            new Band(8, RiskLevel.LOW),
            new Band(12, RiskLevel.MID),
            new Band(16, RiskLevel.HIGH),
            new Band(25, RiskLevel.VERY_HIGH));

    /** 当前生效档位（启动时由 RiskMatrixBootstrap 覆写；volatile 保证可见性）。 */
    private static volatile List<Band> current = DEFAULT_BANDS;

    /** 简易 JSON 解析（避免为此引 ObjectMapper 静态依赖）：抓取 max/level 对。 */
    private static final Pattern BAND = Pattern.compile(
            "\\{\\s*\"max\"\\s*:\\s*(\\d+)\\s*,\\s*\"level\"\\s*:\\s*\"([A-Z_]+)\"\\s*}");

    private RiskMatrix() {
    }

    /** 可能性 × 影响 → 五级。入参越界自动夹到 [1,5]。 */
    public static RiskLevel levelOf(int likelihood, int impact) {
        int score = clamp(likelihood) * clamp(impact);
        for (Band b : current) {
            if (score <= b.max()) {
                return b.level();
            }
        }
        return RiskLevel.VERY_HIGH;
    }

    /**
     * 用配置 JSON 覆写档位；格式非法/不足五档时抛 IllegalArgumentException（调用方决定是否回落默认）。
     */
    public static void configure(String bandsJson) {
        List<Band> parsed = new ArrayList<>();
        Matcher m = BAND.matcher(bandsJson == null ? "" : bandsJson);
        while (m.find()) {
            parsed.add(new Band(Integer.parseInt(m.group(1)), RiskLevel.valueOf(m.group(2))));
        }
        if (parsed.size() != 5) {
            throw new IllegalArgumentException("risk.matrix.bands 须恰为五档，实际解析出 " + parsed.size() + " 档");
        }
        parsed.sort(java.util.Comparator.comparingInt(Band::max));
        current = List.copyOf(parsed);
    }

    /** 恢复默认档（测试用）。 */
    public static void reset() {
        current = DEFAULT_BANDS;
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }
}
