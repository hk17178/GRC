package com.mandao.grc;

import com.mandao.grc.modules.regulation.crawler.RegulationSource;
import com.mandao.grc.modules.regulation.crawler.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 采集源连续未抓到计数单元测试（收口批 B25）。纯逻辑，无 Spring/DB。
 * 验证 markFetched 的累加/清零：失败或零命中累加，正常命中清零——达阈值由调度器判定告警。
 */
class RegulationSourceMissTest {

    private RegulationSource newSource() {
        return new RegulationSource(12L, "测试源", SourceType.SAMPLE, null, null, "DAILY");
    }

    @Test
    void 失败累加零命中累加正常清零() {
        RegulationSource s = newSource();
        assertEquals(0, s.getConsecutiveMiss(), "初始为 0");

        s.markFetched(0, "网络错误");           // 失败
        assertEquals(1, s.getConsecutiveMiss());
        s.markFetched(0, null);                  // 零命中（无错但没抓到）
        assertEquals(2, s.getConsecutiveMiss());
        s.markFetched(0, "选择器失效");          // 再失败
        assertEquals(3, s.getConsecutiveMiss(), "连续三次未抓到");

        s.markFetched(5, null);                  // 正常命中 → 清零
        assertEquals(0, s.getConsecutiveMiss(), "成功抓取应清零");

        s.markFetched(0, "again");               // 再次累加从 0 起
        assertEquals(1, s.getConsecutiveMiss());
    }
}
