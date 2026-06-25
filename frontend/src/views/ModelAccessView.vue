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
              <span class="st" :class="status.offline ? 'wait' : 'ok'"><span class="d"></span>{{ status.offline ? $t('aimodel.local') : 'Claude · ' + status.provider }}</span>
            </div>
            <div class="row"><span class="k">{{ $t('aimodel.embedding') }}</span><b>{{ $t('aimodel.embLocal') }} · {{ status.embeddingDim }}d</b></div>
            <div class="row"><span class="k">{{ $t('aimodel.network') }}</span>
              <span class="st" :class="status.offline ? 'ok' : 'wait'"><span class="d"></span>{{ status.offline ? $t('aimodel.noEgress') : $t('aimodel.egress') }}</span>
            </div>
            <p class="note">{{ status.offline ? $t('aimodel.localNote') : $t('aimodel.claudeNote') }}</p>
          </div>
        </div>

        <!-- 切换指引（部署侧） -->
        <div class="card">
          <div class="ch"><h3>{{ $t('aimodel.howto') }}</h3><span class="sub">{{ $t('aimodel.deploySide') }}</span></div>
          <div class="cb">
            <p class="lead">{{ $t('aimodel.howtoLead') }}</p>
            <div class="env">
              <div class="ek">GRC_AI_PROVIDER</div><div class="ev">local <span class="sep">|</span> claude</div>
              <div class="ek">ANTHROPIC_API_KEY</div><div class="ev">{{ $t('aimodel.keyDeploy') }}</div>
              <div class="ek">GRC_AI_CLAUDE_MODEL</div><div class="ev">claude-opus-4-8</div>
            </div>
            <p class="warn">🔒 {{ $t('aimodel.keyWarn') }}</p>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const status = reactive({ provider: 'local', offline: true, embeddingDim: 1024 })
async function loadStatus() { try { Object.assign(status, await api.get('/ai/status')) } catch (e) { /* 保持默认 */ } }
onMounted(loadStatus)
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
.warn { margin: 14px 0 0; padding: 9px 12px; font-size: 12px; color: var(--text-2); background: var(--warning-tint); border: 1px solid var(--border); border-left: 3px solid #a87d22; border-radius: var(--radius-md); line-height: 1.6; }
</style>
