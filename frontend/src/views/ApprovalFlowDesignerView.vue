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

        <!-- 中：画布 -->
        <div class="canvas">
          <VueFlow v-model:nodes="nodes" v-model:edges="edges" :default-viewport="{ zoom: 0.9 }" fit-view-on-init
                   @connect="onConnect" @node-click="onNodeClick" @edge-click="onEdgeClick">
            <Background pattern-color="#d9c2c2" :gap="18" />
            <Controls />
          </VueFlow>
        </div>

        <!-- 右：属性面板 -->
        <div class="props">
          <div class="ph">{{ $t('flow.props') }}</div>
          <div v-if="!sel" class="phint">{{ $t('flow.selectHint') }}</div>

          <template v-else-if="sel.kind === 'node'">
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
function onNodeClick(e) { sel.value = { kind: 'node', id: e.node.id } }
function onEdgeClick(e) { sel.value = { kind: 'edge', id: e.edge.id } }
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
.editor { display: grid; grid-template-columns: 210px 1fr 240px; gap: 12px; height: 600px; }
.palette, .props { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 12px; box-shadow: var(--shadow-1); overflow-y: auto; }
.canvas { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-1); }
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
