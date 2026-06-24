<template>
  <!--
    通知中心（NotifyView）：调度内核派发的提醒（法定时限/外审临近等）。
    功能真源 = 后端 /api/workbench/notifications（reminder_dispatch_log 经可见组织过滤）；视觉遵 tokens.css。
  -->
  <AppShell>
    <section class="view view-wb">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('notify.tag') }}</div>
          <h1>{{ $t('notify.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn ghost" @click="load">{{ $t('notify.refresh') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('notify.listTitle') }}</h3><span class="cnt">{{ items.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 720px">
            <thead>
              <tr>
                <th>{{ $t('notify.th.time') }}</th>
                <th>{{ $t('notify.th.event') }}</th>
                <th>{{ $t('notify.th.object') }}</th>
                <th>{{ $t('notify.th.threshold') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(n, i) in items" :key="i">
                <td class="num">{{ fmtTime(n.createdAtMs) }}</td>
                <td><span class="evt">{{ n.eventType }}</span></td>
                <td class="code">{{ n.objectType }}:{{ n.objectId }}</td>
                <td class="num">{{ n.thresholdKey }}</td>
              </tr>
              <tr v-if="!items.length">
                <td colspan="4" class="emptyrow">{{ loadError || $t('notify.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const items = ref([])
const loadError = ref('')
async function load() {
  loadError.value = ''
  try {
    items.value = await api.get('/workbench/notifications')
  } catch (e) {
    loadError.value = e.message
    items.value = []
  }
}
function fmtTime(ms) {
  return ms ? new Date(ms).toLocaleString() : '—'
}
onMounted(load)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; letter-spacing: -0.3px; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 10px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
.num { font-variant-numeric: tabular-nums; white-space: nowrap; }
.code { font-weight: 600; color: var(--accent-strong); }
.evt { font-family: var(--font-mono, monospace); font-size: 11px; background: var(--warning-tint); color: #a87d22; padding: 1px 7px; border-radius: 4px; font-weight: 600; }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
</style>
