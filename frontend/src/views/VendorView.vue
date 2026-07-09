<template>
  <!--
    第三方供应商（VendorView）：登记 / 评估 / 准入门控启用 / 监测。
    功能真源 = 后端 /api/vendors；视觉遵 tokens.css。准入门控红线：未评估(riskLevel 为空)不得启用，按钮禁用并标因。
  -->
  <AppShell>
    <section class="view view-vendor">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('vendor.tag') }}</div>
          <h1>{{ $t('vendor.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('vendor')" :title="canWrite('vendor') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('vendor.create.btn') }}</button>
      </div>

      <!-- M7 深度：Tab（台账 / SLA 跟踪 / 事件触发复评）-->
      <div class="tabbar">
        <button :class="{ on: tab === 'ledger' }" @click="tab = 'ledger'">供应商台账</button>
        <button :class="{ on: tab === 'sla' }" @click="tab = 'sla'">SLA 跟踪</button>
        <button :class="{ on: tab === 'incident' }" @click="tab = 'incident'">事件触发复评</button>
      </div>

      <div v-show="tab === 'ledger'" class="g">
        <!-- 供应商列表 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('vendor.list') }}</h3><span class="cnt">{{ vendors.length }}</span></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <table style="min-width: 640px">
              <thead><tr>
                <th>{{ $t('vendor.th.code') }}</th><th>{{ $t('vendor.th.name') }}</th>
                <th>{{ $t('vendor.th.risk') }}</th><th>{{ $t('vendor.th.status') }}</th><th>{{ $t('vendor.th.op') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="v in vendors" :key="v.id" class="clk" :class="{ on: v.id === selectedId }" @click="selectVendor(v)">
                  <td class="code">{{ v.code }}</td>
                  <td>{{ v.name }}</td>
                  <td><span v-if="v.riskLevel" class="tag" :class="riskCls(v.riskLevel)">{{ $t('vendor.level.' + v.riskLevel) }}</span><span v-else class="muted">{{ $t('vendor.unassessed') }}</span></td>
                  <td><span class="st" :class="vStCls(v.status)"><span class="d"></span>{{ $t('vendor.status.' + v.status) }}</span></td>
                  <td class="ops" @click.stop>
                    <button v-if="v.status !== 'TERMINATED'" class="btn ghost sm" @click="openAssess(v)">{{ $t('vendor.op.assess') }}</button>
                    <button v-if="v.status === 'ONBOARDING'" class="btn sm" :disabled="!v.riskLevel || busyId === v.id" :title="!v.riskLevel ? $t('vendor.gateTip') : ''" @click="act(v, 'activate')">{{ $t('vendor.op.activate') }}</button>
                    <button v-else-if="v.status === 'ACTIVE'" class="btn ghost sm" :disabled="busyId === v.id" @click="act(v, 'suspend')">{{ $t('vendor.op.suspend') }}</button>
                    <button v-else-if="v.status === 'SUSPENDED'" class="btn sm" :disabled="busyId === v.id" @click="act(v, 'reactivate')">{{ $t('vendor.op.reactivate') }}</button>
                  </td>
                </tr>
                <tr v-if="!vendors.length"><td colspan="5" class="emptyrow">{{ loadError || $t('vendor.empty') }}</td></tr>
              </tbody>
            </table>
          </div>
          <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
        </div>

        <!-- 右栏：评估历史 + 技术安全/DPA -->
        <div>
          <div class="card">
            <div class="ch"><h3>{{ $t('vendor.assessHist') }}</h3><span class="sub">四类：准入/年度/续约/事件复评</span></div>
            <div class="cb" style="overflow-x: auto; padding-top: 0">
              <div v-if="!selectedId" class="hint">{{ $t('vendor.selectHint') }}</div>
              <table v-else style="min-width: 460px">
                <thead><tr><th>类型</th><th>{{ $t('vendor.ath.risk') }}</th><th>{{ $t('vendor.ath.score') }}</th><th>{{ $t('vendor.ath.assessor') }}</th><th>{{ $t('vendor.ath.concl') }}</th><th>评估依据 / 过程文档</th></tr></thead>
                <tbody>
                  <tr v-for="a in assessments" :key="a.id">
                    <td><span class="pill">{{ AT_LABEL[a.assessType] || a.assessType }}</span></td>
                    <td><span class="tag" :class="riskCls(a.riskLevel)">{{ $t('vendor.level.' + a.riskLevel) }}</span></td>
                    <td class="num">{{ a.score == null ? '—' : a.score }}</td>
                    <td>{{ a.assessor }}</td>
                    <td class="desc">{{ a.conclusion }}</td>
                    <td style="min-width:200px">
                      <div v-if="a.basis" style="font-size:11.5px;color:var(--text-2);margin-bottom:4px">{{ a.basis }}</div>
                      <div style="display:flex;align-items:center;gap:6px">
                        <a v-if="a.docName" class="mini" :href="docUrl(a.id)" target="_blank" :title="'sha256 ' + (a.docSha256||'').slice(0,12)">📎 {{ a.docName }}</a>
                        <button v-if="canWrite('vendor')" class="mini" @click="pickAssessDoc(a)">{{ a.docName ? '换原件' : '⇪ 上传原件' }}</button>
                        <span v-if="!a.basis && !a.docName" class="muted" style="font-size:11px">未留存</span>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!assessments.length"><td colspan="6" class="emptyrow">{{ $t('vendor.assessEmpty') }}</td></tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 技术安全 / DPA 合规（需求 9.3.1 两卡）-->
          <div class="card" style="margin-top: 14px">
            <div class="ch"><h3>技术安全 / DPA 合规</h3>
              <button v-if="selectedId" class="btn sm" style="margin-left:auto" :disabled="!canWrite('vendor') || saving" @click="saveCompliance">保存</button>
            </div>
            <div class="cb">
              <div v-if="!selectedId" class="hint">{{ $t('vendor.selectHint') }}</div>
              <template v-else>
                <div class="cgrid">
                  <label class="fld">数据驻留<input v-model="comp.dataResidency" placeholder="如 境内 / 新加坡" /></label>
                  <label class="fld">自身认证<input v-model="comp.certifications" placeholder="ISO27001,PCI DSS" /></label>
                  <label class="fld" style="grid-column:1/-1">再委托说明<input v-model="comp.subProcessing" placeholder="如 无再委托 / 短信下游XX" /></label>
                </div>
                <div class="chkrow">
                  <label class="chk"><input type="checkbox" v-model="comp.pciScope" /> PCI 范围内</label>
                  <label class="chk"><input type="checkbox" v-model="comp.dpaSigned" /> DPA 已签</label>
                  <label class="chk"><input type="checkbox" v-model="comp.crossBorder" /> 涉跨境</label>
                </div>
                <p v-if="compMsg" class="okmsg">{{ compMsg }}</p>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== Tab · SLA 跟踪 ===== -->
      <div v-show="tab === 'sla'" class="card">
        <div class="ch"><h3>SLA 跟踪</h3><span class="cnt">{{ slas.length }}</span>
          <button class="btn sm" style="margin-left:auto" :disabled="!canWrite('vendor')" @click="openSla">＋ 新增 SLA 项</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:680px">
            <thead><tr><th>供应商</th><th>SLA 项</th><th>目标</th><th>实际</th><th>到期</th><th>达标</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="s in slas" :key="s.id">
                <td>{{ vendorName(s.vendorId) }}</td>
                <td><b>{{ s.item }}</b></td>
                <td class="num">{{ s.target || '—' }}</td>
                <td class="num">{{ s.actual || '—' }}</td>
                <td class="num">{{ s.dueDate || '—' }}</td>
                <td><span class="st" :class="s.met ? 'ok' : 'over'"><span class="d"></span>{{ s.met ? '达标' : '未达标' }}</span></td>
                <td class="ops"><button v-if="canWrite('vendor')" class="btn ghost sm" @click="openTrack(s)">回填</button></td>
              </tr>
              <tr v-if="!slas.length"><td colspan="7" class="emptyrow">暂无 SLA 项，点「＋ 新增 SLA 项」。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== Tab · 事件触发复评 ===== -->
      <div v-show="tab === 'incident'" class="card">
        <div class="ch"><h3>外部事件与复评闭环</h3><span class="cnt">{{ incidents.length }}</span>
          <span class="sub">登记(OPEN) → 触发复评(REASSESSING) → 闭环(CLOSED)</span>
          <button class="btn sm" style="margin-left:auto" :disabled="!canWrite('vendor')" @click="openIncident">＋ 登记事件</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>供应商</th><th>事件</th><th>来源</th><th>风险</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="i in incidents" :key="i.id">
                <td>{{ vendorName(i.vendorId) }}</td>
                <td><b>{{ i.event }}</b></td>
                <td>{{ i.source || '—' }}</td>
                <td><span class="tag" :class="riskCls(i.riskLevel)">{{ $t('vendor.level.' + i.riskLevel) }}</span></td>
                <td><span class="st" :class="INC_CLS[i.status]"><span class="d"></span>{{ INC_LABEL[i.status] }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('vendor')">
                    <button v-if="i.status === 'OPEN'" class="btn sm" @click="openReassess(i)">触发复评</button>
                    <button v-if="i.status === 'REASSESSING'" class="btn ghost sm" @click="closeIncident(i)">闭环</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!incidents.length"><td colspan="6" class="emptyrow">暂无外部事件。</td></tr>
            </tbody>
          </table>
          <p v-if="opError" class="cerr">{{ opError }}</p>
        </div>
      </div>

      <!-- 登记供应商弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('vendor.create.btn') }}</h3>
          <label class="fld">{{ $t('vendor.th.code') }}<input v-model="cf.code" /></label>
          <label class="fld">{{ $t('vendor.th.name') }}<input v-model="cf.name" /></label>
          <label class="fld">{{ $t('vendor.create.category') }}<input v-model="cf.category" /></label>
          <label class="fld">{{ $t('vendor.create.criticality') }}<input v-model="cf.criticality" /></label>
          <label class="fld">{{ $t('vendor.create.org') }}
            <select v-model.number="cf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.name || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('vendor.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 评估弹窗 -->
      <div v-if="showAssess" class="modal-mask" @click.self="showAssess = false">
        <div class="modal-card">
          <h3>{{ $t('vendor.assess.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ assessTarget && assessTarget.name }}</p>
          <label class="fld">评估类型
            <select v-model="af.assessType">
              <option value="ONBOARDING">首次准入</option><option value="ANNUAL">年度定期</option>
              <option value="RENEWAL">续约评估</option><option value="EVENT">事件复评</option>
            </select>
          </label>
          <label class="fld">{{ $t('vendor.assess.risk') }}
            <select v-model="af.riskLevel">
              <option value="VERY_LOW">{{ $t('vendor.level.VERY_LOW') }}</option><option value="LOW">{{ $t('vendor.level.LOW') }}</option>
              <option value="MID">{{ $t('vendor.level.MID') }}</option><option value="HIGH">{{ $t('vendor.level.HIGH') }}</option>
              <option value="VERY_HIGH">{{ $t('vendor.level.VERY_HIGH') }}</option>
            </select>
          </label>
          <label class="fld">{{ $t('vendor.assess.score') }}<input v-model.number="af.score" type="number" min="0" max="100" /></label>
          <label class="fld">{{ $t('vendor.assess.concl') }}<textarea v-model="af.conclusion" rows="3" class="mtext"></textarea></label>
          <label class="fld">评估依据（所用评估表单/标准/维度，过程可溯）<textarea v-model="af.basis" rows="3" class="mtext" placeholder="如 依据《第三方安全评估表》五维评分：资质/数据安全/连续性/合规/事件史"></textarea></label>
          <p style="font-size:11px;color:var(--text-3);margin:-6px 0 8px">提示：登记后可在评估历史该行「上传原件」附评估表单/报告（sha256 固化留存）。</p>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAssess = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="saving" @click="submitAssess">{{ saving ? $t('common.submitting') : $t('vendor.assess.ok') }}</button>
          </div>
        </div>
      </div>
      <!-- 新增 SLA 弹窗 -->
      <div v-if="showSla" class="modal-mask" @click.self="showSla = false">
        <div class="modal-card">
          <h3>新增 SLA 项</h3>
          <label class="fld">供应商
            <select v-model.number="sf.vendorId"><option v-for="v in vendors" :key="v.id" :value="v.id">{{ v.code }} · {{ v.name }}</option></select>
          </label>
          <label class="fld">SLA 项<input v-model="sf.item" placeholder="如 到达率 / 可用率" /></label>
          <label class="fld">目标<input v-model="sf.target" placeholder="如 ≥98%" /></label>
          <label class="fld">当前实际<input v-model="sf.actual" placeholder="如 99.1%（可空）" /></label>
          <label class="fld">到期日期<input type="date" v-model="sf.dueDate" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showSla = false">取消</button>
            <button class="btn" :disabled="!sf.vendorId || !sf.item || saving" @click="submitSla">确认</button>
          </div>
        </div>
      </div>

      <!-- SLA 回填弹窗 -->
      <div v-if="trackTarget" class="modal-mask" @click.self="trackTarget = null">
        <div class="modal-card">
          <h3>回填 SLA</h3>
          <p class="muted" style="margin:-6px 0 14px">{{ trackTarget.item }}（目标 {{ trackTarget.target }}）</p>
          <label class="fld">实际值<input v-model="tf.actual" /></label>
          <label class="chk"><input type="checkbox" v-model="tf.met" /> 达标</label>
          <div class="modal-actions">
            <button class="btn ghost" @click="trackTarget = null">取消</button>
            <button class="btn" :disabled="saving" @click="submitTrack">确认</button>
          </div>
        </div>
      </div>

      <!-- 登记外部事件弹窗 -->
      <div v-if="showIncident" class="modal-mask" @click.self="showIncident = false">
        <div class="modal-card">
          <h3>登记外部事件</h3>
          <label class="fld">供应商
            <select v-model.number="incf.vendorId"><option v-for="v in vendors" :key="v.id" :value="v.id">{{ v.code }} · {{ v.name }}</option></select>
          </label>
          <label class="fld">事件<input v-model="incf.event" placeholder="如 被曝数据泄露" /></label>
          <label class="fld">来源<input v-model="incf.source" placeholder="媒体 / 监管通报 / 客户投诉" /></label>
          <label class="fld">事件风险等级
            <select v-model="incf.riskLevel">
              <option value="VERY_HIGH">极高</option><option value="HIGH">高</option><option value="MID">中</option><option value="LOW">低</option>
            </select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showIncident = false">取消</button>
            <button class="btn" :disabled="!incf.vendorId || !incf.event || saving" @click="submitIncident">确认登记</button>
          </div>
        </div>
      </div>

      <!-- 触发复评弹窗（登记 EVENT 类评估）-->
      <div v-if="reassessTarget" class="modal-mask" @click.self="reassessTarget = null">
        <div class="modal-card">
          <h3>事件触发复评</h3>
          <p class="muted" style="margin:-6px 0 14px">{{ reassessTarget.event }} · {{ vendorName(reassessTarget.vendorId) }}</p>
          <label class="fld">复评风险等级
            <select v-model="rf2.riskLevel">
              <option value="VERY_HIGH">极高</option><option value="HIGH">高</option><option value="MID">中</option><option value="LOW">低</option><option value="VERY_LOW">极低</option>
            </select>
          </label>
          <label class="fld">得分<input v-model.number="rf2.score" type="number" min="0" max="100" /></label>
          <label class="fld">复评结论<textarea v-model="rf2.conclusion" rows="3" class="mtext"></textarea></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="reassessTarget = null">取消</button>
            <button class="btn" :disabled="saving" @click="submitReassess">确认复评</button>
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

const vendors = ref([])
const loadError = ref('')
const selectedId = ref(null)
const assessments = ref([])
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)

async function loadVendors() {
  loadError.value = ''
  try { vendors.value = await api.get('/vendors') } catch (e) { loadError.value = e.message; vendors.value = [] }
}
async function selectVendor(v) {
  selectedId.value = v.id
  try { assessments.value = await api.get('/vendors/' + v.id + '/assessments') } catch (e) { assessments.value = [] }
  // 加载技术安全/DPA 合规属性（就地编辑）
  const full = vendors.value.find((x) => x.id === v.id) || {}
  Object.assign(comp, {
    dataResidency: full.dataResidency || '', pciScope: !!full.pciScope, certifications: full.certifications || '',
    dpaSigned: !!full.dpaSigned, crossBorder: !!full.crossBorder, subProcessing: full.subProcessing || ''
  })
  compMsg.value = ''
}
const LEVEL_KEY = { VERY_HIGH: 'vh', HIGH: 'h', MID: 'm', LOW: 'l', VERY_LOW: 'vl' }
const riskCls = (lv) => 'lvl-' + (LEVEL_KEY[lv] || 'm')
const vStCls = (s) => ({ ONBOARDING: 'wait', ACTIVE: 'ok', SUSPENDED: 'over', TERMINATED: 'wait' }[s] || 'wait')

async function act(v, op) {
  busyId.value = v.id; opError.value = ''
  try {
    await api.post('/vendors/' + v.id + '/' + op, {})
    await loadVendors()
  } catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 登记
const showCreate = ref(false)
const cf = reactive({ code: '', name: '', category: '', criticality: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', name: '', category: '', criticality: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors', { orgId: cf.orgId, code: cf.code, name: cf.name, category: cf.category, criticality: cf.criticality })
    showCreate.value = false; await loadVendors()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 评估
const showAssess = ref(false)
const assessTarget = ref(null)
const af = reactive({ riskLevel: 'MID', score: 70, conclusion: '', assessType: 'ONBOARDING', basis: '' })
function openAssess(v) { assessTarget.value = v; Object.assign(af, { riskLevel: 'MID', score: 70, conclusion: '', assessType: 'ONBOARDING', basis: '' }); opError.value = ''; showAssess.value = true }
async function submitAssess() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors/' + assessTarget.value.id + '/assessments', { riskLevel: af.riskLevel, score: af.score, conclusion: af.conclusion, assessType: af.assessType, basis: af.basis || null })
    showAssess.value = false
    await loadVendors()
    if (selectedId.value === assessTarget.value.id) await selectVendor({ id: assessTarget.value.id })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
// #2 评估过程文档：原件下载 URL + 上传（每条评估一份评估表单/报告，sha256 固化）
function docUrl(assessmentId) { return '/api/vendors/assessments/' + assessmentId + '/document' }
const assessDocInput = ref(null)
const assessDocTarget = ref(null)
function pickAssessDoc(a) {
  assessDocTarget.value = a
  if (!assessDocInput.value) {
    const inp = document.createElement('input')
    inp.type = 'file'
    inp.onchange = onAssessDoc
    assessDocInput.value = inp
  }
  assessDocInput.value.value = ''
  assessDocInput.value.click()
}
async function onAssessDoc(e) {
  const file = e.target.files && e.target.files[0]
  if (!file || !assessDocTarget.value) return
  try {
    const fd = new FormData(); fd.append('file', file)
    await api.upload('/vendors/assessments/' + assessDocTarget.value.id + '/document', fd)
    if (selectedId.value) await selectVendor({ id: selectedId.value })
  } catch (err) { window.alert('上传失败：' + err.message) }
}

// ===== M7 深度 =====
const tab = ref('ledger')
const AT_LABEL = { ONBOARDING: '准入', ANNUAL: '年度', RENEWAL: '续约', EVENT: '事件复评' }
const INC_LABEL = { OPEN: '待处置', REASSESSING: '复评中', CLOSED: '已闭环' }
const INC_CLS = { OPEN: 'over', REASSESSING: 'wait', CLOSED: 'ok' }
const vendorName = (id) => { const v = vendors.value.find((x) => x.id === id); return v ? v.name : '#' + id }

// 技术安全/DPA
const comp = reactive({ dataResidency: '', pciScope: false, certifications: '', dpaSigned: false, crossBorder: false, subProcessing: '' })
const compMsg = ref('')
async function saveCompliance() {
  saving.value = true; opError.value = ''; compMsg.value = ''
  try {
    await api.put('/vendors/' + selectedId.value + '/compliance', { ...comp })
    compMsg.value = '已保存'; await loadVendors()
    setTimeout(() => (compMsg.value = ''), 2200)
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// SLA
const slas = ref([])
async function loadSlas() { try { slas.value = await api.get('/vendors/sla') } catch (e) { slas.value = [] } }
const showSla = ref(false)
const sf = reactive({ vendorId: 0, item: '', target: '', actual: '', dueDate: '' })
function openSla() { Object.assign(sf, { vendorId: vendors.value[0]?.id || 0, item: '', target: '', actual: '', dueDate: '' }); opError.value = ''; showSla.value = true }
async function submitSla() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors/' + sf.vendorId + '/sla', { item: sf.item, target: sf.target || null, actual: sf.actual || null, dueDate: sf.dueDate || null, met: true })
    showSla.value = false; await loadSlas()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
const trackTarget = ref(null)
const tf = reactive({ actual: '', met: true })
function openTrack(s) { trackTarget.value = s; Object.assign(tf, { actual: s.actual || '', met: s.met }); showSla.value = false }
async function submitTrack() {
  saving.value = true
  try { await api.put('/vendors/sla/' + trackTarget.value.id, { actual: tf.actual, met: tf.met }); trackTarget.value = null; await loadSlas() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 事件触发复评
const incidents = ref([])
async function loadIncidents() { try { incidents.value = await api.get('/vendors/incidents') } catch (e) { incidents.value = [] } }
const showIncident = ref(false)
const incf = reactive({ vendorId: 0, event: '', source: '', riskLevel: 'HIGH' })
function openIncident() { Object.assign(incf, { vendorId: vendors.value[0]?.id || 0, event: '', source: '', riskLevel: 'HIGH' }); opError.value = ''; showIncident.value = true }
async function submitIncident() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors/' + incf.vendorId + '/incidents', { event: incf.event, source: incf.source || null, riskLevel: incf.riskLevel })
    showIncident.value = false; await loadIncidents()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
const reassessTarget = ref(null)
const rf2 = reactive({ riskLevel: 'HIGH', score: 55, conclusion: '' })
function openReassess(i) { reassessTarget.value = i; Object.assign(rf2, { riskLevel: 'HIGH', score: 55, conclusion: '' }); opError.value = '' }
async function submitReassess() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors/incidents/' + reassessTarget.value.id + '/reassess', { riskLevel: rf2.riskLevel, score: rf2.score, conclusion: rf2.conclusion })
    reassessTarget.value = null; await loadIncidents(); await loadVendors()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
async function closeIncident(i) {
  opError.value = ''
  try { await api.post('/vendors/incidents/' + i.id + '/close', {}); await loadIncidents() } catch (e) { opError.value = e.message }
}

onMounted(() => { loadVendors(); loadSlas(); loadIncidents() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.g { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; }
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
.hint { color: var(--text-3); font-size: 12.5px; padding: 18px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
td.ops { display: flex; gap: 6px; }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.desc { color: var(--text-2); max-width: 200px; }
.muted { color: var(--text-3); font-size: 11.5px; }
.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 700; }
.tag.lvl-vh { background: #f1d9d6; color: #7a1620; }
.tag.lvl-h { background: var(--danger-tint); color: var(--danger); }
.tag.lvl-m { background: var(--warning-tint); color: #a87d22; }
.tag.lvl-l { background: var(--safe-weak); color: var(--safe); }
.tag.lvl-vl { background: rgba(120,120,120,0.12); color: var(--text-2); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
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
/* M7 深度 */
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
.ch .sub { font-size: 11px; color: var(--text-3); }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--accent-weak); color: var(--accent-strong); }
.cgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.cgrid .fld, .fld { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 8px; }
.cgrid .fld input { display: block; width: 100%; height: 34px; margin-top: 4px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; box-sizing: border-box; }
.chkrow { display: flex; gap: 16px; margin-top: 6px; }
.chk { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: var(--text-2); }
.okmsg { color: var(--success); font-size: 12px; font-weight: 600; margin: 8px 0 0; }
</style>
