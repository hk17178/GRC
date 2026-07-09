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
        <!-- 采集法规流（#7 信息流样式：按追踪源自动分区）-->
        <div class="card">
          <div class="ch"><h3>采集到的法规</h3><span class="cnt">{{ crawlKw ? crawledShown + '/' + crawled.length : crawled.length }}</span><span class="sub">按追踪源分区 · 点条目看详情</span>
            <div class="feed-search"><input v-model="crawlKw" placeholder="🔍 关键字过滤 标题/文号/机构" /><button v-if="crawlKw" class="feed-clear" @click="crawlKw = ''" title="清除">×</button></div>
          </div>
          <div class="cb" style="padding-top:0">
            <div v-for="grp in crawledGroups" :key="grp.sourceId" class="feed-group">
              <div class="feed-head">
                <span class="feed-src">{{ grp.sourceName }}</span>
                <span class="pill" v-if="grp.category">{{ grp.category }}</span>
                <span class="cnt">{{ grp.items.length }}</span>
              </div>
              <div class="feed-list">
                <div v-for="c in grp.visible" :key="c.id" class="feed-item clk" @click="openLaw(c)">
                  <span class="feed-dot"></span>
                  <span class="feed-title">{{ c.title }}</span>
                  <span class="feed-meta">
                    <span v-if="c.docNo" class="code">{{ c.docNo }}</span>
                    <span v-if="c.publishDate" class="num">{{ c.publishDate }}</span>
                    <span class="muted">{{ fmtFeedTime(c.fetchedAt) }}</span>
                  </span>
                </div>
                <button v-if="grp.collapsedMore > 0" class="feed-more" @click="toggleGroup(grp.sourceId)">展开更多 {{ grp.collapsedMore }} 条 ▾</button>
                <button v-else-if="crawlExpanded[grp.sourceId] && !crawlKw && grp.items.length > CRAWL_ROWS" class="feed-more" @click="toggleGroup(grp.sourceId)">收起 ▴</button>
              </div>
            </div>
            <div v-if="!crawled.length" class="emptyrow">暂无采集法规，先新增源并「立即抓取」（人行两源已内置）。</div>
            <div v-else-if="!crawledGroups.length" class="emptyrow">没有匹配「{{ crawlKw }}」的法规。</div>
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
                <template v-for="c in changes" :key="c.id">
                  <tr>
                    <td><span class="pill">{{ $t('reg.changeType.' + c.changeType) }}</span></td>
                    <td class="num">{{ c.changeDate || '—' }}</td>
                    <td class="desc">{{ c.description }}</td>
                    <td><span class="st" :class="c.impactStatus === 'ASSESSED' ? 'ok' : 'wait'"><span class="d"></span>{{ $t('reg.impact.' + c.impactStatus) }}</span></td>
                    <td>
                      <button v-if="c.impactStatus === 'PENDING'" class="btn sm" @click="openAssess(c)">{{ $t('reg.assess.btn') }}</button>
                      <button v-if="!c.aiSummary" class="btn ghost sm" :disabled="!canWrite('law') || aiBusy === c.id" @click="aiSummarize(c)">{{ aiBusy === c.id ? '生成中…' : 'AI 摘要' }}</button>
                    </td>
                  </tr>
                  <!-- AI 条款级变更摘要（需求 6.5.1；本地离线模式诚实标注）-->
                  <tr v-if="c.aiSummary">
                    <td colspan="5" class="aisum">⚡ AI 变更摘要：{{ c.aiSummary }}</td>
                  </tr>
                </template>
                <tr v-if="!changes.length"><td colspan="5" class="emptyrow">{{ $t('reg.changeEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>

          <!-- 法规-制度映射（需求 6.2：法规条款命中的制度）-->
          <div v-if="selectedId" class="ch" style="border-top:1px solid var(--border-subtle)">
            <h3>命中制度映射</h3><span class="cnt">{{ maps.length }}</span>
            <div style="margin-left:auto;display:flex;gap:8px">
              <button class="btn ghost sm" :disabled="!canWrite('law') || suggestBusy" @click="suggestMap">{{ suggestBusy ? '分析中…' : '⚡ AI 匹配建议' }}</button>
              <button class="btn ghost sm" :disabled="!canWrite('law')" @click="openMap">＋ 登记映射</button>
            </div>
          </div>
          <div v-if="selectedId" class="cb" style="padding-top:0">
            <div v-for="m in maps" :key="m.id">
              <div class="map-row">
                <span class="pill">{{ m.clause || '整篇' }}</span>
                <span class="map-p">→ {{ policyName(m.policyId) }}</span>
                <span class="muted">{{ m.note }}</span>
                <!-- 六轮 #6：AI 符合度评估（结论徽标 + 需重评标记 + 详情展开）-->
                <span style="margin-left:auto;display:flex;gap:6px;align-items:center">
                  <span v-if="m.assessVerdict" class="verdict" :class="VD_CLS[m.assessVerdict] || 'vd-p'">{{ m.assessVerdict }}</span>
                  <span v-if="m.assessStale" class="verdict vd-stale" title="法规已再变更，结论可能过期">需重评</span>
                  <button v-if="m.assessDetail" class="mini" @click="assessOpen = assessOpen === m.id ? 0 : m.id">{{ assessOpen === m.id ? '收起' : '详情' }}</button>
                  <button class="mini" :disabled="!canWrite('law') || assessBusy === m.id" @click="assessMap(m)">{{ assessBusy === m.id ? '评估中…' : (m.assessVerdict ? '重新评估' : '⚡ 符合度评估') }}</button>
                </span>
              </div>
              <div v-if="assessErr[m.id]" class="hint" style="color:var(--danger);padding:4px 10px">{{ assessErr[m.id] }}</div>
              <div v-if="assessOpen === m.id && m.assessDetail" class="suggest-box" style="margin:4px 0 10px">
                <div class="sg-h">⚡ AI 符合度评估（{{ (m.assessedAt || '').slice(0, 10) }} · 初稿须人工复核）</div>
                <pre class="sg-body">{{ m.assessDetail }}</pre>
              </div>
            </div>
            <div v-if="!maps.length" class="hint" style="padding:10px">暂无映射，点「⚡ AI 匹配建议」由 AI 初筛，或「＋ 登记映射」手工关联。</div>
            <div v-if="suggestion" class="suggest-box">
              <div class="sg-h">⚡ AI 匹配建议（{{ suggestion.provider }} · 初稿须人工确认后用「＋ 登记映射」落库）</div>
              <pre class="sg-body">{{ suggestion.suggestion }}</pre>
            </div>
          </div>
        </div>
      </div>

      <!-- 登记法规-制度映射弹窗 -->
      <div v-if="showMap" class="modal-mask" @click.self="showMap = false">
        <div class="modal-card">
          <h3>登记法规-制度映射</h3>
          <label class="fld">法规条款<input v-model="mf.clause" placeholder="如 §41（可空=整篇）" /></label>
          <label class="fld">命中制度
            <select v-model.number="mf.policyId">
              <option :value="0" disabled>— 选择制度 —</option>
              <option v-for="p in policies" :key="p.id" :value="p.id">{{ p.code }} · {{ p.title }}</option>
            </select>
          </label>
          <label class="fld">映射说明<input v-model="mf.note" placeholder="如 对应制度第3章日志留存" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showMap = false">取消</button>
            <button class="btn" :disabled="!mf.policyId || saving" @click="submitMap">确认登记</button>
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
          <label class="fld">分类（分区标签）<input v-model="sf.category" placeholder="如 支付清算 / 数据安全 / 反洗钱" /></label>
          <label class="fld">关键字过滤（可空；逗号/顿号分隔，只采纳标题命中的条目）
            <input v-model="sf.keyword" placeholder="如 反洗钱、备付金、数据安全" />
          </label>
          <template v-if="sf.sourceType === 'HTTP'">
            <label class="fld">列表页 URL<input v-model="sf.url" placeholder="https://…" /></label>
            <label class="fld">选择器配置 (JSON，可空)
              <textarea v-model="sf.config" rows="3" placeholder='{"listSelector":".list li","titleSelector":"a","linkSelector":"a","issuer":"中国人民银行"}'></textarea>
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
import { ref, reactive, computed, onMounted } from 'vue'
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
  loadMaps()
}
const regStCls = (s) => ({ TRACKING: 'wait', EFFECTIVE: 'ok', SUPERSEDED: 'over', ABOLISHED: 'over' }[s] || 'wait')

// ===== M4 深度：法规-制度映射 + AI 变更摘要 =====
const maps = ref([])
const policies = ref([])
async function loadMaps() {
  try { maps.value = await api.get('/regulations/' + selectedId.value + '/policy-maps') } catch (e) { maps.value = [] }
}
async function loadPolicies() {
  try { policies.value = await api.get('/policies') } catch (e) { policies.value = [] }
}
const policyName = (id) => { const p = policies.value.find((x) => x.id === id); return p ? p.code + ' · ' + p.title : '制度 #' + id }

// ===== 六轮 #6：AI 符合度评估（对单条映射比对法规要求与制度全文，结论+差距+建议落库）=====
const assessBusy = ref(0)
const assessOpen = ref(0)
const assessErr = reactive({})
const VD_CLS = { 符合: 'vd-ok', 部分符合: 'vd-mid', 不符合: 'vd-bad', 待复核: 'vd-p' }
async function assessMap(m) {
  assessBusy.value = m.id; assessErr[m.id] = ''
  try {
    await api.post('/regulations/policy-maps/' + m.id + '/assess', {})
    await loadMaps()
    assessOpen.value = m.id
  } catch (e) { assessErr[m.id] = e.message } finally { assessBusy.value = 0 }
}

// ===== AI 匹配建议（V49 · POLICY_MAP 场景：只出建议不落库，人工确认后登记映射）=====
const suggestion = ref(null)
const suggestBusy = ref(false)
async function suggestMap() {
  suggestBusy.value = true; suggestion.value = null
  try { suggestion.value = await api.post('/regulations/' + selectedId.value + '/map-suggest', {}) }
  catch (e) { suggestion.value = { suggestion: '建议生成失败：' + e.message, provider: '-' } }
  finally { suggestBusy.value = false }
}

const showMap = ref(false)
const mf = reactive({ clause: '', policyId: 0, note: '' })
function openMap() { Object.assign(mf, { clause: '', policyId: 0, note: '' }); opError.value = ''; showMap.value = true }
async function submitMap() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations/' + selectedId.value + '/policy-maps', { policyId: mf.policyId, clause: mf.clause || null, note: mf.note || null })
    showMap.value = false; await loadMaps()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
const aiBusy = ref(null)
async function aiSummarize(c) {
  aiBusy.value = c.id; opError.value = ''
  try {
    await api.post('/regulations/changes/' + c.id + '/ai-summary', {})
    changes.value = await api.get('/regulations/' + selectedId.value + '/changes')
  } catch (e) { opError.value = e.message } finally { aiBusy.value = null }
}

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
// 关键字过滤 + 分区默认收起（每区 5 行，超出「展开更多」）
const crawlKw = ref('')
const CRAWL_ROWS = 5
const crawlExpanded = reactive({})   // { [sourceId]: true } 已展开的分区
function toggleGroup(id) { crawlExpanded[id] = !crawlExpanded[id] }
// #7 信息流分组：按 sourceId 分区，区头显示追踪源名称与分类；无源信息（历史数据）归"其他来源"
const crawledGroups = computed(() => {
  const kw = crawlKw.value.trim().toLowerCase()
  const match = (c) => !kw || (c.title || '').toLowerCase().includes(kw)
    || (c.docNo || '').toLowerCase().includes(kw) || (c.issuer || '').toLowerCase().includes(kw)
  const bySrc = new Map()
  for (const c of crawled.value) {
    if (!match(c)) continue
    const key = c.sourceId || 0
    if (!bySrc.has(key)) bySrc.set(key, [])
    bySrc.get(key).push(c)
  }
  const groups = []
  for (const [sourceId, items] of bySrc) {
    const src = sources.value.find((s) => s.id === sourceId)
    let category = null
    try { category = src && src.config ? JSON.parse(src.config).category : null } catch (e) { /* 忽略 */ }
    // 关键字命中时自动展开该区（便于直接看到全部匹配）；否则默认收起前 N 行
    const showAll = !!kw || crawlExpanded[sourceId]
    groups.push({ sourceId, sourceName: src ? src.name : '其他来源', category,
      items, visible: showAll ? items : items.slice(0, CRAWL_ROWS), collapsedMore: showAll ? 0 : Math.max(0, items.length - CRAWL_ROWS) })
  }
  groups.sort((a, b) => (a.sourceName > b.sourceName ? 1 : -1))
  return groups
})
// 过滤后可见总数（区头计数用）
const crawledShown = computed(() => crawledGroups.value.reduce((n, g) => n + g.items.length, 0))
function fmtFeedTime(t) { return t ? new Date(t).toLocaleString() : '' }
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
const sf = reactive({ name: '', sourceType: 'SAMPLE', url: '', config: '', category: '', keyword: '', orgId: 12 })
function openSource() { Object.assign(sf, { name: '', sourceType: 'SAMPLE', url: '', config: '', category: '', keyword: '', orgId: 12 }); sourceErr.value = ''; showSource.value = true }
// 把「分类/关键字」并入 config JSON（保留 HTTP 选择器等已有键）
function buildSourceConfig() {
  let cfg = {}
  if (sf.config && sf.config.trim()) { try { cfg = JSON.parse(sf.config) } catch (e) { throw new Error('选择器配置不是合法 JSON') } }
  if (sf.category && sf.category.trim()) cfg.category = sf.category.trim(); else delete cfg.category
  if (sf.keyword && sf.keyword.trim()) cfg.keyword = sf.keyword.trim(); else delete cfg.keyword
  return Object.keys(cfg).length ? JSON.stringify(cfg) : null
}
async function submitSource() {
  sourceSaving.value = true; sourceErr.value = ''
  try {
    await api.post('/regulation-sources', {
      orgId: sf.orgId, name: sf.name, sourceType: sf.sourceType,
      url: sf.url || null, config: buildSourceConfig(), frequency: 'DAILY'
    })
    showSource.value = false; await loadSources()
  } catch (e) { sourceErr.value = e.message } finally { sourceSaving.value = false }
}
function fmt(t) { try { return new Date(t).toLocaleString() } catch (e) { return t } }

onMounted(() => { loadRegs(); loadSources(); loadCrawled(); loadPolicies() })
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
.g-1-1 { grid-template-columns: 1fr 1.2fr; align-items: start; }
/* 采集流：关键字过滤 + 展开更多 */
.feed-search { margin-left: auto; position: relative; display: flex; align-items: center; }
.feed-search input { width: 220px; max-width: 40vw; font-size: 12px; padding: 5px 26px 5px 10px; border: 1px solid var(--surface-border); border-radius: 8px; background: var(--bg); color: var(--text-1); }
.feed-search input:focus { outline: none; border-color: var(--accent); }
.feed-clear { position: absolute; right: 6px; border: 0; background: none; color: var(--text-3); font-size: 15px; line-height: 1; cursor: pointer; }
.feed-more { width: 100%; margin-top: 4px; padding: 6px; border: 1px dashed var(--surface-border); border-radius: 8px; background: none; color: var(--accent-strong); font-size: 11.5px; font-weight: 600; cursor: pointer; }
.feed-more:hover { background: var(--accent-tint); border-style: solid; }
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
/* M4 深度 */
.aisum { background: var(--accent-tint); color: var(--text-2); font-size: 12px; line-height: 1.7; border-left: 3px solid var(--accent); }
.map-row { display: flex; align-items: center; gap: 10px; padding: 7px 4px; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }
.map-row .map-p { font-weight: 600; }
/* #7 采集信息流分组 */
.feed-group { margin-bottom: 14px; }
.feed-head { display: flex; align-items: center; gap: 8px; padding: 8px 2px 6px; border-bottom: 2px solid var(--accent-weak); }
.feed-src { font-size: 13px; font-weight: 720; color: var(--accent-strong); font-family: var(--font-display); }
.feed-list { padding: 2px 0; }
.feed-item { display: flex; align-items: center; gap: 9px; padding: 7px 4px; border-bottom: 1px dashed var(--border-subtle); font-size: 12.5px; }
.feed-item:hover { background: var(--accent-weak); border-radius: 6px; }
.feed-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); flex-shrink: 0; }
.feed-title { color: var(--text-1); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.feed-meta { margin-left: auto; display: flex; gap: 10px; flex-shrink: 0; font-size: 11px; color: var(--text-3); }
/* V49 AI 匹配建议 */
.suggest-box { margin-top: 10px; border: 1px dashed var(--accent); border-radius: var(--radius-md); overflow: hidden; }
.suggest-box .sg-h { padding: 8px 12px; font-size: 11.5px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); }
.suggest-box .sg-body { margin: 0; padding: 10px 12px; font-size: 12.5px; font-family: inherit; white-space: pre-wrap; line-height: 1.7; color: var(--text-2); }
/* 六轮 #6：符合度结论徽标 + 小按钮 */
.verdict { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
.vd-ok { color: var(--success); background: color-mix(in srgb, var(--success) 12%, transparent); }
.vd-mid { color: #a87d22; background: color-mix(in srgb, #a87d22 12%, transparent); }
.vd-bad { color: var(--danger); background: var(--danger-tint, color-mix(in srgb, var(--danger) 12%, transparent)); }
.vd-p { color: var(--text-3); background: var(--surface-2, var(--bg)); border: 1px solid var(--surface-border); }
.vd-stale { color: var(--warning); border: 1px dashed var(--warning); background: none; }
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; }
.mini:hover { background: var(--accent-tint, var(--accent-weak)); }
</style>
