<!-- =============================================================
     占位页（PlaceholderView）
     说明：用于尚未复原原型的左侧菜单项。挂在 AppShell 内，
     保证所有导航项点击不报错、对应菜单可高亮。
       · 页名由路由 meta.navKey → i18n nav.<key> 动态取得；
       · 五主题 / 中英 i18n 自然生效（复用 AppShell 与 tokens）。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <!-- 页头：建设中标识 + 模块名 -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('placeholder.tag') }}</div>
          <h1>{{ pageName }}</h1>
        </div>
      </div>
      <!-- 占位说明卡 -->
      <div class="card">
        <div class="cb ph-body">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <path d="M3 9h18M8 4v5" />
          </svg>
          <p>{{ $t('placeholder.desc') }}</p>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AppShell from '@/components/AppShell.vue'

const route = useRoute()
const { t } = useI18n()

// 页名：取路由 meta.navKey 对应的 nav 文案（与左侧菜单同源）
const pageName = computed(() => {
  const key = route.meta?.navKey
  return key ? t('nav.' + key) : t('placeholder.tag')
})
</script>

<style scoped>
/* 与原型 .phead / .card / .cb 对齐 */
.phead {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
}
.phead .kqt {
  font-size: 10.5px;
  letter-spacing: 1.5px;
  color: var(--accent);
  text-transform: uppercase;
  font-weight: 700;
  margin-bottom: 4px;
}
.phead h1 {
  font-size: 20px;
  font-weight: 760;
  letter-spacing: -0.3px;
  font-family: var(--font-display);
}
.card {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}
.cb {
  padding: 14px 18px 18px;
}
.ph-body {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.6;
}
.ph-body svg {
  width: 34px;
  height: 34px;
  color: var(--accent);
  flex-shrink: 0;
}
</style>
