<template>
  <!--
    法规跟踪（RegulationView）：法规库 + 变更动态 + 影响评估闭环。
    功能真源 = 后端 /api/regulations（库/状态/变更/影响评估）；视觉遵 tokens.css。隔离由后端 RLS。
  -->
  <AppShell>
    <section class="view view-reg">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('reg.tag') }}</div>
          <h1>{{ $t('reg.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('law')" :title="canWrite('law') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('reg.create.btn') }}</button>
      </div>

      <!-- ===== 法规跟踪爬虫：追踪源采集 + 采集流（权威信息源采集，替代手动登记为主）===== -->
      <div class="g g-1-1" style="margin-bottom: 14px">
        <!-- 追踪源 -->
        <div class="card">
          <div class="ch">
            <h3>追踪源采集</h3><span class="sub">权威信息源采集 · 检测频率 · 采集健康</span>
            <button class="btn ghost sm" style="margin-left: auto" :disabled="!canWrite('law')" @click="openSource">＋ 新增追踪源</button>
          </div>
          <div class="cb" style="padding-top: 0">
            <div v-for="s in sources" :key="s.id" class="srcrow">
              <span class="st" :class="s.status === 'OK' ? 'ok' : 'over'"><span class="d"></span></span>
              <div class="srcmeta">
                <div class="snm">{{ s.name }} <span class="pill">{{ s.sourceType }}</span></div>
                <div class="ssub">
                  频率 {{ s.frequency }} · 累计命中 {{ s.lastHitCount }} ·
                  {{ s.lastFetchedAt ? fmt(s.lastFetchedAt) : '未采集' }}
                  <span v-if="s.lastError" style="color: var(--danger)"> · {{ s.lastError }}</span>
                </div>
              </div>
              <button class="btn sm" :disabled="!canWrite('law') || crawling === s.id" @click="crawlNow(s)">
                {{ crawling === s.id ? '采集中…' : '立即抓取' }}
              </button>
            </div>
            <div v-if="!sources.length" class="hint">暂无追踪源。点「＋ 新增追踪源」——可选内置示例源(SAMPLE)不外联演示采集，或配 HTTP 源抓真实站点。</div>
            <div v-if="crawlMsg" class="ok-msg">{{ crawlMsg }}</div>
          </div>
        </div>
        <!-- 采集法规流 -->
        <div class="card">
          <div class="ch"><h3>采集到的法规</h3><span class="cnt">{{ crawled.length }}</span></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <table style="min-width: 560px">
              <thead><tr><th>标题</th><th>发布机关</th><th>文号</th><th>发布日期</th><th>分类</th></tr></thead>
              <tbody>
                <tr v-for="c in crawled" :key="c.id" class="clk" @click="openLaw(c)">
                  <td><a class="lawlink">{{ c.title }}</a></td>
                  <td>{{ c.issuer || '—' }}</td>
                  <td class="code">{{ c.docNo || '—' }}</td>
                  <td class="num">{{ c.publishDate || '—' }}</td>
                  <td><span class="pill">{{ c.category || '—' }}</span></td>
                </tr>
                <tr v-if="!crawled.length"><td colspan="5" class="emptyrow">暂无采集法规，先新增源并「立即抓取」。</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="g">
        <!-- 法规库 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('reg.lib') }}</h3><span class="cnt">{{ regulations.length }}</span></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <table style="min-width: 520px">
              <thead><tr>
                <th>{{ $t('reg.th.code') }}</th><th>{{ $t('reg.th.title') }}</th>
                <th>{{ $t('reg.th.issuer') }}</th><th>{{ $t('reg.th.status') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="r in regulations" :key="r.id" class="clk" :class="{ on: r.id === selectedId }" @click="selectReg(r)">
                  <td class="code">{{ r.code }}</td>
                  <td>{{ r.title }}</td>
                  <td>{{ r.issuer || '—' }}</td>
                  <td><span class="st" :class="regStCls(r.status)"><span class="d"></span>{{ $t('reg.status.' + r.status) }}</span></td>
                </tr>
                <tr v-if="!regulations.length"><td colspan="4" class="emptyrow">{{ loadError || $t('reg.empty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 变更动态（选中法规）-->
        <div class="card">
          <div class="ch">
            <h3>{{ $t('reg.changes') }}</h3>
            <button v-if="selectedId" class="btn ghost sm" @click="openChange">{{ $t('reg.change.btn') }}</button>
          </div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <div v-if="!selectedId" class="hint">{{ $t('reg.selectHint') }}</div>
            <table v-else style="min-width: 560px">
              <thead><tr>
                <th>{{ $t('reg.cth.type') }}</th><th>{{ $t('reg.cth.date') }}</th>
                <th>{{ $t('reg.cth.desc') }}</th><th>{{ $t('reg.cth.impact') }}</th><th>{{ $t('reg.cth.op') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="c in changes" :key="c.id">
                  <td><span class="pill">{{ $t('reg.changeType.' + c.changeType) }}</span></td>
                  <td class="num">{{ c.changeDate || '—' }}</td>
                  <td class="desc">{{ c.description }}</td>
                  <td><span class="st" :class="c.impactStatus === 'ASSESSED' ? 'ok' : 'wait'"><span class="d"></span>{{ $t('reg.impact.' + c.impactStatus) }}</span></td>
                  <td><button v-if="c.impactStatus === 'PENDING'" class="btn sm" @click="openAssess(c)">{{ $t('reg.assess.btn') }}</button></td>
                </tr>
                <tr v-if="!changes.length"><td colspan="5" class="emptyrow">{{ $t('reg.changeEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 采集法规详情弹窗（点采集流任一条目）-->
      <div v-if="lawDetail" class="modal-mask" @click.self="lawDetail = null">
        <div class="modal-card" style="width: 560px">
          <h3 style="line-height:1.4">{{ lawDetail.title }}</h3>
          <div class="lawmeta">
            <div><span class="lk">发布机关</span>{{ lawDetail.issuer || '—' }}</div>
            <div><span class="lk">发文字号</span>{{ lawDetail.docNo || '—' }}</div>
            <div><span class="lk">发布日期</span>{{ lawDetail.publishDate || '—' }}</div>
            <div><span class="lk">分类</span><span class="pill">{{ lawDetail.category || '—' }}</span></div>
          </div>
          <p class="lawsum">{{ lawDetail.summary || '（无摘要）' }}</p>
          <div class="modal-actions">
            <a v-if="isHttp(lawDetail.url)" class="btn ghost" :href="lawDetail.url" target="_blank" rel="noopener">查看原文 ↗</a>
            <span v-else-if="lawDetail.url" class="muted" style="margin-right:auto">原文链接：{{ lawDetail.url }}（示例源为占位链接）</span>
            <button class="btn" @click="lawDetail = null">关闭</button>
          </div>
        </div>
      </div>

      <!-- 新增追踪源弹窗 -->
      <div v-if="showSource" class="modal-mask" @click.self="showSource = false">
        <div class="modal-card">
          <h3>新增追踪源</h3>
          <label class="fld">源名称<input v-model="sf.name" placeholder="如 全国法规库（示例）" /></label>
          <label class="fld">类型
            <select v-model="sf.sourceType">
              <option value="SAMPLE">内置示例源（不外联，演示采集）</option>
              <option value="HTTP">HTTP 抓取（配 URL + 选择器）</option>
            </select>
          </label>
          <template v-if="sf.sourceType === 'HTTP'">
            <label class="fld">列表页 URL<input v-model="sf.url" placeholder="https://…" /></label>
            <label class="fld">选择器配置 (JSON)
              <textarea v-model="sf.config" rows="3" placeholder='{"listSelector":".list li","titleSelector":"a","linkSelector":"a","issuer":"中国人民银行","category":"支付清算"}'></textarea>
            </label>
          </template>
          <label class="fld">所属组织
            <select v-model.number="sf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="sourceErr" class="cerr">{{ sourceErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showSource = false">取消</button>
            <button class="btn" :disabled="!sf.name || sourceSaving" @click="submitSource">{{ sourceSaving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 登记法规弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('reg.create.btn') }}</h3>
          <label class="fld">{{ $t('reg.th.code') }}<input v-model="cf.code" /></label>
          <label class="fld">{{ $t('reg.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('reg.th.issuer') }}<input v-model="cf.issuer" /></label>
          <label class="fld">{{ $t('reg.create.category') }}<input v-model="cf.category" /></label>
          <label class="fld">{{ $t('reg.create.org') }}
            <select v-model.number="cf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('reg.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 登记变更弹窗 -->
      <div v-if="showChange" class="modal-mask" @click.self="showChange = false">
        <div class="modal-card">
          <h3>{{ $t('reg.change.btn') }}</h3>
          <label class="fld">{{ $t('reg.cth.type') }}
            <select v-model="ch.changeType"><option value="ENACTED">{{ $t('reg.changeType.ENACTED') }}</option><option value="AMENDED">{{ $t('reg.changeType.AMENDED') }}</option><option value="ABOLISHED">{{ $t('reg.changeType.ABOLISHED') }}</option></select>
          </label>
          <label class="fld">{{ $t('reg.cth.date') }}<input v-model="ch.changeDate" type="date" /></label>
          <label class="fld">{{ $t('reg.cth.desc') }}<input v-model="ch.description" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showChange = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ch.description || saving" @click="submitChange">{{ saving ? $t('common.submitting') : $t('reg.change.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 影响评估弹窗 -->
      <div v-if="showAssess" class="modal-mask" @click.self="showAssess = false">
        <div class="modal-card">
          <h3>{{ $t('reg.assess.title') }}</h3>
          <label class="fld">{{ $t('reg.assess.scope') }}<input v-model="af.impactScope" :placeholder="$t('reg.assess.scopePh')" /></label>
          <label class="fld">{{ $t('reg.assess.note') }}<input v-model="af.impactNote" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAssess = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!af.impactScope || saving" @click="submitAssess">{{ saving ? $t('common.submitting') : $t('reg.assess.ok') }}</button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const regulations = ref([])
const loadError = ref('')
const selectedId = ref(null)
const changes = ref([])
const saving = ref(false)
const opError = ref('')

async function loadRegs() {
  loadError.value = ''
  try { regulations.value = await api.get('/regulations') } catch (e) { loadError.value = e.message; regulations.value = [] }
}
async function selectReg(r) {
  selectedId.value = r.id
  try { changes.value = await api.get('/regulations/' + r.id + '/changes') } catch (e) { changes.value = [] }
}
const regStCls = (s) => ({ TRACKING: 'wait', EFFECTIVE: 'ok', SUPERSEDED: 'over', ABOLISHED: 'over' }[s] || 'wait')

// 登记法规
const showCreate = ref(false)
const cf = reactive({ code: '', title: '', issuer: '', category: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', title: '', issuer: '', category: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations', { orgId: cf.orgId, code: cf.code, title: cf.title, issuer: cf.issuer, category: cf.category })
    showCreate.value = false; await loadRegs()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 登记变更
const showChange = ref(false)
const ch = reactive({ changeType: 'AMENDED', changeDate: '', description: '' })
function openChange() { Object.assign(ch, { changeType: 'AMENDED', changeDate: '', description: '' }); opError.value = ''; showChange.value = true }
async function submitChange() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations/' + selectedId.value + '/changes', { changeType: ch.changeType, changeDate: ch.changeDate || null, description: ch.description })
    showChange.value = false; await selectReg({ id: selectedId.value })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 影响评估
const showAssess = ref(false)
const assessTarget = ref(null)
const af = reactive({ impactScope: '', impactNote: '' })
function openAssess(c) { assessTarget.value = c; Object.assign(af, { impactScope: '', impactNote: '' }); opError.value = ''; showAssess.value = true }
async function submitAssess() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations/changes/' + assessTarget.value.id + '/assess', { impactScope: af.impactScope, impactNote: af.impactNote })
    showAssess.value = false; await selectReg({ id: selectedId.value })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// ===== 法规跟踪爬虫：追踪源 + 采集流 =====
const sources = ref([])
const crawled = ref([])
// 采集法规详情（点条目打开；示例源 url 为占位，真实 HTTP 源 url 可"查看原文"）
const lawDetail = ref(null)
function openLaw(c) { lawDetail.value = c }
function isHttp(u) { return typeof u === 'string' && /^https?:\/\//.test(u) && !/\/sample\//.test(u) }
const crawling = ref(null)
const crawlMsg = ref('')
async function loadSources() {
  try { sources.value = await api.get('/regulation-sources') } catch (e) { sources.value = [] }
}
async function loadCrawled() {
  try { crawled.value = await api.get('/crawled-regulations') } catch (e) { crawled.value = [] }
}
async function crawlNow(s) {
  crawling.value = s.id; crawlMsg.value = ''
  try {
    const r = await api.post('/regulation-sources/' + s.id + '/crawl', {})
    crawlMsg.value = `「${s.name}」命中 ${r.hit} 条，新增 ${r.added} 条` + (r.error ? ('（错误：' + r.error + '）') : '')
    await loadSources(); await loadCrawled()
  } catch (e) { crawlMsg.value = '采集失败：' + e.message } finally { crawling.value = null }
}
const showSource = ref(false)
const sourceSaving = ref(false)
const sourceErr = ref('')
const sf = reactive({ name: '', sourceType: 'SAMPLE', url: '', config: '', orgId: 12 })
function openSource() { Object.assign(sf, { name: '', sourceType: 'SAMPLE', url: '', config: '', orgId: 12 }); sourceErr.value = ''; showSource.value = true }
async function submitSource() {
  sourceSaving.value = true; sourceErr.value = ''
  try {
    await api.post('/regulation-sources', {
      orgId: sf.orgId, name: sf.name, sourceType: sf.sourceType,
      url: sf.url || null, config: sf.config || null, frequency: 'DAILY'
    })
    showSource.value = false; await loadSources()
  } catch (e) { sourceErr.value = e.message } finally { sourceSaving.value = false }
}
function fmt(t) { try { return new Date(t).toLocaleString() } catch (e) { return t } }

onMounted(() => { loadRegs(); loadSources(); loadCrawled() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.g { display: grid; grid-template-columns: 1fr 1.2fr; gap: 14px; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .btn.sm { margin-left: auto; }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 18px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.desc { color: var(--text-2); max-width: 240px; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.modal-card .fld textarea { display: block; width: 100%; margin-top: 5px; padding: 8px 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.ch .sub { font-size: 11.5px; color: var(--text-3); }
/* 追踪源行 */
.g-1-1 { grid-template-columns: 1fr 1.2fr; }
.srcrow { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-top: 1px solid var(--border-subtle); }
.srcrow:first-child { border-top: 0; }
.srcrow .srcmeta { flex: 1; min-width: 0; }
.srcrow .snm { font-size: 12.5px; font-weight: 600; }
.srcrow .ssub { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.ok-msg { color: var(--success); font-size: 12px; font-weight: 600; margin-top: 10px; }
/* 采集法规详情 */
.lawlink { color: var(--accent-strong); cursor: pointer; }
tbody tr.clk:hover .lawlink { text-decoration: underline; }
.lawmeta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; margin: 4px 0 12px; font-size: 12.5px; }
.lawmeta .lk { color: var(--text-3); display: inline-block; min-width: 64px; }
.lawsum { font-size: 13px; color: var(--text-2); line-height: 1.7; background: var(--bg); border-radius: 8px; padding: 12px; margin: 0; }
.muted { color: var(--text-3); font-size: 11.5px; }
</style>
