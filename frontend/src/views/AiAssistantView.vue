<template>
  <!--
    AI 智能问答（AiAssistantView · CR-004 RAG）：基于知识库的检索增强问答。
    功能真源 = 后端 /api/ai；视觉遵 tokens.css。诚实标注当前 AI 模式（本地离线/Claude），不伪造能力。隔离由后端 RLS。
  -->
  <AppShell>
    <section class="view view-ai">
      <div class="phead">
        <div><div class="kqt">{{ $t('ai.tag') }}</div><h1>{{ $t('ai.title') }}</h1></div>
        <div class="sp"></div>
        <span class="mode" :class="status.offline ? 'off' : 'on'">
          <span class="d"></span>{{ status.offline ? $t('ai.mode.local') : status.model }}
        </span>
      </div>

      <div class="g">
        <!-- 左：问答 -->
        <div class="card chat">
          <div class="cb">
            <div ref="msgsEl" class="msgs">
              <div v-if="!messages.length" class="placeholder">{{ $t('ai.placeholder') }}</div>
              <div v-for="(m, i) in messages" :key="i" class="turn">
                <div class="q"><span class="who">{{ $t('ai.you') }}</span>{{ m.question }}</div>
                <div class="a">
                  <span class="who">AI</span>
                  <span v-if="m.loading" class="muted">{{ $t('ai.thinking') }}</span>
                  <span v-else class="atext">{{ m.answer }}</span>
                  <div v-if="m.citations && m.citations.length" class="cites">
                    <div class="cl">{{ $t('ai.citations') }}</div>
                    <div v-for="(c, j) in m.citations" :key="j" class="cite">
                      <span class="cref">#{{ c.documentId }}·{{ c.seq }}</span>
                      <span class="cscore">{{ (c.score * 100).toFixed(0) }}%</span>
                      <span class="csnip">{{ c.snippet }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="ask-bar">
              <input v-model="q" :placeholder="$t('ai.askPh')" :disabled="asking" @keyup.enter="ask" />
              <button class="btn" :disabled="!q.trim() || asking" @click="ask">{{ asking ? $t('ai.asking') : $t('ai.ask') }}</button>
            </div>
          </div>
        </div>

        <!-- 右：知识库 -->
        <div class="card">
          <div class="ch">
            <h3>{{ $t('ai.kb') }}</h3>
            <span class="cnt">{{ docs.length }}</span>
            <button class="btn ghost sm" style="margin-left: auto" :disabled="!canWrite('ai')" :title="canWrite('ai') ? '' : $t('common.noPerm')" @click="openIngest">{{ $t('ai.ingest.btn') }}</button>
          </div>
          <div class="cb" style="padding-top: 0; overflow-x: auto">
            <table>
              <thead><tr><th>{{ $t('ai.dth.title') }}</th><th>{{ $t('ai.dth.type') }}</th><th>{{ $t('ai.dth.chunks') }}</th><th>{{ $t('ai.dth.status') }}</th></tr></thead>
              <tbody>
                <tr v-for="d in docs" :key="d.id">
                  <td>{{ d.title }}</td>
                  <td><span class="pill">{{ $t('ai.stype.' + d.sourceType) }}</span></td>
                  <td class="num">{{ d.chunkCount }}</td>
                  <td><span class="st" :class="d.status === 'INDEXED' ? 'ok' : 'wait'"><span class="d"></span>{{ $t('ai.dstatus.' + d.status) }}</span></td>
                </tr>
                <tr v-if="!docs.length"><td colspan="4" class="emptyrow">{{ $t('ai.kbEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 录入知识弹窗 -->
      <div v-if="showIngest" class="modal-mask" @click.self="showIngest = false">
        <div class="modal-card">
          <h3>{{ $t('ai.ingest.btn') }}</h3>
          <label class="fld">{{ $t('ai.dth.title') }}<input v-model="inf.title" /></label>
          <label class="fld">{{ $t('ai.dth.type') }}
            <select v-model="inf.sourceType"><option value="POLICY">{{ $t('ai.stype.POLICY') }}</option><option value="REGULATION">{{ $t('ai.stype.REGULATION') }}</option><option value="OBLIGATION">{{ $t('ai.stype.OBLIGATION') }}</option><option value="MANUAL">{{ $t('ai.stype.MANUAL') }}</option></select>
          </label>
          <label class="fld">{{ $t('ai.ingest.ref') }}<input v-model="inf.sourceRef" /></label>
          <label class="fld">{{ $t('ai.ingest.content') }}<textarea v-model="inf.content" rows="5" :placeholder="$t('ai.ingest.contentPh')"></textarea></label>
          <label class="fld">{{ $t('ai.ingest.org') }}<select v-model.number="inf.orgId"><option :value="12">{{ $t('ai.org.pay') }}</option><option :value="13">{{ $t('ai.org.consumer') }}</option></select></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showIngest = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!inf.title || !inf.content || saving" @click="submitIngest">{{ saving ? $t('common.submitting') : $t('ai.ingest.ok') }}</button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { canWrite } from '@/auth.js'

const status = reactive({ provider: 'local', model: 'local', offline: true, embeddingDim: 1024 })
const docs = ref([])
const messages = ref([])
const q = ref('')
const asking = ref(false)
const opError = ref('')
const saving = ref(false)
const msgsEl = ref(null)

async function loadStatus() { try { Object.assign(status, await api.get('/ai/status')) } catch (e) { /* 保持默认 */ } }
async function loadDocs() { try { docs.value = await api.get('/ai/documents') } catch (e) { docs.value = [] } }

async function ask() {
  const question = q.value.trim()
  if (!question || asking.value) return
  asking.value = true
  const turn = reactive({ question, answer: '', citations: [], loading: true })
  messages.value.push(turn)
  q.value = ''
  await scrollDown()
  try {
    const res = await api.post('/ai/ask', { question, topK: 4 })
    turn.answer = res.answer
    turn.citations = res.citations || []
  } catch (e) {
    turn.answer = '提问失败：' + e.message
  } finally {
    turn.loading = false
    asking.value = false
    await scrollDown()
  }
}
async function scrollDown() { await nextTick(); if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight }

const showIngest = ref(false)
const inf = reactive({ title: '', sourceType: 'POLICY', sourceRef: '', content: '', orgId: 12 })
function openIngest() { Object.assign(inf, { title: '', sourceType: 'POLICY', sourceRef: '', content: '', orgId: 12 }); opError.value = ''; showIngest.value = true }
async function submitIngest() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/ai/documents', { orgId: inf.orgId, title: inf.title, sourceType: inf.sourceType, sourceRef: inf.sourceRef, content: inf.content })
    showIngest.value = false; await loadDocs()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(() => { loadStatus(); loadDocs() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.mode { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; padding: 4px 11px; border-radius: 999px; border: 1px solid var(--surface-border); }
.mode .d { width: 6px; height: 6px; border-radius: 50%; }
.mode.off { color: #a87d22; background: var(--warning-tint); } .mode.off .d { background: #a87d22; }
.mode.on { color: var(--success); background: var(--success-tint, rgba(40,150,90,.1)); } .mode.on .d { background: var(--success); }
.g { display: grid; grid-template-columns: 1.7fr 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
.chat .cb { display: flex; flex-direction: column; height: 560px; }
.msgs { flex: 1; overflow-y: auto; padding-right: 4px; }
.placeholder { color: var(--text-3); font-size: 12.5px; text-align: center; padding: 40px 12px; }
.turn { margin-bottom: 16px; }
.turn .who { display: inline-block; font-size: 10px; font-weight: 700; color: var(--text-3); margin-right: 8px; vertical-align: top; }
.turn .q { font-size: 13px; font-weight: 600; color: var(--text-1); margin-bottom: 8px; }
.turn .a { font-size: 12.5px; color: var(--text-2); background: var(--bg); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); padding: 10px 12px; }
.atext { white-space: pre-wrap; }
.cites { margin-top: 10px; border-top: 1px dashed var(--border-subtle); padding-top: 8px; }
.cites .cl { font-size: 10px; color: var(--text-3); font-weight: 700; margin-bottom: 5px; }
.cite { display: flex; gap: 7px; align-items: baseline; font-size: 11px; padding: 2px 0; }
.cite .cref { color: var(--accent-strong); font-weight: 700; font-variant-numeric: tabular-nums; }
.cite .cscore { color: var(--success); font-weight: 600; font-variant-numeric: tabular-nums; }
.cite .csnip { color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ask-bar { display: flex; gap: 8px; margin-top: 12px; }
.ask-bar input { flex: 1; height: 38px; padding: 0 12px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 10px; }
tbody td { padding: 8px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
.num { font-variant-numeric: tabular-nums; }
.muted { color: var(--text-3); font-size: 12px; }
.pill { display: inline-block; padding: 2px 7px; border-radius: 6px; font-size: 10px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 16px 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 10px; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 480px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select, .modal-card .fld textarea { display: block; width: 100%; margin-top: 5px; padding: 8px 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; box-sizing: border-box; }
.modal-card .fld input, .modal-card .fld select { height: 38px; padding: 0 11px; }
.modal-card .fld textarea { resize: vertical; line-height: 1.5; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
