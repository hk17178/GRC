<template>
  <!--
    看板与留痕（BoardView）：防篡改操作留痕查询 + 哈希链完整性校验。
    功能真源 = 后端 /api/audit-trail（查询）与 /api/audit-trail/verify（校验）；视觉遵 tokens.css 设计语言。
    隔离：留痕仅返回当前主体可见组织（后端 RLS）；链校验逐 org。
  -->
  <AppShell>
    <section class="view view-board">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('board.tag') }}</div>
          <h1>{{ $t('board.title') }}</h1>
        </div>
      </div>

      <!-- 链完整性校验（防篡改卖点）-->
      <div class="card">
        <div class="ch">
          <h3>{{ $t('board.verify.title') }}</h3>
          <span class="redline-badge">{{ $t('board.verify.badge') }}</span>
        </div>
        <div class="cb verify-row">
          <label class="inline">{{ $t('board.verify.org') }}
            <select v-model.number="verifyOrg">
              <option :value="1">{{ $t('board.org.group') }}</option>
              <option :value="12">{{ $t('board.org.pay') }}</option>
              <option :value="13">{{ $t('board.org.consumer') }}</option>
            </select>
          </label>
          <button class="btn" @click="doVerify">{{ $t('board.verify.run') }}</button>
          <span v-if="verifyResult" class="verify-out" :class="verifyResult.valid ? 'ok' : 'bad'">
            <template v-if="verifyResult.valid">✓ {{ $t('board.verify.valid', { n: verifyResult.count }) }}</template>
            <template v-else>✗ {{ $t('board.verify.broken', { seq: verifyResult.brokenAtSeq }) }} — {{ verifyResult.reason }}</template>
          </span>
        </div>
      </div>

      <!-- 操作留痕查询 -->
      <div class="card">
        <div class="ch"><h3>{{ $t('board.trail.title') }}</h3></div>
        <div class="cb filter-row">
          <input v-model="fEntity" :placeholder="$t('board.trail.entityPh')" />
          <input v-model="fAction" :placeholder="$t('board.trail.actionPh')" />
          <input v-model="fActor" :placeholder="$t('board.trail.actorPh')" />
          <button class="btn" @click="loadTrail">{{ $t('board.trail.query') }}</button>
        </div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 900px">
            <thead>
              <tr>
                <th>{{ $t('board.trail.th.seq') }}</th>
                <th>{{ $t('board.trail.th.time') }}</th>
                <th>{{ $t('board.trail.th.action') }}</th>
                <th>{{ $t('board.trail.th.actor') }}</th>
                <th>{{ $t('board.trail.th.entity') }}</th>
                <th>{{ $t('board.trail.th.detail') }}</th>
                <th>{{ $t('board.trail.th.hash') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in trail" :key="i">
                <td class="num">{{ r.seq }}</td>
                <td class="num">{{ fmtTime(r.createdAtMs) }}</td>
                <td><span class="action">{{ r.action }}</span></td>
                <td>{{ r.actor }}</td>
                <td class="code">{{ r.entity }}</td>
                <td class="detail">{{ r.detail }}</td>
                <td class="hash" :title="r.currHash">{{ shortHash(r.currHash) }}</td>
              </tr>
              <tr v-if="!trail.length">
                <td colspan="7" class="emptyrow">{{ trailError || $t('board.trail.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
// 看板与留痕：接真实后端留痕查询与链校验
import { ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const trail = ref([])
const trailError = ref('')
const fEntity = ref('')
const fAction = ref('')
const fActor = ref('')

async function loadTrail() {
  trailError.value = ''
  const qs = new URLSearchParams()
  if (fEntity.value.trim()) qs.set('entity', fEntity.value.trim())
  if (fAction.value.trim()) qs.set('action', fAction.value.trim())
  if (fActor.value.trim()) qs.set('actor', fActor.value.trim())
  qs.set('limit', '200')
  try {
    trail.value = await api.get('/audit-trail?' + qs.toString())
  } catch (e) {
    trailError.value = e.message
    trail.value = []
  }
}

// ---- 链完整性校验 ----
const verifyOrg = ref(12)
const verifyResult = ref(null)
async function doVerify() {
  try {
    verifyResult.value = await api.get('/audit-trail/verify?orgId=' + verifyOrg.value)
  } catch (e) {
    verifyResult.value = { valid: false, brokenAtSeq: -1, reason: e.message }
  }
}

function fmtTime(ms) {
  if (!ms) return '—'
  return new Date(ms).toLocaleString()
}
function shortHash(h) {
  return h ? h.slice(0, 12) + '…' : '—'
}

onMounted(loadTrail)
</script>

<style scoped>
.phead {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
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
  margin-bottom: 14px;
}
.ch {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px 4px;
}
.ch h3 {
  font-size: 14px;
  font-weight: 720;
  font-family: var(--font-display);
}
.cb {
  padding: 14px 18px 18px;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  border: 0;
  border-radius: var(--radius-md);
  padding: 8px 16px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-1);
}
.redline-badge {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--success);
  background: var(--success-tint, rgba(40, 140, 80, 0.1));
  border: 1px solid var(--success);
}
.verify-row,
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.inline {
  font-size: 12.5px;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.verify-row select,
.filter-row input {
  height: 36px;
  padding: 0 11px;
  border: 1px solid var(--border, var(--surface-border));
  border-radius: var(--radius-md);
  background: var(--bg);
  color: var(--text-1);
  font-size: 13px;
  font-family: inherit;
  outline: none;
}
.filter-row input {
  min-width: 180px;
}
.verify-out {
  font-size: 13px;
  font-weight: 600;
}
.verify-out.ok {
  color: var(--success);
}
.verify-out.bad {
  color: var(--danger);
}
table {
  width: 100%;
  border-collapse: collapse;
}
thead th {
  text-align: left;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-3);
  padding: 0 14px 10px;
}
tbody td {
  padding: 9px 14px;
  border-top: 1px solid var(--border-subtle);
  font-size: 12px;
  vertical-align: top;
}
.num {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.code {
  font-weight: 600;
  color: var(--accent-strong);
}
.action {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  background: rgba(120, 120, 120, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
}
.detail {
  color: var(--text-2);
  max-width: 360px;
}
.hash {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  color: var(--text-3);
}
.emptyrow {
  text-align: center;
  color: var(--text-2);
  padding: 18px 0;
}
</style>
