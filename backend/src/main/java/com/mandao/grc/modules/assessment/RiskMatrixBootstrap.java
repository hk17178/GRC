package com.mandao.grc.modules.assessment;

import com.mandao.grc.common.isolation.IsolationContext;
import com.mandao.grc.modules.settings.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险矩阵档位启动加载（M2 深度包 C3）。
 *
 * 应用就绪后以集团（org 1）上下文读取 system_setting 键 {@code risk.matrix.bands}，
 * 覆写 {@link RiskMatrix} 的生效档位。键缺失或 JSON 非法时保持内置默认档并记日志——
 * 不阻断启动（定级永远有兜底）。设置项为系统锁定项（五级制为基线决策），调整走迁移+重启。
 */
@Component
public class RiskMatrixBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RiskMatrixBootstrap.class);

    /** 集团级配置键：五级定级档位 JSON。 */
    public static final String SETTING_KEY = "risk.matrix.bands";

    private final SystemSettingService settingService;

    public RiskMatrixBootstrap(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        IsolationContext.set(List.of(1L));
        try {
            settingService.findByKey(SETTING_KEY).ifPresentOrElse(s -> {
                try {
                    RiskMatrix.configure(s.getSettingValue());
                    log.info("风险矩阵档位已从 system_setting 加载：{}", s.getSettingValue());
                } catch (Exception e) {
                    log.warn("risk.matrix.bands 解析失败，沿用内置默认档：{}", e.getMessage());
                }
            }, () -> log.info("未配置 risk.matrix.bands，风险矩阵使用内置默认档"));
        } finally {
            IsolationContext.clear();
        }
    }
}
