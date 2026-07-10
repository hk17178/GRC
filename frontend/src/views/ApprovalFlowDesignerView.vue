<template>
  <!--
    审批流配置（ApprovalFlowDesignerView · 增强② P1.4）：Vue Flow 可视化画布。
    功能真源 = 后端 /api/approval-flows（草稿/校验/发布）。每组织各配各的（后端 RLS 隔离）。
    画布只承载"结构"，加载时按层级自动布局（坐标不回传后端，不改已测后端契约）。
  -->
  <AppShell>
    <section class="view view-flow">
      <div class="phead">
        <div><div class="kqt">{{ $t('flow.tag') }}</div><h1>{{ $t('flow.title') }}</h1></div>
        <div class="sp"></div>
        <select v-model="bizType" class="sel" @change="loadList">
          <option v-for="b in BIZ" :key="b" :value="b">{{ $t('flow.biz.' + b) }}</option>
        </select>
        <select v-model.number="selId" class="sel" @change="openFlow(selId)">
          <option :value="0">{{ $t('flow.pickFlow') }}</option>
          <option v-for="f in flows" :key="f.id" :value="f.id">{{ f.name }}（{{ $t('flow.status.' + f.status) }}）</option>
        </select>
        <button class="btn ghost sm" @click="newFlow">{{ $t('flow.new') }}</button>
      </div>

      <div class="editor">
        <!-- 左：节点面板 -->
        <div class="palette">
          <div class="ph">{{ $t('flow.palette') }}</div>
          <button class="pnode appr" @click="addNode('APPROVAL')">＋ {{ $t('flow.node.APPROVAL') }}</button>
          <button class="pnode cond" @click="addNode('CONDITION')">＋ {{ $t('flow.node.CONDITION') }}</button>
          <button class="pnode par" @click="addNode('PARALLEL_SPLIT')">＋ {{ $t('flow.node.PARALLEL_SPLIT') }}</button>
          <button class="pnode par" @click="addNode('PARALLEL_JOIN')">＋ {{ $t('flow.node.PARALLEL_JOIN') }}</button>
          <button class="pnode end" @click="addNode('END')">＋ {{ $t('flow.node.END') }}</button>
          <div class="phint">{{ $t('flow.connectHint') }}</div>
          <div class="bar2">
            <input v-model="flowName" class="nameinput" :placeholder="$t('flow.namePh')" />
            <button class="btn sm" :disabled="busy" @click="save">{{ $t('flow.save') }}</button>
            <button class="btn ghost sm" :disabled="!selId || busy" @click="validate">{{ $t('flow.validate') }}</button>
            <button class="btn sm pub" :disabled="!selId || busy" @click="publish">{{ $t('flow.publish') }}</button>
          </div>
          <p v-if="msg" class="msg" :class="msgKind">{{ msg }}</p>
        </div>

        <!-- 中：画布（属性面板紧贴所点节点旁展开，就地编辑） -->
        <div class="canvas" ref="canvasEl">
          <VueFlow v-model:nodes="nodes" v-model:edges="edges" :default-viewport="{ zoom: 0.9 }" fit-view-on-init
                   @connect="onConnect" @node-click="onNodeClick" @edge-click="onEdgeClick">
            <Background pattern-color="#d9c2c2" :gap="18" />
            <Controls />
          </VueFlow>

          <!-- 未选中：画布内轻提示 -->
          <div v-if="!sel" class="canvas-hint">{{ $t('flow.selectHint') }}</div>

          <!-- 属性浮层：紧贴所点节点/连线旁就地编辑 -->
          <div v-else class="props-float" :style="panelPos">
            <div class="pf-head"><span class="pf-ttl">{{ $t('flow.props') }}</span><button class="pf-x" @click="clearSel" title="关闭">×</button></div>

          <template v-if="sel.kind === 'node'">
            <div class="fkind">{{ $t('flow.node.' + selNode.data.kind) }}</div>
            <template v-if="selNode.data.kind === 'APPROVAL'">
              <label class="fl">{{ $t('flow.f.name') }}<input v-model="selNode.data.name" @input="touch" /></label>
              <label class="fl">{{ $t('flow.f.approverType') }}
                <select v-model="selNode.data.approverType" @change="touch"><option value="ROLE">{{ $t('flow.at.ROLE') }}</option><option value="USER">{{ $t('flow.at.USER') }}</option><option value="GROUP">{{ $t('flow.at.GROUP') }}</option></select>
              </label>
              <label class="fl">{{ $t('flow.f.approvers') }}<input v-model="selNode.data.approverRefs" :placeholder="$t('flow.f.approversPh')" @input="touch" /></label>
              <label class="fl">{{ $t('flow.f.mode') }}
                <select v-model="selNode.data.mode" @change="touch"><option value="ANY">{{ $t('flow.mode.ANY') }}</option><option value="ALL">{{ $t('flow.mode.ALL') }}</option></select>
              </label>
              <label v-if="selNode.data.mode === 'ANY'" class="fl">{{ $t('flow.f.required') }}<input v-model.number="selNode.data.requiredCount" type="number" min="1" @input="touch" /></label>
              <label class="fl">{{ $t('flow.f.timeout') }}<input v-model.number="selNode.data.timeoutHours" type="number" min="0" :placeholder="$t('flow.f.timeoutPh')" @input="touch" /></label>
              <template v-if="selNode.data.timeoutHours > 0">
                <label class="fl">{{ $t('flow.f.escType') }}
                  <select v-model="selNode.data.escalateType" @change="touch"><option value="ROLE">{{ $t('flow.at.ROLE') }}</option><option value="USER">{{ $t('flow.at.USER') }}</option></select>
                </label>
                <label class="fl">{{ $t('flow.f.escRef') }}<input v-model="selNode.data.escalateRef" @input="touch" /></label>
              </template>
            </template>
            <template v-else-if="selNode.data.kind === 'END'">
              <label class="fl">{{ $t('flow.f.outcome') }}
                <select v-model="selNode.data.outcome" @change="touch"><option value="APPROVED">{{ $t('flow.oc.APPROVED') }}</option><option value="REJECTED">{{ $t('flow.oc.REJECTED') }}</option></select>
              </label>
            </template>
            <template v-else>
              <label class="fl">{{ $t('flow.f.name') }}<input v-model="selNode.data.name" @input="touch" /></label>
              <p class="phint" style="padding:6px 0">{{ $t('flow.node.' + selNode.data.kind) }}</p>
            </template>
            <button class="btn ghost sm danger" style="margin-top:10px" @click="delNode">{{ $t('flow.delNode') }}</button>
          </template>

          <template v-else-if="sel.kind === 'edge'">
            <div class="fkind">{{ $t('flow.edge') }}</div>
            <label class="fl">{{ $t('flow.f.condition') }}<input v-model="selEdge.data.condition" :placeholder="$t('flow.f.conditionPh')" @input="touchEdge" /></label>
            <p class="phint" style="padding:6px 0">{{ $t('flow.condHint') }}</p>
            <button class="btn ghost sm danger" style="margin-top:10px" @click="delEdge">{{ $t('flow.delEdge') }}</button>
          </template>
          </div>
        </div>
      </div>

      <!-- D1-8 H-06：流程绑定（条件分流 + 版本快照固化） -->
      <div class="pb-card">
        <div class="pb-head">
          <h3>按条件分流到不同审批流程</h3>
          <span class="pb-sub">给「{{ $t('flow.biz.' + bizType) }}」按单据情况分流：满足某条件的单据走指定审批流程，都不满足则走兜底流程。发起时会锁定当时的流程版本，之后改规则不影响在途单据；不同公司主体的规则互不干扰。</span>
        </div>
        <table class="pb-tbl">
          <thead><tr><th>优先级</th><th>规则名称</th><th>触发条件</th><th>走哪个审批流程</th><th>版本</th><th>状态</th><th></th></tr></thead>
          <tbody>
            <tr v-for="b in bindings" :key="b.id">
              <td style="text-align:center">{{ b.seq }}</td>
              <td>{{ b.name }}</td>
              <td><code style="font-size:11px">{{ condText(b.condition) }}</code></td>
              <td><code>{{ b.processDefKey }}</code></td>
              <td style="text-align:center">v{{ b.processVersion }}</td>
              <td style="text-align:center"><span :class="b.status==='ACTIVE'?'pb-pill on':'pb-pill'">{{ b.status==='ACTIVE'?'启用':'停用' }}</span></td>
              <td style="text-align:right"><button v-if="b.status==='ACTIVE'" class="btn ghost sm danger" @click="retireBinding(b)">停用</button></td>
            </tr>
            <tr v-if="!bindings.length"><td colspan="7" style="text-align:center;color:var(--text-3);padding:10px">暂无绑定，下方新建。空条件=兜底默认。</td></tr>
          </tbody>
        </table>
        <div class="pb-add">
          <div class="pb-row">
            <label class="pb-f">这条规则叫什么<input v-model="nb.name" placeholder="如 大额支付走三级审批" /></label>
          </div>
          <div class="pb-row">
            <span class="pb-lbl">当单据满足</span>
            <input v-model="nb.field" placeholder="看单据的哪一项（留空=所有单据）" style="width:190px" />
            <select v-model="nb.op" style="width:110px"><option v-for="o in OP_OPTIONS" :key="o.v" :value="o.v">{{ o.t }}</option></select>
            <input v-model="nb.value" placeholder="等于/满足什么" style="width:130px" />
          </div>
          <div class="pb-row">
            <span class="pb-lbl">就走这个审批流程</span>
            <select v-if="publishedFlows.length" @change="nb.processDefKey = $event.target.value" style="width:190px">
              <option value="">— 从已发布流程选择 —</option>
              <option v-for="f in publishedFlows" :key="f.id" :value="f.bpmnKey">{{ f.name }}（v{{ f.version }}）</option>
            </select>
            <input v-model="nb.processDefKey" placeholder="流程标识（可从左侧下拉选）" style="width:170px" />
            <label class="pb-mini">版本<input v-model.number="nb.processVersion" type="number" min="1" style="width:56px" /></label>
            <label class="pb-mini">优先级<input v-model.number="nb.seq" type="number" min="0" style="width:64px" /></label>
            <button class="btn sm" :disabled="!nb.name || !nb.processDefKey" @click="addBinding">＋ 新增规则</button>
          </div>
          <p class="pb-hint">「看单据的哪一项」= 单据发起时携带的属性（如金额、密级、风险等级）；留空表示这条规则对所有单据生效（兜底）。「优先级」数字越小越先匹配。</p>
        </div>
        <div class="pb-resolve">
          <span class="pb-lbl">模拟试算</span>
          <input v-model="rb.field" placeholder="单据的哪一项" style="width:130px" />
          <input v-model="rb.value" placeholder="它的值" style="width:110px" />
          <button class="btn ghost sm" @click="tryResolve">看会走哪个流程</button>
          <span v-if="resolveResult" class="pb-res">{{ resolveResult }}</span>
        </div>
        <p v-if="pbMsg" class="msg" :class="pbMsgKind">{{ pbMsg }}</p>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, computed } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const BIZ = ['POLICY_PUBLISH', 'RISK_ACCEPT', 'SOD_EXCEPTION', 'REG_FILING']
const bizType = ref('POLICY_PUBLISH')
const flows = ref([])
const selId = ref(0)
const flowName = ref('')
const busy = ref(false)
const msg = ref('')
const msgKind = ref('ok')

const nodes = ref([])
const edges = ref([])
const sel = ref(null)
const { addEdges, removeNodes, removeEdges } = useVueFlow()

let seq = 1
const newKey = (p) => p + (seq++)

function setMsg(text, kind) { msg.value = text; msgKind.value = kind || 'ok' }

async function loadList() {
  selId.value = 0
  try { flows.value = await api.get('/approval-flows?bizType=' + bizType.value) } catch (e) { flows.value = [] }
  loadBindings()
}

// ===== D1-8 H-06：流程绑定（条件分流 + 版本快照固化）=====
const bindings = ref([])
const nb = ref({ name: '', field: '', op: 'eq', value: '', processDefKey: '', processVersion: 1, seq: 0 })
const rb = ref({ field: '', value: '' })
const resolveResult = ref('')
const pbMsg = ref('')
const pbMsgKind = ref('ok')
function setPbMsg(t, k) { pbMsg.value = t; pbMsgKind.value = k || 'ok' }
// 运算符人话化（下拉与表格展示统一走这套；提交仍用 eq/ne/… 原码，不改后端契约）
const OP_OPTIONS = [
  { v: 'eq', t: '等于' }, { v: 'ne', t: '不等于' }, { v: 'in', t: '是其中之一' },
  { v: 'gt', t: '大于' }, { v: 'gte', t: '大于等于' }, { v: 'lt', t: '小于' }, { v: 'lte', t: '小于等于' }
]
const OP_LABEL = Object.fromEntries(OP_OPTIONS.map((o) => [o.v, o.t]))
// 已发布（ACTIVE 且有部署 key）的本业务类型流程——供「走哪个审批流程」下拉直选
const publishedFlows = computed(() => (flows.value || []).filter((f) => f.status === 'ACTIVE' && f.bpmnKey))
function condText(cond) {
  try {
    const preds = (JSON.parse(cond || '{}').predicates) || []
    if (!preds.length) return '所有单据（兜底）'
    return preds.map((p) => `${p.field} ${OP_LABEL[p.op] || p.op} ${Array.isArray(p.value) ? p.value.join(' / ') : p.value}`).join(' 且 ')
  } catch (e) { return cond }
}
async function loadBindings() {
  try { bindings.value = await api.get('/process-bindings?objectType=' + bizType.value) } catch (e) { bindings.value = [] }
}
function buildCondition() {
  if (!nb.value.field) return '{}'   // 兜底
  const raw = nb.value.value
  const num = raw !== '' && !isNaN(Number(raw)) ? Number(raw) : raw
  const value = nb.value.op === 'in' ? String(raw).split(',').map((s) => s.trim()) : num
  return JSON.stringify({ predicates: [{ field: nb.value.field, op: nb.value.op, value }] })
}
async function addBinding() {
  setPbMsg('')
  try {
    await api.post('/process-bindings', {
      orgId: 12, objectType: bizType.value, name: nb.value.name, condition: buildCondition(),
      processDefKey: nb.value.processDefKey, processVersion: nb.value.processVersion || 1, seq: nb.value.seq || 0
    })
    nb.value = { name: '', field: '', op: 'eq', value: '', processDefKey: '', processVersion: 1, seq: 0 }
    await loadBindings(); setPbMsg('✓ 已新增绑定', 'ok')
  } catch (e) { setPbMsg(e.message, 'err') }
}
async function retireBinding(b) {
  try { await api.post('/process-bindings/' + b.id + '/retire', {}); await loadBindings() }
  catch (e) { setPbMsg(e.message, 'err') }
}
async function tryResolve() {
  resolveResult.value = ''
  const ctx = {}
  if (rb.value.field) {
    const raw = rb.value.value
    ctx[rb.value.field] = raw !== '' && !isNaN(Number(raw)) ? Number(raw) : raw
  }
  try {
    const r = await api.post('/process-bindings/resolve', { objectType: bizType.value, context: ctx })
    resolveResult.value = r ? `命中 → ${r.processDefKey} v${r.processVersion}（绑定#${r.bindingId}）` : '无匹配（回落业务默认流程）'
  } catch (e) { resolveResult.value = '解析失败：' + e.message }
}

// ---- 画布 ↔ FlowGraph ----
function nodeLabel(d) {
  if (d.kind === 'APPROVAL') return (d.name || '审批') + (d.mode === 'ALL' ? ' · 会签' : ' · 或签')
  if (d.kind === 'END') return d.outcome === 'REJECTED' ? '驳回结束' : '通过结束'
  return { START: '开始', CONDITION: '条件', PARALLEL_SPLIT: '并行分叉', PARALLEL_JOIN: '并行合流' }[d.kind] || d.kind
}
function vfNode(key, data, x, y) {
  return {
    id: key, position: { x, y },
    data,
    label: nodeLabel(data),
    class: 'vfn vfn-' + data.kind,
    sourcePosition: 'right', targetPosition: 'left'
  }
}
// 按 BFS 层级自动布局（结构清晰，坐标不入库）
function autoLayout(graphNodes, graphEdges) {
  const adj = {}; graphEdges.forEach(e => (adj[e.from] = adj[e.from] || []).push(e.to))
  const startKey = (graphNodes.find(n => n.type === 'START') || {}).key
  const level = {}; const q = []
  if (startKey) { level[startKey] = 0; q.push(startKey) }
  for (let i = 0; i < q.length; i++) {
    for (const nx of (adj[q[i]] || [])) if (level[nx] == null) { level[nx] = level[q[i]] + 1; q.push(nx) }
  }
  const perLevel = {}
  return graphNodes.map(n => {
    const lv = level[n.key] == null ? 0 : level[n.key]
    perLevel[lv] = (perLevel[lv] || 0)
    const y = 40 + perLevel[lv] * 110; perLevel[lv]++
    return vfNode(n.key, toData(n), 60 + lv * 200, y)
  })
}
function toData(n) {
  return {
    kind: n.type, name: n.name,
    approverType: n.approverType || 'ROLE',
    approverRefs: (n.approverRefs || []).join(','),
    mode: n.mode || 'ANY', requiredCount: n.requiredCount || 1,
    timeoutHours: n.timeoutHours || 0,
    escalateType: n.escalateTo?.type || 'ROLE', escalateRef: n.escalateTo?.ref || '',
    outcome: n.outcome || 'APPROVED'
  }
}
function buildGraph() {
  const gn = nodes.value.map(v => {
    const d = v.data
    const node = { key: v.id, type: d.kind, name: d.name || null }
    if (d.kind === 'APPROVAL') {
      node.approverType = d.approverType
      node.approverRefs = (d.approverRefs || '').split(',').map(s => s.trim()).filter(Boolean)
      node.mode = d.mode
      node.requiredCount = d.requiredCount || 1
      node.timeoutHours = d.timeoutHours || null
      if (d.timeoutHours > 0 && d.escalateRef) node.escalateTo = { type: d.escalateType, ref: d.escalateRef }
    }
    if (d.kind === 'END') node.outcome = d.outcome
    return node
  })
  const ge = edges.value.map(e => ({ from: e.source, to: e.target, condition: e.data?.condition || null }))
  return { nodes: gn, edges: ge }
}

// ---- 操作 ----
function newFlow() {
  selId.value = 0; flowName.value = bizType.value + ' 审批流'; sel.value = null; seq = 1
  nodes.value = [
    vfNode('start', toData({ type: 'START' }), 60, 100),
    vfNode('n1', toData({ type: 'APPROVAL', name: '审批', approverRefs: ['CHECKER'] }), 260, 100),
    vfNode('end', toData({ type: 'END', outcome: 'APPROVED' }), 480, 100)
  ]
  edges.value = [edge('start', 'n1'), edge('n1', 'end')]
  setMsg('已新建草稿，编辑后保存', 'ok')
}
async function openFlow(id) {
  if (!id) return
  sel.value = null
  const f = await api.get('/approval-flows/' + id)
  flowName.value = f.name
  const g = JSON.parse(f.graphJson || '{"nodes":[],"edges":[]}')
  nodes.value = autoLayout(g.nodes || [], g.edges || [])
  edges.value = (g.edges || []).map(e => edge(e.from, e.to, e.condition))
  seq = (g.nodes || []).length + 1
  setMsg('已载入：' + f.name + '（' + f.status + '）', 'ok')
}
function edge(from, to, condition) {
  return { id: 'e_' + from + '_' + to, source: from, target: to, data: { condition: condition || '' }, label: condition || '', animated: false }
}
function onConnect(c) {
  addEdges([edge(c.source, c.target)])
}
function addNode(kind) {
  const key = newKey(kind === 'APPROVAL' ? 'a' : kind === 'CONDITION' ? 'c' : kind === 'END' ? 'end' : 'p')
  const data = toData({ type: kind, name: kind === 'APPROVAL' ? '审批' : '', approverRefs: kind === 'APPROVAL' ? ['CHECKER'] : [], outcome: 'APPROVED' })
  nodes.value = [...nodes.value, vfNode(key, data, 200 + Math.random() * 120, 240 + Math.random() * 80)]
}
const canvasEl = ref(null)
const panelPos = ref({ top: '12px', right: '12px' })   // 缺省右上；点击后贴到元素旁
const PANEL_W = 250
// 把属性浮层定位到被点元素（节点/连线）右侧；越界则翻到左侧，纵向夹在画布内
function placePanelBeside(evt) {
  const canvas = canvasEl.value
  const target = evt && (evt.currentTarget || (evt.target && evt.target.closest && evt.target.closest('.vue-flow__node, .vue-flow__edge')))
  if (!canvas || !target || !target.getBoundingClientRect) { panelPos.value = { top: '12px', right: '12px' }; return }
  const cr = canvas.getBoundingClientRect()
  const nr = target.getBoundingClientRect()
  let left = nr.right - cr.left + 12
  if (left + PANEL_W > cr.width - 8) left = Math.max(8, nr.left - cr.left - PANEL_W - 12)   // 右侧放不下→翻左侧
  let top = Math.max(8, Math.min(nr.top - cr.top, cr.height - 260))
  panelPos.value = { left: left + 'px', top: top + 'px' }
}
function onNodeClick(e) { sel.value = { kind: 'node', id: e.node.id }; placePanelBeside(e.event) }
function onEdgeClick(e) { sel.value = { kind: 'edge', id: e.edge.id }; placePanelBeside(e.event) }
function clearSel() { sel.value = null }
const selNode = computed(() => nodes.value.find(n => n.id === sel.value?.id))
const selEdge = computed(() => edges.value.find(e => e.id === sel.value?.id))
function touch() { const n = selNode.value; if (n) n.label = nodeLabel(n.data) }
function touchEdge() { const e = selEdge.value; if (e) e.label = e.data.condition }
function delNode() { removeNodes([sel.value.id]); sel.value = null }
function delEdge() { removeEdges([sel.value.id]); sel.value = null }

async function save() {
  busy.value = true; msg.value = ''
  try {
    const graph = buildGraph()
    if (selId.value) {
      await api.put('/approval-flows/' + selId.value, { name: flowName.value, graph })
      setMsg('已保存草稿', 'ok')
    } else {
      const f = await api.post('/approval-flows', { orgId: 12, bizType: bizType.value, name: flowName.value, graph })
      selId.value = f.id; await loadList(); selId.value = f.id
      setMsg('已创建草稿 #' + f.id, 'ok')
    }
  } catch (e) { setMsg('保存失败：' + e.message, 'err') } finally { busy.value = false }
}
async function validate() {
  busy.value = true
  try { await api.post('/approval-flows/' + selId.value + '/validate', {}); setMsg('✓ 校验通过', 'ok') }
  catch (e) { setMsg('校验未通过：' + e.message, 'err') } finally { busy.value = false }
}
async function publish() {
  busy.value = true
  try { await save(); await api.post('/approval-flows/' + selId.value + '/publish', {}); await loadList(); setMsg('✓ 已发布并生效', 'ok') }
  catch (e) { setMsg('发布失败：' + e.message, 'err') } finally { busy.value = false }
}

loadList()
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 12px; gap: 10px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.sel { height: 34px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--surface); color: var(--text-1); font-size: 12.5px; font-family: inherit; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 7px 13px; font-size: 12.5px; font-weight: 600; cursor: pointer; }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.ghost.danger { color: var(--danger); border-color: var(--danger); }
.btn.sm { padding: 5px 11px; font-size: 11.5px; }
.btn.pub { background: linear-gradient(135deg, var(--success), #2f8f5e); }
.btn[disabled] { opacity: 0.5; cursor: not-allowed; }
.editor { display: grid; grid-template-columns: 210px 1fr; gap: 12px; height: 600px; }
.palette { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 12px; box-shadow: var(--shadow-1); overflow-y: auto; }
.canvas { position: relative; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-1); }
/* 画布内属性浮层（点节点/连线就地展示） */
.props-float { position: absolute; width: 250px; max-height: calc(100% - 24px); overflow-y: auto; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-md, 10px); box-shadow: var(--shadow-2, 0 10px 30px rgba(0,0,0,0.16)); padding: 12px 14px; z-index: 5; }
.pf-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.pf-ttl { font-size: 12px; font-weight: 700; color: var(--text-1); }
.pf-x { border: 0; background: none; color: var(--text-3); font-size: 17px; line-height: 1; cursor: pointer; }
.pf-x:hover { color: var(--danger); }
.canvas-hint { position: absolute; top: 12px; right: 12px; font-size: 11.5px; color: var(--text-3); background: var(--surface-2, rgba(0,0,0,0.03)); border: 1px solid var(--surface-border); border-radius: 8px; padding: 6px 10px; z-index: 5; pointer-events: none; }
.ph { font-size: 11px; font-weight: 700; color: var(--text-2); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 10px; }
.pnode { display: block; width: 100%; text-align: left; margin-bottom: 7px; padding: 8px 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; cursor: pointer; font-weight: 600; }
.pnode.appr { border-left: 3px solid var(--accent); }
.pnode.cond { border-left: 3px solid #a87d22; }
.pnode.par { border-left: 3px solid var(--info); }
.pnode.end { border-left: 3px solid var(--success); }
.phint { font-size: 11px; color: var(--text-3); margin: 8px 0; line-height: 1.5; }
.bar2 { margin-top: 12px; border-top: 1px solid var(--border-subtle); padding-top: 12px; display: flex; flex-wrap: wrap; gap: 6px; }
.nameinput { width: 100%; height: 32px; padding: 0 9px; margin-bottom: 6px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; font-family: inherit; }
.msg { font-size: 11.5px; margin-top: 10px; padding: 7px 9px; border-radius: var(--radius-md); line-height: 1.5; }
.msg.ok { color: var(--success); background: var(--success-tint, rgba(40,150,90,.1)); }
.msg.err { color: var(--danger); background: var(--danger-tint, rgba(180,35,45,.1)); white-space: pre-wrap; }
.fkind { font-size: 12.5px; font-weight: 700; color: var(--accent-strong); margin-bottom: 10px; }
.fl { display: block; font-size: 11.5px; color: var(--text-2); margin-bottom: 9px; }
.fl input, .fl select { display: block; width: 100%; height: 32px; margin-top: 4px; padding: 0 9px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; font-family: inherit; box-sizing: border-box; }
.props :deep(.vf-controls) { }
/* D1-8 H-06：流程绑定面板 */
.pb-card { margin-top: 16px; border: 1px solid var(--surface-border); border-radius: var(--radius-lg, 12px); background: var(--surface); padding: 16px; }
.pb-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.pb-head h3 { font-size: 14px; font-weight: 700; color: var(--text-1); margin: 0; }
.pb-sub { font-size: 11.5px; color: var(--text-3); }
.pb-tbl { width: 100%; font-size: 12px; border-collapse: collapse; margin-bottom: 12px; }
.pb-tbl th { text-align: left; font-size: 11px; color: var(--text-3); font-weight: 600; padding: 4px 8px; border-bottom: 1px solid var(--surface-border); }
.pb-tbl td { padding: 5px 8px; border-bottom: 1px solid var(--border-subtle, rgba(0,0,0,.05)); }
.pb-pill { font-size: 10px; padding: 1px 7px; border-radius: 8px; background: var(--bg); color: var(--text-3); }
.pb-pill.on { background: var(--accent-tint, rgba(40,90,180,.12)); color: var(--accent-strong); }
.pb-add, .pb-resolve { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 10px; background: var(--bg); border-radius: var(--radius-md); margin-bottom: 8px; }
.pb-add input, .pb-add select, .pb-resolve input { height: 30px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-sm); background: var(--surface); color: var(--text-1); font-size: 12px; font-family: inherit; }
.pb-lbl { font-size: 12px; color: var(--text-2); min-width: 92px; font-weight: 600; }
.pb-res { font-size: 12px; font-weight: 600; color: var(--accent-strong); }
/* ④ 条件分流表单：多行分步、人话标签 */
.pb-add { flex-direction: column; align-items: stretch; gap: 10px; }
.pb-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.pb-f { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-2); }
.pb-f input { width: 260px; }
.pb-mini { display: flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-2); }
.pb-hint { width: 100%; font-size: 11px; color: var(--text-3); line-height: 1.55; margin: 2px 0 0; }
</style>

<style>
/* Vue Flow 节点配色（全局，因 vue-flow 节点不在 scoped 作用域内） */
.vue-flow__node.vfn { border-radius: 8px; border: 1px solid var(--surface-border); background: var(--surface); color: var(--text-1); font-size: 11.5px; font-weight: 600; padding: 8px 10px; box-shadow: var(--shadow-1); width: 140px; text-align: center; }
.vue-flow__node.vfn-START { border-left: 4px solid var(--text-3); }
.vue-flow__node.vfn-APPROVAL { border-left: 4px solid var(--accent); }
.vue-flow__node.vfn-CONDITION { border-left: 4px solid #a87d22; }
.vue-flow__node.vfn-PARALLEL_SPLIT, .vue-flow__node.vfn-PARALLEL_JOIN { border-left: 4px solid var(--info); }
.vue-flow__node.vfn-END { border-left: 4px solid var(--success); }
</style>
