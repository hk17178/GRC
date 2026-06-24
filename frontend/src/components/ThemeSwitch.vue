<!-- =============================================================
     主题切换器（胶囊式色点按钮）
     说明：完全复用原型 .themesel/.tp 形态；点击切换并持久化主题。
     在登录页与 App 外壳顶栏共用。
     ============================================================= -->
<template>
  <div class="themesel">
    <span class="tl">{{ $t('common.theme') }}</span>
    <button
      v-for="t in THEME_LIST"
      :key="t.key"
      class="tp"
      :class="{ on: currentTheme === t.key }"
      :style="{ '--c': t.color }"
      type="button"
      @click="setTheme(t.key)"
    >
      <i></i>{{ $t('themes.' + t.key) }}
    </button>
  </div>
</template>

<script setup>
// 引入主题列表与切换方法（与原型逻辑一致）
import { THEME_LIST, currentTheme, setTheme } from '@/theme.js'
</script>

<style scoped>
/* 形态与原型 .themesel/.tp 严格一致 */
.themesel {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.themesel .tl {
  font-size: 12px;
  color: var(--text-3);
  margin-right: 1px;
}
.tp {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 999px;
  padding: 5px 11px 5px 8px;
  font-size: 12.5px;
  color: var(--text-2);
  cursor: pointer;
  font-family: inherit;
  transition: 0.18s;
}
.tp i {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--c);
  box-shadow: 0 0 0 2px var(--surface), 0 0 0 3px var(--c);
}
.tp:hover {
  border-color: var(--c);
}
.tp.on {
  border-color: var(--c);
  color: var(--text-1);
  box-shadow: 0 0 0 1px var(--c) inset;
  background: var(--accent-tint);
}
@media (max-width: 900px) {
  .themesel .tl {
    display: none;
  }
}
</style>
