<template>
  <!--
    制度发布（PolicyView · M1）：制度全生命周期 + 审批两步化。
    功能真源 = 后端 /api/policies；视觉遵 tokens.css。审批红线：提交评审(DRAFT→REVIEW)经 Flowable 审批，
    审批通过才生效(REVIEW→EFFECTIVE)，仅 EFFECTIVE 可签署/废止。隔离由后端 RLS。
  -->
  <AppShell>
    <section class="view view-policy">
      <div class="phead">
        <div><div class="kqt">{{ $t('policy.tag') }}</div><h1>{{ $t('policy.title') }}</h1></div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('policy.create')" :title="canWrite('policy.create') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('policy.create.btn') }}</button>
      </div>

      <!-- M1 深度：KPI 四卡（真实统计）-->
      <div class="kpibar">
        <div class="kc"><div class="l">制度总数</div><div class="v">{{ policies.length }}</div></div>
        <div class="kc"><div class="l">已生效</div><div class="v" style="color:var(--success)">{{ kpi.effective }}</div></div>
        <div class="kc"><div class="l">评审中</div><div class="v" style="color:var(--accent-strong)">{{ kpi.review }}</div></div>
        <div class="kc"><div class="l">草稿</div><div class="v" style="color:#a87d22">{{ kpi.draft }}</div></div>
      </div>

      <div class="card">
        <div class="ch">
          <h3>{{ $t('policy.list') }}</h3><span class="cnt">{{ filtered.length }}</span>
          <!-- 工具栏：搜索 + 体系筛选（需求 D1-7§5.3）-->
          <input v-model="q" class="tool-input" placeholder="搜索标题/编号…" />
          <select v-model="fwFilter" class="tool-sel">
            <option value="">全部体系</option>
            <option v-for="(l, k) in FW_LABEL" :key="k" :value="k">{{ l }}</option>
          </select>
        </div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 860px">
            <thead><tr>
              <th>{{ $t('policy.th.code') }}</th><th>{{ $t('policy.th.title') }}</th>
              <th>体系</th><th>{{ $t('policy.th.version') }}</th><th>生效日期</th><th>责任部门</th>
              <th>{{ $t('policy.th.status') }}</th><th>{{ $t('policy.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="p in filtered" :key="p.id" class="clk" @click="openDetail(p)">
                <td class="code">{{ p.code }}</td>
                <td><b>{{ p.title }}</b></td>
                <td><span v-if="p.framework" class="pill">{{ FW_LABEL[p.framework] || p.framework }}</span><span v-else class="muted">—</span></td>
                <td class="num">v{{ p.version }}</td>
                <td class="num">{{ p.effectiveDate || '—' }}</td>
                <td>{{ p.ownerDept || '—' }}</td>
                <td><span class="st" :class="ST_CLS[p.status]"><span class="d"></span>{{ $t('policy.status.' + p.status) }}</span></td>
                <td class="ops" @click.stop>
                  <button v-if="p.status === 'DRAFT'" class="btn sm" :disabled="busyId === p.id" @click="act(p, 'submit')">{{ $t('policy.op.submit') }}</button>
                  <template v-else-if="p.status === 'REVIEW'">
                    <button class="btn sm" :disabled="busyId === p.id" @click="act(p, 'approve')">{{ $t('policy.op.approve') }}</button>
                    <button class="btn ghost sm danger" :disabled="busyId === p.id" @click="openReject(p)">{{ $t('policy.op.reject') }}</button>
                  </template>
                  <template v-else-if="p.status === 'EFFECTIVE'">
                    <button class="btn ghost sm" :disabled="busyId === p.id" @click="act(p, 'signoff')">{{ $t('policy.op.signoff') }}</button>
                    <button class="btn ghost sm danger" :disabled="busyId === p.id" @click="act(p, 'archive')">{{ $t('policy.op.archive') }}</button>
                  </template>
                  <span v-else class="muted">{{ $t('policy.deprecated') }}</span>
                </td>
              </tr>
              <tr v-if="!filtered.length"><td colspan="8" class="emptyrow">{{ loadError || $t('policy.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- M1 深度：制度详情抽屉（元数据 / 版本时间线 / 引用关系 / 签署确认 / 修订）-->
      <div v-if="detail" class="modal-mask" @click.self="detail = null">
        <div class="modal-card wide">
          <h3 style="display:flex;align-items:center;gap:10px">
            {{ detail.title }}
            <span class="pill">v{{ detail.version }}</span>
            <span class="st" :class="ST_CLS[detail.status]"><span class="d"></span>{{ $t('policy.status.' + detail.status) }}</span>
          </h3>

          <div class="dgrid2">
            <!-- 左：元数据（可编辑）+ 签署确认 -->
            <div>
              <div class="sec-t">元数据</div>
              <label class="fld">体系分类
                <select v-model="mf.framework"><option value="">—</option><option v-for="(l, k) in FW_LABEL" :key="k" :value="k">{{ l }}</option></select>
              </label>
              <label class="fld">生效日期<input type="date" v-model="mf.effectiveDate" /></label>
              <label class="fld">复审周期（月）<input type="number" v-model.number="mf.reviewCycleMonths" min="1" /></label>
              <label class="fld">责任部门<input v-model="mf.ownerDept" placeholder="如 信息安全部" /></label>
              <label class="fld">责任人<input v-model="mf.owner" placeholder="如 张三" /></label>
              <button class="btn sm" :disabled="!canWrite('policy') || saving" @click="saveMeta">保存元数据</button>
              <span v-if="metaMsg" class="okmsg">{{ metaMsg }}</span>

              <!-- 六轮 #6：制度全文与原件（上传 .docx → 后端提取全文，AI 符合度评估的数据基础）-->
              <div class="sec-t" style="margin-top:16px">制度全文与原件</div>
              <div class="docbox">
                <div v-if="detail.docName" class="doc-row">
                  📄 {{ detail.docName }}
                  <a class="mini-a" style="margin-left:auto" :href="'/api/policies/' + detail.id + '/document'" target="_blank">下载原件</a>
                </div>
                <div class="doc-row" :class="detail.content ? 'okc' : 'warnc'">
                  {{ detail.content ? ('✓ 已有全文（' + detail.content.length + ' 字），可用于 AI 符合度评估') : '⚠ 仅元数据无全文——上传 .docx 后才能做法规符合度评估' }}
                </div>
                <div v-if="detail.docSha256" class="doc-row muted">sha256：{{ detail.docSha256.slice(0, 16) }}…（原件固化防篡改）</div>
                <button class="btn ghost sm" :disabled="!canWrite('policy') || docBusy" @click="pickPolicyDocx">{{ docBusy ? '上传解析中…' : '上传制度 .docx' }}</button>
                <input id="policy-docx-input" type="file" accept=".docx" style="display:none" @change="onPolicyDocxPicked" />
                <span v-if="docErr" class="errmsg">{{ docErr }}</span>
                <span v-if="docMsg" class="okmsg">{{ docMsg }}</span>
              </div>

              <div class="sec-t" style="margin-top:16px">签署确认 <span class="cnt">{{ signoffs.length }}</span></div>
              <div v-if="!signoffs.length" class="muted">暂无签署记录（仅生效制度可签署）</div>
              <div v-for="s in signoffs" :key="s.id" class="sig-row">
                <span class="sig-av">{{ (s.signer || '·').charAt(0) }}</span>{{ s.signer }}
                <span class="muted" style="margin-left:auto">{{ fmt(s.signedAt) }}</span>
              </div>
            </div>

            <!-- 右：版本时间线 + 引用关系 -->
            <div>
              <div class="sec-t" style="display:flex;align-items:center">版本时间线
                <button v-if="detail.status === 'EFFECTIVE'" class="btn ghost sm" style="margin-left:auto" :disabled="!canWrite('policy.submit')" @click="showRevise = true">修订</button>
              </div>
              <div class="vline">
                <div class="v-item cur">
                  <div class="vn">v{{ detail.version }}（当前）</div>
                  <div class="vd">{{ $t('policy.status.' + detail.status) }} · {{ fmt(detail.updatedAt) }}</div>
                </div>
                <div v-for="v in versions" :key="v.id" class="v-item">
                  <div class="vn">v{{ v.versionNo }}</div>
                  <div class="vd">{{ v.changedBy || '—' }} · {{ fmt(v.createdAt) }}</div>
                  <div class="vc">{{ v.note || v.title }}</div>
                </div>
                <div v-if="!versions.length" class="muted">无历史版本（未经修订）</div>
              </div>

              <div class="sec-t" style="margin-top:16px">引用关系</div>
              <div class="refblock">
                <div class="ref-h">本制度引用：</div>
                <div v-for="r in refs.outgoing" :key="'o'+r.id" class="ref-row">→ {{ policyName(r.refPolicyId) }} <span class="muted">{{ r.note }}</span></div>
                <div v-if="!refs.outgoing?.length" class="muted">无</div>
                <div class="ref-h" style="margin-top:8px">被以下制度引用：</div>
                <div v-for="r in refs.incoming" :key="'i'+r.id" class="ref-row">← {{ policyName(r.policyId) }} <span class="muted">{{ r.note }}</span></div>
                <div v-if="!refs.incoming?.length" class="muted">无</div>
                <div class="ref-add">
                  <select v-model.number="refForm.refPolicyId" class="tool-sel">
                    <option :value="0" disabled>— 选择被引用制度 —</option>
                    <option v-for="p in policies.filter(x => x.id !== detail.id)" :key="p.id" :value="p.id">{{ p.code }} · {{ p.title }}</option>
                  </select>
                  <input v-model="refForm.note" class="tool-input" placeholder="引用说明(可空)" />
                  <button class="btn sm" :disabled="!refForm.refPolicyId || !canWrite('policy')" @click="addRef">添加</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 修订表单（仅 EFFECTIVE）-->
          <div v-if="showRevise" class="revbox">
            <div class="sec-t">修订（旧版存快照 · 版本+1 · 回评审重走审批）</div>
            <label class="fld">新标题<input v-model="rv.title" /></label>
            <label class="fld">新正文<input v-model="rv.content" /></label>
            <label class="fld">修订说明<input v-model="rv.note" placeholder="如 新增远程办公条款" /></label>
            <div style="display:flex;gap:8px">
              <button class="btn sm" :disabled="!rv.title || saving" @click="submitRevise">确认修订</button>
              <button class="btn ghost sm" @click="showRevise = false">取消</button>
            </div>
          </div>

          <p v-if="opError" class="cerr" style="margin-top:10px">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn" @click="detail = null">关闭</button>
          </div>
        </div>
      </div>

      <!-- 新建制度弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('policy.create.btn') }}</h3>
          <label class="fld">{{ $t('policy.th.code') }}<input v-model="cf.code" placeholder="POL-KYC-001" /></label>
          <label class="fld">{{ $t('policy.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('policy.create.content') }}<input v-model="cf.content" /></label>
          <label class="fld">{{ $t('policy.create.org') }}
            <select v-model.number="cf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('policy.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 驳回弹窗（原因）-->
      <div v-if="showReject" class="modal-mask" @click.self="showReject = false">
        <div class="modal-card">
          <h3>{{ $t('policy.reject.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ rejectTarget && rejectTarget.title }}</p>
          <label class="fld">{{ $t('policy.reject.reason') }}<input v-model="rejectReason" :placeholder="$t('policy.reject.reasonPh')" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showReject = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!rejectReason || saving" @click="submitReject">{{ saving ? $t('common.submitting') : $t('policy.op.reject') }}</button>
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
import { canWrite } from '@/auth.js'
const orgOptions = useOrgs()

const policies = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)
const ST_CLS = { DRAFT: 'wait', REVIEW: 'doing', EFFECTIVE: 'ok', DEPRECATED: 'over' }
const FW_LABEL = { ISO27001: 'ISO 27001', MLPS: '等保三级', PIPL: 'PIPL 个保', PBOC: 'PBOC 监管', PCI_DSS: 'PCI DSS', GENERAL: '通用' }

// KPI + 工具栏筛选
const kpi = computed(() => ({
  effective: policies.value.filter((p) => p.status === 'EFFECTIVE').length,
  review: policies.value.filter((p) => p.status === 'REVIEW').length,
  draft: policies.value.filter((p) => p.status === 'DRAFT').length
}))
const q = ref('')
const fwFilter = ref('')
const filtered = computed(() => policies.value.filter((p) =>
  (!fwFilter.value || p.framework === fwFilter.value) &&
  (!q.value.trim() || (p.title + p.code).toLowerCase().includes(q.value.trim().toLowerCase()))
))
const policyName = (id) => { const p = policies.value.find((x) => x.id === id); return p ? p.code + ' · ' + p.title : '#' + id }
function fmt(t) { if (!t) return '—'; try { return new Date(t).toLocaleDateString() } catch (e) { return t } }

// ===== M1 深度：详情抽屉（元数据/版本/引用/签署/修订）=====
const detail = ref(null)
const versions = ref([])
const refs = ref({ outgoing: [], incoming: [] })
const signoffs = ref([])
const mf = reactive({ framework: '', effectiveDate: '', reviewCycleMonths: null, ownerDept: '', owner: '' })
const metaMsg = ref('')
const showRevise = ref(false)
const rv = reactive({ title: '', content: '', note: '' })
const refForm = reactive({ refPolicyId: 0, note: '' })

async function openDetail(p) {
  detail.value = p
  Object.assign(mf, { framework: p.framework || '', effectiveDate: p.effectiveDate || '', reviewCycleMonths: p.reviewCycleMonths || null, ownerDept: p.ownerDept || '', owner: p.owner || '' })
  Object.assign(rv, { title: p.title, content: p.content || '', note: '' })
  refForm.refPolicyId = 0; refForm.note = ''; metaMsg.value = ''; opError.value = ''; showRevise.value = false
  try {
    const [v, r, s] = await Promise.all([
      api.get('/policies/' + p.id + '/versions'),
      api.get('/policies/' + p.id + '/refs'),
      api.get('/policies/' + p.id + '/signoffs')
    ])
    versions.value = v; refs.value = r; signoffs.value = s
  } catch (e) { versions.value = []; refs.value = { outgoing: [], incoming: [] }; signoffs.value = [] }
}
// ===== 六轮 #6：制度原件上传（.docx → 后端 POI 提取全文 + sha256 固化）=====
const docBusy = ref(false)
const docErr = ref('')
const docMsg = ref('')
function pickPolicyDocx() { document.getElementById('policy-docx-input').click() }
async function onPolicyDocxPicked(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file || !detail.value) return
  docBusy.value = true; docErr.value = ''; docMsg.value = ''
  try {
    const fd = new FormData()
    fd.append('file', file)
    const updated = await api.upload('/policies/' + detail.value.id + '/document', fd)
    detail.value = updated
    docMsg.value = '已上传并提取全文 ' + (updated.content ? updated.content.length : 0) + ' 字'
    await load()
    setTimeout(() => (docMsg.value = ''), 3000)
  } catch (err) { docErr.value = err.message } finally { docBusy.value = false }
}

async function saveMeta() {
  saving.value = true; opError.value = ''; metaMsg.value = ''
  try {
    const updated = await api.put('/policies/' + detail.value.id + '/meta', {
      framework: mf.framework || null, effectiveDate: mf.effectiveDate || null,
      reviewCycleMonths: mf.reviewCycleMonths || null, ownerDept: mf.ownerDept || null, owner: mf.owner || null
    })
    detail.value = updated; metaMsg.value = '已保存'
    await load()
    setTimeout(() => (metaMsg.value = ''), 2200)
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
async function submitRevise() {
  saving.value = true; opError.value = ''
  try {
    const updated = await api.post('/policies/' + detail.value.id + '/revise', { title: rv.title, content: rv.content, note: rv.note || null })
    showRevise.value = false
    await load()
    await openDetail(updated)
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
async function addRef() {
  opError.value = ''
  try {
    await api.post('/policies/' + detail.value.id + '/refs', { refPolicyId: refForm.refPolicyId, note: refForm.note || null })
    refs.value = await api.get('/policies/' + detail.value.id + '/refs')
    refForm.refPolicyId = 0; refForm.note = ''
  } catch (e) { opError.value = e.message }
}

async function load() {
  loadError.value = ''
  try { policies.value = await api.get('/policies') } catch (e) { loadError.value = e.message; policies.value = [] }
}
// 一键流转：submit/approve/signoff/archive（驳回走单独弹窗带原因）
async function act(p, op) {
  busyId.value = p.id; opError.value = ''
  try { await api.post('/policies/' + p.id + '/' + op, {}); await load() }
  catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 新建
const showCreate = ref(false)
const cf = reactive({ code: '', title: '', content: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', title: '', content: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try { await api.post('/policies', { orgId: cf.orgId, code: cf.code, title: cf.title, content: cf.content }); showCreate.value = false; await load() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 驳回（带原因）
const showReject = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
function openReject(p) { rejectTarget.value = p; rejectReason.value = ''; opError.value = ''; showReject.value = true }
async function submitReject() {
  saving.value = true; opError.value = ''
  try { await api.post('/policies/' + rejectTarget.value.id + '/reject', { reason: rejectReason.value }); showReject.value = false; await load() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.ghost.danger { color: var(--danger); border-color: var(--danger); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
td.ops { display: flex; gap: 6px; align-items: center; }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.muted { color: var(--text-3); font-size: 11.5px; }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.doing { color: var(--accent-strong); } .st.doing .d { background: var(--accent); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
/* ===== M1 深度 ===== */
.kpibar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.kpibar .kc { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 14px 16px; box-shadow: var(--shadow-1); }
.kpibar .l { font-size: 11.5px; color: var(--text-3); margin-bottom: 6px; }
.kpibar .v { font-size: 22px; font-weight: 760; font-family: var(--font-display); }
.tool-input { margin-left: auto; height: 32px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; width: 180px; }
.tool-sel { height: 32px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover { background: var(--accent-tint); }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--accent-weak); color: var(--accent-strong); }
.modal-card.wide { width: 760px; max-height: 86vh; overflow-y: auto; }
.dgrid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 6px; }
@media (max-width: 760px) { .dgrid2 { grid-template-columns: 1fr; } }
.sec-t { font-size: 12.5px; font-weight: 700; color: var(--text-1); border-left: 3px solid var(--accent-strong); padding-left: 8px; margin: 4px 0 10px; }
.okmsg { color: var(--success); font-size: 12px; font-weight: 600; margin-left: 8px; }
.errmsg { color: var(--danger); font-size: 12px; margin-left: 8px; }
/* 六轮 #6：制度原件区 */
.docbox { border: 1px solid var(--surface-border); border-radius: var(--radius-md); padding: 10px 12px; background: var(--bg); }
.doc-row { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: var(--text-2); margin-bottom: 7px; }
.doc-row.okc { color: var(--success); }
.doc-row.warnc { color: var(--warning); }
.mini-a { font-size: 11px; color: var(--accent-strong); text-decoration: none; padding: 2px 8px; border: 1px solid var(--surface-border); border-radius: 6px; }
.mini-a:hover { border-color: var(--accent); }
.sig-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 12.5px; border-bottom: 1px solid var(--border-subtle); }
.sig-av { width: 22px; height: 22px; border-radius: 50%; background: var(--accent-weak); color: var(--accent-strong); font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
.vline { border-left: 2px solid var(--border); padding-left: 14px; display: flex; flex-direction: column; gap: 10px; }
.v-item { position: relative; }
.v-item::before { content: ''; position: absolute; left: -19px; top: 4px; width: 8px; height: 8px; border-radius: 50%; background: var(--text-4); }
.v-item.cur::before { background: var(--accent); box-shadow: 0 0 0 3px var(--accent-tint); }
.v-item .vn { font-size: 12.5px; font-weight: 700; }
.v-item.cur .vn { color: var(--accent-strong); }
.v-item .vd { font-size: 11px; color: var(--text-3); }
.v-item .vc { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.refblock .ref-h { font-size: 11.5px; color: var(--text-3); font-weight: 700; margin-bottom: 4px; }
.ref-row { font-size: 12.5px; padding: 3px 0; }
.ref-add { display: flex; gap: 6px; margin-top: 10px; align-items: center; }
.ref-add .tool-input { margin-left: 0; width: 120px; }
.revbox { margin-top: 14px; padding: 12px; background: var(--bg); border-radius: 10px; border: 1px dashed var(--accent); }
</style>
