<!-- =============================================================
     反洗钱 AML（GRC 合规管理视角）
     名单管理 + 名单筛查 + 可疑交易报告(STR) + 合规义务/机构自评（引用既有模块）。
     隔离：所有数据经 X-User 头 → visible_orgs → RLS；筛查/报送均在本组织可见域内。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <div class="phead">
        <div><div class="kqt">AML · 反洗钱</div><h1>反洗钱管理</h1></div>
        <div class="sp"></div>
        <button v-if="tab === 'watch'" class="btn" :disabled="!canWrite('aml')" @click="openWatch">＋ 登记名单</button>
        <button v-else-if="tab === 'str'" class="btn" :disabled="!canWrite('aml')" @click="openStr">＋ 登记可疑交易(STR)</button>
      </div>

      <div class="tabbar">
        <button :class="{ on: tab === 'watch' }" @click="tab = 'watch'; loadWatch()">名单管理</button>
        <button :class="{ on: tab === 'screen' }" @click="tab = 'screen'">名单筛查</button>
        <button :class="{ on: tab === 'str' }" @click="tab = 'str'; loadStr()">可疑交易报告 STR</button>
        <button :class="{ on: tab === 'ref' }" @click="tab = 'ref'">合规义务与自评</button>
      </div>

      <!-- 名单管理 -->
      <div v-show="tab === 'watch'" class="card">
        <div class="ch"><h3>名单库</h3><span class="cnt">{{ watchlist.length }}</span><span class="sub">制裁 / PEP / 内部黑名单 · 供客户与交易对手筛查</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:760px">
            <thead><tr><th>类型</th><th>主体</th><th>证件号</th><th>国别</th><th>来源</th><th>原因</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="w in watchlist" :key="w.id">
                <td><span class="tag" :class="LT_CLS[w.listType]">{{ LT_TXT[w.listType] || w.listType }}</span></td>
                <td><b>{{ w.name }}</b></td>
                <td class="mono">{{ w.idNumber || '—' }}</td>
                <td>{{ w.country || '—' }}</td>
                <td class="muted">{{ w.source || '—' }}</td>
                <td class="muted">{{ w.reason || '—' }}</td>
                <td><span class="st" :class="w.status==='ACTIVE'?'ok':'off'"><span class="d"></span>{{ w.status==='ACTIVE'?'生效':'停用' }}</span></td>
                <td style="text-align:right"><button v-if="canWrite('aml') && w.status==='ACTIVE'" class="mini danger" @click="retireWatch(w)">停用</button></td>
              </tr>
              <tr v-if="!watchlist.length"><td colspan="8" class="emptyrow">暂无名单条目，点「＋ 登记名单」。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 名单筛查 -->
      <div v-show="tab === 'screen'" class="card">
        <div class="ch"><h3>名单筛查</h3><span class="sub">对客户/交易对手按名称或证件号比对本组织生效名单</span></div>
        <div class="cb">
          <div class="screen-bar">
            <input v-model="scName" placeholder="主体名称（子串匹配）" />
            <input v-model="scId" placeholder="证件号（精确匹配）" />
            <button class="btn" :disabled="!scName && !scId" @click="doScreen">筛查</button>
          </div>
          <div v-if="scDone" style="margin-top:14px">
            <div v-if="!scHits.length" class="scr-clean">✓ 未命中任何名单（{{ scName || scId }}）——可继续业务</div>
            <div v-else class="scr-hit">
              <div class="scr-hit-h">⚠ 命中 {{ scHits.length }} 条名单，需按反洗钱流程处置（拒绝/尽调/上报）</div>
              <table style="width:100%">
                <thead><tr><th>类型</th><th>主体</th><th>证件号</th><th>命中</th><th>来源</th><th>原因</th></tr></thead>
                <tbody>
                  <tr v-for="(h, i) in scHits" :key="i">
                    <td><span class="tag" :class="LT_CLS[h.entry.listType]">{{ LT_TXT[h.entry.listType] || h.entry.listType }}</span></td>
                    <td><b>{{ h.entry.name }}</b></td>
                    <td class="mono">{{ h.entry.idNumber || '—' }}</td>
                    <td>{{ h.matchBy === 'ID' ? '证件号' : '名称' }}</td>
                    <td class="muted">{{ h.entry.source || '—' }}</td>
                    <td class="muted">{{ h.entry.reason || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- 可疑交易报告 STR -->
      <div v-show="tab === 'str'" class="card">
        <div class="ch"><h3>可疑交易报告</h3><span class="cnt">{{ strs.length }}</span><span class="sub">登记 → 内部提交 → 报送反洗钱监测中心 → 了结</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:880px">
            <thead><tr><th>主体</th><th>金额</th><th>风险</th><th>可疑理由</th><th>状态</th><th>报送信息</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="s in strs" :key="s.id">
                <td><b>{{ s.subject }}</b></td>
                <td class="num">{{ s.amount == null ? '—' : fmtAmt(s.amount) }}</td>
                <td><span class="tag" :class="RL_CLS[s.riskLevel]">{{ RL_TXT[s.riskLevel] || s.riskLevel }}</span></td>
                <td class="muted" style="max-width:220px">{{ s.reason }}</td>
                <td><span class="st" :class="STR_CLS[s.status]"><span class="d"></span>{{ STR_TXT[s.status] || s.status }}</span></td>
                <td class="muted">{{ s.reportNo ? (s.reportedTo || '监测中心') + ' · ' + s.reportNo + ' · ' + (s.reportedDate || '') : '—' }}</td>
                <td style="white-space:nowrap;text-align:right">
                  <template v-if="canWrite('aml')">
                    <button v-if="s.status==='DRAFT'" class="mini" @click="submitStr(s)">提交</button>
                    <button v-if="s.status==='SUBMITTED'" class="mini" @click="openReport(s)">报送</button>
                    <button v-if="s.status==='REPORTED'" class="mini" @click="closeStr(s)">了结</button>
                    <span v-if="s.status==='CLOSED'" class="muted">已了结</span>
                  </template>
                </td>
              </tr>
              <tr v-if="!strs.length"><td colspan="7" class="emptyrow">暂无 STR，点「＋ 登记可疑交易(STR)」。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 合规义务与自评（引用既有模块） -->
      <div v-show="tab === 'ref'" class="card">
        <div class="ch"><h3>合规义务与机构自评</h3><span class="sub">AML 合规视角复用既有能力，避免重复建设</span></div>
        <div class="cb">
          <div class="ref-row">
            <div class="ref-ic">📋</div>
            <div class="ref-tx"><b>反洗钱合规义务</b><span>KYC 客户尽调、可疑交易监测、大额交易报告、名单筛查、员工培训等，作为「合规清单」义务条目登记与落实追踪（落实须留证据）。</span></div>
            <button class="btn ghost sm" @click="go('obligation')">前往合规清单 →</button>
          </div>
          <div class="ref-row">
            <div class="ref-ic">🛡</div>
            <div class="ref-tx"><b>机构反洗钱风险自评</b><span>洗钱与恐怖融资风险自评估（客户/地域/业务/渠道维度）走「风险评估」评估任务与五级风险矩阵，形成发现并纳入整改闭环。</span></div>
            <button class="btn ghost sm" @click="go('risk')">前往风险评估 →</button>
          </div>
          <p class="ref-note">说明：本模块聚焦 AML 特有的<b>名单管理/筛查</b>与<b>可疑交易报告(STR)</b>；义务与自评复用平台既有的合规清单与风险评估，口径一致、留痕统一。</p>
        </div>
      </div>

      <!-- 登记名单 弹窗 -->
      <div v-if="showWatch" class="modal-mask" @click.self="showWatch = false">
        <div class="modal-card">
          <h3>登记名单条目</h3>
          <label class="fld">名单类型
            <select v-model="wf.listType"><option value="SANCTION">制裁名单</option><option value="PEP">政治敏感人物(PEP)</option><option value="INTERNAL">内部黑名单</option></select>
          </label>
          <label class="fld">主体名称<input v-model="wf.name" placeholder="个人姓名 / 机构名称" /></label>
          <label class="fld">证件号 / 组织机构代码<input v-model="wf.idNumber" placeholder="可空" /></label>
          <label class="fld">国别 / 地区<input v-model="wf.country" placeholder="可空" /></label>
          <label class="fld">名单来源<input v-model="wf.source" placeholder="如 OFAC / 联合国 / 公安部 / 内部" /></label>
          <label class="fld">列入原因<input v-model="wf.reason" placeholder="可空" /></label>
          <label class="fld">所属组织<select v-model.number="wf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="err" class="cerr">{{ err }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="showWatch = false">取消</button><button class="btn" :disabled="!wf.name || saving" @click="saveWatch">确认登记</button></div>
        </div>
      </div>

      <!-- 登记 STR 弹窗 -->
      <div v-if="showStr" class="modal-mask" @click.self="showStr = false">
        <div class="modal-card">
          <h3>登记可疑交易报告</h3>
          <label class="fld">可疑主体<input v-model="sf.subject" placeholder="客户 / 交易对手" /></label>
          <label class="fld">涉及金额（元）<input v-model.number="sf.amount" type="number" min="0" placeholder="可空" /></label>
          <label class="fld">风险等级
            <select v-model="sf.riskLevel"><option value="LOW">低</option><option value="MID">中</option><option value="HIGH">高</option></select>
          </label>
          <label class="fld">可疑理由 / 情形<input v-model="sf.reason" placeholder="如 短期大额分拆、与身份不符的资金往来" /></label>
          <label class="fld">可疑交易发生日<input type="date" v-model="sf.occurredDate" /></label>
          <label class="fld">所属组织<select v-model.number="sf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="err" class="cerr">{{ err }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="showStr = false">取消</button><button class="btn" :disabled="!sf.subject || !sf.reason || saving" @click="saveStr">确认登记</button></div>
        </div>
      </div>

      <!-- 报送 STR 弹窗 -->
      <div v-if="showReport" class="modal-mask" @click.self="showReport = false">
        <div class="modal-card">
          <h3>报送反洗钱监测中心 · {{ reportTarget?.subject }}</h3>
          <label class="fld">报送机构<input v-model="rf.reportedTo" placeholder="中国人民银行反洗钱监测分析中心" /></label>
          <label class="fld">报送回执号<input v-model="rf.reportNo" placeholder="监测中心受理回执号（必填）" /></label>
          <label class="fld">报送日期<input type="date" v-model="rf.reportedDate" /></label>
          <p v-if="err" class="cerr">{{ err }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="showReport = false">取消</button><button class="btn" :disabled="!rf.reportNo || saving" @click="doReport">确认报送</button></div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
import { canWrite } from '@/auth.js'
import { confirm } from '@/composables/confirm'

const router = useRouter()
const orgOptions = useOrgs()
const tab = ref('watch')
const err = ref('')
const saving = ref(false)

const LT_TXT = { SANCTION: '制裁', PEP: 'PEP', INTERNAL: '内部黑名单' }
const LT_CLS = { SANCTION: 'h', PEP: 'm', INTERNAL: 'l' }
const RL_TXT = { LOW: '低', MID: '中', HIGH: '高' }
const RL_CLS = { LOW: 'l', MID: 'm', HIGH: 'h' }
const STR_TXT = { DRAFT: '登记', SUBMITTED: '已提交', REPORTED: '已报送', CLOSED: '已了结' }
const STR_CLS = { DRAFT: 'wait', SUBMITTED: 'doing', REPORTED: 'doing', CLOSED: 'ok' }

function fmtAmt(a) { return Number(a).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }
function go(name) { router.push({ name }) }

// ---- 名单管理 ----
const watchlist = ref([])
async function loadWatch() { try { watchlist.value = await api.get('/aml/watchlist') } catch (e) { watchlist.value = [] } }
const showWatch = ref(false)
const wf = reactive({ listType: 'SANCTION', name: '', idNumber: '', country: '', source: '', reason: '', orgId: 12 })
function openWatch() { Object.assign(wf, { listType: 'SANCTION', name: '', idNumber: '', country: '', source: '', reason: '', orgId: 12 }); err.value = ''; showWatch.value = true }
async function saveWatch() {
  saving.value = true; err.value = ''
  try {
    await api.post('/aml/watchlist', { orgId: wf.orgId, listType: wf.listType, name: wf.name, idNumber: wf.idNumber || null, country: wf.country || null, source: wf.source || null, reason: wf.reason || null })
    showWatch.value = false; await loadWatch()
  } catch (e) { err.value = e.message } finally { saving.value = false }
}
async function retireWatch(w) {
  if (!await confirm({ title: '停用名单', message: `停用名单条目「${w.name}」？停用后不再参与筛查（留档）。`, danger: true })) return
  try { await api.post('/aml/watchlist/' + w.id + '/retire', {}); await loadWatch() } catch (e) { window.alert(e.message) }
}

// ---- 名单筛查 ----
const scName = ref(''); const scId = ref(''); const scHits = ref([]); const scDone = ref(false)
async function doScreen() {
  try { scHits.value = await api.post('/aml/screen', { name: scName.value || null, idNumber: scId.value || null }); scDone.value = true }
  catch (e) { window.alert(e.message) }
}

// ---- STR ----
const strs = ref([])
async function loadStr() { try { strs.value = await api.get('/aml/str-reports') } catch (e) { strs.value = [] } }
const showStr = ref(false)
const sf = reactive({ subject: '', amount: null, riskLevel: 'MID', reason: '', occurredDate: '', orgId: 12 })
function openStr() { Object.assign(sf, { subject: '', amount: null, riskLevel: 'MID', reason: '', occurredDate: '', orgId: 12 }); err.value = ''; showStr.value = true }
async function saveStr() {
  saving.value = true; err.value = ''
  try {
    await api.post('/aml/str-reports', { orgId: sf.orgId, subject: sf.subject, amount: sf.amount, riskLevel: sf.riskLevel, reason: sf.reason, occurredDate: sf.occurredDate || null })
    showStr.value = false; await loadStr()
  } catch (e) { err.value = e.message } finally { saving.value = false }
}
async function submitStr(s) {
  if (!await confirm({ title: '提交 STR', message: `将「${s.subject}」的可疑交易报告提交内部复核？` })) return
  try { await api.post('/aml/str-reports/' + s.id + '/submit', {}); await loadStr() } catch (e) { window.alert(e.message) }
}
const showReport = ref(false); const reportTarget = ref(null)
const rf = reactive({ reportedTo: '中国人民银行反洗钱监测分析中心', reportNo: '', reportedDate: '' })
function openReport(s) { reportTarget.value = s; Object.assign(rf, { reportedTo: '中国人民银行反洗钱监测分析中心', reportNo: '', reportedDate: '' }); err.value = ''; showReport.value = true }
async function doReport() {
  saving.value = true; err.value = ''
  try {
    await api.post('/aml/str-reports/' + reportTarget.value.id + '/report', { reportedTo: rf.reportedTo || null, reportNo: rf.reportNo, reportedDate: rf.reportedDate || null })
    showReport.value = false; await loadStr()
  } catch (e) { err.value = e.message } finally { saving.value = false }
}
async function closeStr(s) {
  if (!await confirm({ title: '了结 STR', message: `将「${s.subject}」的可疑交易报告了结（终态）？` })) return
  try { await api.post('/aml/str-reports/' + s.id + '/close', {}); await loadStr() } catch (e) { window.alert(e.message) }
}

loadWatch()
</script>

<style scoped>
.view { max-width: 1200px; }
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button:hover { color: var(--text-1); }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .sub { font-size: 11.5px; color: var(--text-3); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 10px; }
tbody td { padding: 9px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
.num { font-variant-numeric: tabular-nums; text-align: right; }
.mono { font-family: monospace; font-size: 11.5px; }
.muted { color: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 700; }
.tag.h { background: var(--danger-tint); color: var(--danger); }
.tag.m { background: var(--warning-tint); color: #a87d22; }
.tag.l { background: var(--safe-weak); color: var(--safe); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.doing { color: var(--info, #47c); } .st.doing .d { background: var(--info, #47c); }
.st.wait .d { background: var(--text-3); }
.st.off { color: var(--text-3); }
.mini { border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; padding: 3px 9px; font-size: 11px; cursor: pointer; margin-left: 6px; }
.mini.danger { color: var(--danger); border-color: var(--danger-tint); }
.mini:hover { border-color: var(--accent); }
/* 筛查 */
.screen-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.screen-bar input { flex: 1; min-width: 180px; padding: 8px 12px; border: 1px solid var(--surface-border); border-radius: 8px; font-size: 13px; background: var(--bg); color: var(--text-1); }
.scr-clean { padding: 14px 16px; border-radius: 10px; background: var(--safe-weak); color: var(--safe); font-size: 13px; font-weight: 600; }
.scr-hit { border: 1px solid var(--danger-tint); border-radius: 10px; overflow: hidden; }
.scr-hit-h { padding: 12px 16px; background: var(--danger-tint); color: var(--danger); font-size: 13px; font-weight: 700; }
.scr-hit table { padding: 0 8px; }
/* 引用卡 */
.ref-row { display: flex; align-items: center; gap: 14px; padding: 14px; border: 1px solid var(--surface-border); border-radius: 10px; margin-bottom: 12px; }
.ref-ic { font-size: 26px; }
.ref-tx { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.ref-tx b { font-size: 13.5px; }
.ref-tx span { font-size: 12px; color: var(--text-3); line-height: 1.6; }
.ref-note { font-size: 11.5px; color: var(--text-3); line-height: 1.7; margin: 4px 0 0; }
/* 弹窗 */
.fld { display: block; font-size: 12px; color: var(--text-2); font-weight: 600; margin-bottom: 12px; }
.fld input, .fld select { display: block; width: 100%; margin-top: 5px; padding: 7px 10px; border: 1px solid var(--surface-border); border-radius: 8px; font-size: 13px; background: var(--bg); color: var(--text-1); box-sizing: border-box; font-weight: 400; }
.cerr { color: var(--danger); font-size: 12px; margin: 4px 0 8px; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 460px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; max-height: 90vh; overflow-y: auto; }
.modal-card h3 { margin: 0 0 14px; font-size: 16px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
</style>
