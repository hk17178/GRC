<template>
  <!--
    全局 AI 合规助手侧滑面板（AI 深度包 A22：修复"点了没反应"的悬浮按钮）。
    - 所有页由 AppShell 的悬浮按钮唤起，右侧滑出；
    - B26 多轮对话：保留本会话历史并随每次提问回传，追问有上下文；
    - B32 反馈：每条回答下方赞/踩，踩可填原因，入库供质量分析。
    功能真源 = 后端 /api/ai/ask、/api/ai/feedback；视觉遵 tokens.css。
  -->
  <transition name="ai-slide">
    <div v-if="open" class="ai-panel-mask" @click.self="$emit('close')">
      <aside class="ai-panel">
        <header class="ai-head">
          <div class="ttl">
            <span class="badge">AI</span>
            <div>
              <h3>{{ $t('aiPanel.title') }}</h3>
              <span class="mode" :class="status.offline ? 'off' : 'on'"><span class="d"></span>{{ status.offline ? $t('ai.mode.local') : status.model }}</span>
            </div>
          </div>
          <div class="acts">
            <button class="ic" :title="$t('aiPanel.clear')" @click="clear">⟲</button>
            <button class="ic" :title="$t('common.cancel')" @click="$emit('close')">✕</button>
          </div>
        </header>

        <div ref="msgsEl" class="ai-msgs">
          <div v-if="!messages.length" class="ai-empty">
            <p>{{ $t('aiPanel.intro') }}</p>
            <div class="chips">
              <button v-for="s in suggestions" :key="s" class="chip" @click="askOne(s)">{{ s }}</button>
            </div>
          </div>
          <div v-for="(m, i) in messages" :key="i" class="turn">
            <div class="q">{{ m.question }}</div>
            <div class="a">
              <span v-if="m.loading" class="muted">{{ $t('ai.thinking') }}</span>
              <template v-else>
                <div class="atext">{{ m.answer }}</div>
                <div v-if="m.citations && m.citations.length" class="cites">
                  <div class="cl">{{ $t('ai.citations') }}</div>
                  <div v-for="(c, j) in m.citations" :key="j" class="cite">
                    <span class="cref">#{{ c.documentId }}·{{ c.seq }}</span>
                    <span class="cscore">{{ (c.score * 100).toFixed(0) }}%</span>
                    <span class="csnip">{{ c.snippet }}</span>
                  </div>
                </div>
                <!-- B32 反馈 -->
                <div class="fb" v-if="!m.error">
                  <template v-if="!m.fb">
                    <span class="fl">{{ $t('aiPanel.fb.ask') }}</span>
                    <button class="fbtn" @click="sendFb(m, true)">👍</button>
                    <button class="fbtn" @click="openFbNeg(m)">👎</button>
                  </template>
                  <span v-else class="fdone">{{ m.fb === 'up' ? $t('aiPanel.fb.thanksUp') : $t('aiPanel.fb.thanksDown') }}</span>
                </div>
                <div v-if="m.fbNegOpen" class="fbneg">
                  <input v-model="m.fbReason" :placeholder="$t('aiPanel.fb.reasonPh')" @keyup.enter="sendFb(m, false)" />
                  <button class="btn sm" @click="sendFb(m, false)">{{ $t('aiPanel.fb.submit') }}</button>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="ai-ask">
          <textarea v-model="q" rows="3" :placeholder="$t('ai.askPh') + '（Enter 发送，Shift+Enter 换行）'" :disabled="asking"
                    @keydown.enter.exact.prevent="ask"></textarea>
          <button class="btn" :disabled="!q.trim() || asking" @click="ask">{{ asking ? $t('ai.asking') : $t('ai.ask') }}</button>
        </div>
      </aside>
    </div>
  </transition>
</template>

<script setup>
import { ref, reactive, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/client.js'

const props = defineProps({ open: Boolean })
defineEmits(['close'])
const { t } = useI18n()

const status = reactive({ provider: 'local', model: 'local', offline: true })
const messages = ref([])
const q = ref('')
const asking = ref(false)
const msgsEl = ref(null)
let statusLoaded = false

const suggestions = [
  '反洗钱客户身份识别有哪些要求？',
  '等保三级系统需要多久做一次测评？',
  '数据跨境传输要走哪些合规流程？'
]

// 面板首次打开时懒加载 AI 模式（诚实标注本地/在线）
watch(() => props.open, (v) => {
  if (v && !statusLoaded) {
    statusLoaded = true
    api.get('/ai/status').then((s) => Object.assign(status, s)).catch(() => {})
  }
  if (v) scrollDown()
})

async function scrollDown() { await nextTick(); if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight }

function askOne(text) { q.value = text; ask() }

async function ask() {
  const question = q.value.trim()
  if (!question || asking.value) return
  asking.value = true
  // B26：回传已成型的历史轮（不含本轮、不含出错轮）
  const history = messages.value
    .filter((m) => !m.loading && !m.error)
    .map((m) => ({ question: m.question, answer: m.answer }))
  const turn = reactive({ question, answer: '', citations: [], loading: true, error: false, fb: null, fbNegOpen: false, fbReason: '' })
  messages.value.push(turn)
  q.value = ''
  await scrollDown()
  try {
    const res = await api.post('/ai/ask', { question, topK: 4, history })
    turn.answer = res.answer
    turn.citations = res.citations || []
  } catch (e) {
    turn.answer = t('aiPanel.askFail') + (e.message || e)
    turn.error = true
  } finally {
    turn.loading = false
    asking.value = false
    await scrollDown()
  }
}

function openFbNeg(m) { m.fbNegOpen = true }

// B32：提交反馈（问/答摘要随反馈上送，供后端质量分析）
async function sendFb(m, helpful) {
  try {
    await api.post('/ai/feedback', {
      question: m.question, answer: m.answer, helpful, reason: helpful ? null : (m.fbReason || null)
    })
    m.fb = helpful ? 'up' : 'down'
    m.fbNegOpen = false
  } catch (e) { /* 反馈失败静默，不打断问答 */ }
}

function clear() { messages.value = []; q.value = '' }
</script>

<style scoped>
/* 左下角浮动对话窗（非全屏遮罩；点窗外关闭）——不再右侧长条 */
.ai-panel-mask { position: fixed; inset: 0; background: transparent; z-index: 60; display: flex; align-items: flex-end; justify-content: flex-start; }
.ai-panel { width: 420px; max-width: calc(100vw - 40px); height: 560px; max-height: 76vh; margin: 0 0 84px 20px; background: var(--surface); border: 1px solid var(--surface-border); border-radius: 16px; box-shadow: var(--shadow-2); display: flex; flex-direction: column; overflow: hidden; }
.ai-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border-subtle); }
.ai-head .ttl { display: flex; align-items: center; gap: 10px; }
.ai-head h3 { font-size: 14.5px; font-weight: 740; font-family: var(--font-display); margin: 0; }
.ai-head .badge { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: 9px; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; font-size: 11px; font-weight: 800; }
.mode { display: inline-flex; align-items: center; gap: 5px; font-size: 10.5px; font-weight: 600; margin-top: 2px; }
.mode .d { width: 5px; height: 5px; border-radius: 50%; }
.mode.off { color: #a87d22; } .mode.off .d { background: #a87d22; }
.mode.on { color: var(--success); } .mode.on .d { background: var(--success); }
.acts { display: flex; gap: 4px; }
.ic { border: 0; background: none; color: var(--text-3); font-size: 15px; cursor: pointer; width: 28px; height: 28px; border-radius: 6px; }
.ic:hover { background: var(--bg); color: var(--text-1); }
.ai-msgs { flex: 1; overflow-y: auto; padding: 14px 16px; }
.ai-empty { color: var(--text-3); font-size: 12.5px; padding-top: 12px; }
.ai-empty p { margin: 0 0 12px; }
.chips { display: flex; flex-direction: column; gap: 8px; }
.chip { text-align: left; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: var(--radius-md); padding: 8px 11px; font-size: 12px; cursor: pointer; font-family: inherit; }
.chip:hover { border-color: var(--accent); color: var(--text-1); }
.turn { margin-bottom: 16px; }
.turn .q { font-size: 13px; font-weight: 600; color: var(--text-1); margin-bottom: 8px; }
.turn .a { font-size: 12.5px; color: var(--text-2); background: var(--bg); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); padding: 10px 12px; }
.atext { white-space: pre-wrap; line-height: 1.65; }
.muted { color: var(--text-3); }
.cites { margin-top: 10px; border-top: 1px dashed var(--border-subtle); padding-top: 8px; }
.cites .cl { font-size: 10px; color: var(--text-3); font-weight: 700; margin-bottom: 5px; }
.cite { display: flex; gap: 7px; align-items: baseline; font-size: 11px; padding: 2px 0; }
.cite .cref { color: var(--accent-strong); font-weight: 700; font-variant-numeric: tabular-nums; }
.cite .cscore { color: var(--success); font-weight: 600; font-variant-numeric: tabular-nums; }
.cite .csnip { color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fb { margin-top: 9px; display: flex; align-items: center; gap: 6px; }
.fb .fl { font-size: 10.5px; color: var(--text-3); }
.fbtn { border: 1px solid var(--surface-border); background: var(--surface); border-radius: 6px; cursor: pointer; font-size: 12px; padding: 2px 7px; }
.fbtn:hover { border-color: var(--accent); }
.fdone { font-size: 10.5px; color: var(--success); font-weight: 600; }
.fbneg { display: flex; gap: 6px; margin-top: 8px; }
.fbneg input { flex: 1; height: 30px; padding: 0 9px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; font-family: inherit; outline: none; }
.ai-ask { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid var(--border-subtle); align-items: flex-end; }
.ai-ask textarea { flex: 1; padding: 8px 12px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; line-height: 1.5; outline: none; resize: vertical; min-height: 62px; max-height: 160px; box-sizing: border-box; }
.ai-ask textarea:focus { border-color: var(--accent); }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; }
.btn.sm { padding: 5px 11px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
/* 侧滑动画 */
.ai-slide-enter-active, .ai-slide-leave-active { transition: opacity 0.18s ease; }
.ai-slide-enter-active .ai-panel, .ai-slide-leave-active .ai-panel { transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1); }
.ai-slide-enter-from, .ai-slide-leave-to { opacity: 0; }
.ai-slide-enter-from .ai-panel, .ai-slide-leave-to .ai-panel { transform: translateY(24px) scale(0.97); }
</style>
