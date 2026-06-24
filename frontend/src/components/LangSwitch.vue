<!-- =============================================================
     语言切换器（中 / EN 胶囊）
     说明：复用原型 .lang 形态；切换中英并持久化到 localStorage。
     ============================================================= -->
<template>
  <div class="lang">
    <button
      :class="{ on: locale === 'zh' }"
      type="button"
      @click="setLang('zh')"
    >
      中
    </button>
    <button
      :class="{ on: locale === 'en' }"
      type="button"
      @click="setLang('en')"
    >
      EN
    </button>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { setLang } from '@/i18n.js'

// 当前语言（响应式），用于高亮选中态
const { locale } = useI18n()
</script>

<style scoped>
/* 形态与原型 .lang 严格一致 */
.lang {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 999px;
  overflow: hidden;
  background: var(--surface);
}
.lang button {
  border: 0;
  background: transparent;
  padding: 5px 13px;
  font-size: 12.5px;
  color: var(--text-2);
  cursor: pointer;
  font-family: inherit;
}
.lang button.on {
  background: var(--accent);
  color: #fff;
}
</style>
