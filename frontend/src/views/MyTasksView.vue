<template>
  <!--
    我的待办（MyTasksView）：跨模块归并的待处理工作。
    功能真源 = 后端 /api/workbench/todos（未验证整改 / 未完成合规项 / 待报送，RLS 按域）；视觉遵 tokens.css。
    注：当前为可见范围内待办；按登录人(角色/责任人)过滤的「我的」待鉴权接入后细化。
  -->
  <AppShell>
    <section class="view view-wb">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('todo.tag') }}</div>
          <h1>{{ $t('todo.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn ghost" @click="load">{{ $t('todo.refresh') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('todo.listTitle') }}</h3><span class="cnt">{{ todos.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 720px">
            <thead>
              <tr>
                <th>{{ $t('todo.th.type') }}</th>
                <th>{{ $t('todo.th.matter') }}</th>
                <th>{{ $t('todo.th.due') }}</th>
                <th>{{ $t('todo.th.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in todos" :key="i">
                <td><span class="tpill" :class="t.type">{{ $t('todo.type.' + t.type) }}</span></td>
                <td><span class="code">#{{ t.refId }}</span> {{ t.title }}</td>
                <td class="num">{{ t.dueDate || '—' }}</td>
                <td><span class="st"><span class="d"></span>{{ t.status }}</span></td>
              </tr>
              <tr v-if="!todos.length">
                <td colspan="4" class="emptyrow">{{ loadError || $t('todo.empty') }}</td>
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

const todos = ref([])
const loadError = ref('')
async function load() {
  loadError.value = ''
  try {
    todos.value = await api.get('/workbench/todos')
  } catch (e) {
    loadError.value = e.message
    todos.value = []
  }
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
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.tpill { display: inline-block; padding: 2px 9px; border-radius: 6px; font-size: 10.5px; font-weight: 700; background: rgba(120,120,120,0.1); color: var(--text-2); }
.tpill.REMEDIATION { background: var(--danger-tint); color: var(--danger); }
.tpill.COMPLIANCE_ITEM { background: var(--info-tint); color: var(--info); }
.tpill.REG_FILING { background: var(--warning-tint); color: #a87d22; }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
</style>
