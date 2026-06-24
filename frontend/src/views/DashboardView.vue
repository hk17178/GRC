<!-- =============================================================
     仪表盘页（占位）
     说明：用 AppShell 外壳包裹一个占位内容；后续按驾驶舱版原型
     逐步复原 KPI、热力矩阵、KRI 监控等组件。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <div class="phead">
        <h1>
          {{ $t('dashboard.title') }}
          <small>{{ $t('dashboard.subtitle') }}</small>
        </h1>
      </div>
      <div class="ph-card">{{ $t('dashboard.placeholder') }}</div>
    </section>
  </AppShell>
</template>

<script setup>
import AppShell from '@/components/AppShell.vue'
</script>

<style scoped>
.phead {
  margin-bottom: 18px;
}
.phead h1 {
  font-size: var(--fs-display);
  font-weight: 740;
  font-family: var(--font-display);
  color: var(--text-1);
}
.phead h1 small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-3);
  margin-left: 8px;
}
.ph-card {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
  padding: 28px;
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.8;
}
</style>
