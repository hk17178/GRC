<template>
  <!--
    模型接入（ModelAccessView · CR-004）：AI 提供方/嵌入配置「状态展示 + 切换指引」。
    功能真源 = 后端 /api/ai/status。安全：密钥经部署环境变量注入，<b>不</b>在 UI 录入/展示密钥（无密钥输入框）。
  -->
  <AppShell>
    <section class="view view-model">
      <div class="phead">
        <div><div class="kqt">{{ $t('aimodel.tag') }}</div><h1>{{ $t('aimodel.title') }}</h1></div>
        <div class="sp"></div>
        <button class="btn ghost sm" @click="loadStatus">{{ $t('aimodel.refresh') }}</button>
      </div>

      <div class="g">
        <!-- 当前状态 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('aimodel.current') }}</h3></div>
          <div class="cb">
            <div class="row"><span class="k">{{ $t('aimodel.llm') }}</span>
              <span class="st" :class="status.offline ? 'wait' : 'ok'"><span class="d"></span>{{ status.offline ? $t('aimodel.local') : status.provider + ' · ' + status.model }}</span>
            </div>
            <div class="row"><span class="k">{{ $t('aimodel.embedding') }}</span><b>{{ $t('aimodel.embLocal') }} · {{ status.embeddingDim }}d</b></div>
            <div class="row"><span class="k">{{ $t('aimodel.network') }}</span>
              <span class="st" :class="status.offline ? 'ok' : 'wait'"><span class="d"></span>{{ status.offline ? $t('aimodel.noEgress') : $t('aimodel.egress') }}</span>
            </div>
            <p class="note">{{ status.offline ? $t('aimodel.localNote') : $t('aimodel.claudeNote') }}</p>
          </div>
        </div>

        <!-- Web 配置（运营在界面配 provider + API Key，密钥加密存储不回显） -->
        <div class="card">
          <div class="ch"><h3>大模型接入配置</h3><span class="sub">集团统一 · 密钥加密存储</span></div>
          <div class="cb">
            <label class="fld">提供方
              <select v-model="cfg.provider" :disabled="!writable">
                <option value="LOCAL">本地离线（不接外部大模型）</option>
                <option value="OPENAI">通用大模型（OpenAI 兼容 · 接 qwen/deepseek/moonshot/GLM/Ollama 等）</option>
              </select>
            </label>
            <template v-if="cfg.provider !== 'LOCAL'">
              <label class="fld">服务商接口地址 Base URL
                <input v-model="cfg.baseUrl" :disabled="!writable" placeholder="如 https://dashscope.aliyuncs.com/compatible-mode/v1" />
              </label>
              <!-- 模型版本：同一服务商常有多个模型版本；选预设或自填该服务商的具体模型 id -->
              <label class="fld">模型版本
                <input v-model="cfg.model" :disabled="!writable" list="model-presets" placeholder="选预设或填具体模型 id，如 qwen-plus" />
                <datalist id="model-presets">
                  <option value="qwen-plus">通义千问 · qwen-plus</option>
                  <option value="qwen-max">通义千问 · qwen-max</option>
                  <option value="deepseek-chat">DeepSeek · deepseek-chat</option>
                  <option value="deepseek-reasoner">DeepSeek · deepseek-reasoner</option>
                  <option value="moonshot-v1-8k">Kimi · moonshot-v1-8k</option>
                  <option value="glm-4-plus">智谱 · glm-4-plus</option>
                  <option value="gpt-4o-mini">OpenAI · gpt-4o-mini</option>
                </datalist>
              </label>
              <label class="fld">API Key
                <input v-model="cfg.apiKey" type="password" :disabled="!writable"
                       :placeholder="view.keyConfigured ? ('已配置（' + (view.keyHint || '····') + '）· 留空不改') : '粘贴密钥（加密存储，不回显）'" />
              </label>
              <label class="fld">Max Tokens
                <input v-model.number="cfg.maxTokens" type="number" :disabled="!writable" />
              </label>
            </template>
            <label class="chk">
              <input type="checkbox" v-model="cfg.enabled" :disabled="!writable" /> 启用
            </label>
            <div class="acts">
              <button class="btn" :disabled="!writable || saving" @click="save">{{ saving ? '保存中…' : '保存配置' }}</button>
              <span v-if="saveMsg" class="ok-msg">{{ saveMsg }}</span>
              <span v-if="saveErr" class="err-msg">{{ saveErr }}</span>
            </div>
            <p class="warn">🔒 密钥经 AES 加密落库、接口不回显明文；上线须配置环境变量 <b>GRC_CONFIG_SECRET</b>（加密主密钥）。我（助手）不会代你输入真实密钥。</p>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { canWrite } from '@/auth.js'

const status = reactive({ provider: 'local', model: 'local', offline: true, embeddingDim: 1024 })
async function loadStatus() { try { Object.assign(status, await api.get('/ai/status')) } catch (e) { /* 保持默认 */ } }

// Web 配置
const writable = canWrite('ai')
const view = reactive({ provider: 'LOCAL', keyConfigured: false, keyHint: '' })
const cfg = reactive({ provider: 'LOCAL', baseUrl: '', model: '', maxTokens: 1024, apiKey: '', enabled: true })
const saving = ref(false)
const saveMsg = ref('')
const saveErr = ref('')
async function loadConfig() {
  try {
    const v = await api.get('/ai/config')
    Object.assign(view, v)
    cfg.provider = v.provider || 'LOCAL'
    cfg.baseUrl = v.baseUrl || ''
    cfg.model = v.model || ''
    cfg.maxTokens = v.maxTokens || 1024
    cfg.enabled = v.enabled !== false
    cfg.apiKey = '' // 永不回显密钥
  } catch (e) { /* 保持默认 */ }
}
async function save() {
  saving.value = true; saveMsg.value = ''; saveErr.value = ''
  try {
    // apiKey 留空 → 后端保持原密钥不变
    await api.put('/ai/config', {
      provider: cfg.provider, baseUrl: cfg.baseUrl || null, model: cfg.model || null,
      maxTokens: cfg.maxTokens, enabled: cfg.enabled, apiKey: cfg.apiKey || null
    })
    saveMsg.value = '已保存'
    await loadConfig(); await loadStatus()
    setTimeout(() => (saveMsg.value = ''), 2500)
  } catch (e) { saveErr.value = e.message } finally { saving.value = false }
}

onMounted(() => { loadStatus(); loadConfig() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.g { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .sub { margin-left: auto; font-size: 11px; color: var(--text-3); }
.cb { padding: 14px 18px 18px; }
.row { display: flex; align-items: center; justify-content: space-between; padding: 9px 0; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }
.row:last-of-type { border-bottom: 0; }
.row .k { color: var(--text-2); }
.row b { font-weight: 700; font-variant-numeric: tabular-nums; }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.wait { color: #a87d22; } .st.wait .d { background: #a87d22; }
.note { margin: 12px 0 0; font-size: 12px; color: var(--text-3); line-height: 1.6; }
.lead { font-size: 12.5px; color: var(--text-2); margin: 0 0 12px; }
.env { display: grid; grid-template-columns: auto 1fr; gap: 6px 14px; font-size: 12px; align-items: center; }
.env .ek { font-family: var(--font-mono, monospace); font-weight: 700; color: var(--accent-strong); }
.env .ev { font-family: var(--font-mono, monospace); color: var(--text-2); }
.env .sep { color: var(--text-3); margin: 0 4px; }
.prov { margin-top: 12px; }
.prov .pt { font-size: 11px; font-weight: 700; color: var(--text-2); margin-bottom: 6px; }
.warn { margin: 14px 0 0; padding: 9px 12px; font-size: 12px; color: var(--text-2); background: var(--warning-tint); border: 1px solid var(--border); border-left: 3px solid #a87d22; border-radius: var(--radius-md); line-height: 1.6; }
.fld { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 11px; }
.fld input, .fld select { display: block; width: 100%; height: 36px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; box-sizing: border-box; }
.chk { display: flex; align-items: center; gap: 6px; font-size: 12.5px; margin: 4px 0 12px; }
.acts { display: flex; align-items: center; gap: 12px; }
.ok-msg { color: var(--success); font-weight: 600; font-size: 12px; }
.err-msg { color: var(--danger); font-size: 12px; }
</style>
