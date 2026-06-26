<!--
  风险评估·表单填写组件（表单引擎 P1）
  ------------------------------------------------------------
  职责：按评估绑定的表单 schema（来自上传 .docx 解析）动态渲染分章节的可填写表单，
        并把填写值（标量 + 明细表行）保存到后端。
  数据：GET  /api/assessments/{id}/form     → { hasForm, reason, formVersionId, schema, answers }
        PUT  /api/assessments/{id}/answers  ← 填写值对象
  说明：未绑定模板/模板未启用表单时友好提示；写权限由 canWrite('risk') 门控。
  设计依据：D1-6 表单引擎（结构配置层运行期渲染）。
-->
<template>
  <div class="aff card">
    <div class="ch">
      <h3>评估表单</h3>
      <span class="sub" v-if="view.hasForm">按模板自定义格式填写（来源：上传的 .docx 报告模板）</span>
      <div class="sp"></div>
      <button
        v-if="view.hasForm"
        class="btn"
        :disabled="!writable || saving"
        @click="save"
        :title="writable ? '' : '无风险评估写权限'"
      >{{ saving ? '保存中…' : '保存填写' }}</button>
    </div>

    <div class="cb">
      <div v-if="loading" class="muted">加载表单…</div>
      <div v-else-if="error" class="err">{{ error }}</div>

      <!-- 未绑定表单：友好提示 -->
      <div v-else-if="!view.hasForm" class="empty">
        <p class="muted">{{ view.reason || '该评估未配置表单' }}</p>
        <p class="hint">在「模板库」给评估所用模板上传 .docx 报告模板并启用后，关联该模板新建评估即可在此填写规范评估报告。</p>
      </div>

      <!-- 表单渲染：分章节 -->
      <div v-else class="form">
        <div v-if="okMsg" class="ok">{{ okMsg }}</div>
        <section v-for="(sec, si) in view.schema.sections" :key="si" class="sec">
          <h4 class="sec-t">{{ sec.title }}</h4>

          <!-- 标量字段 -->
          <div class="fields">
            <div v-for="f in sec.fields" :key="f.key" class="field">
              <label>{{ f.label }}</label>
              <component :is="'span'">
                <textarea v-if="f.type === 'textarea'" v-model="model[f.key]" :disabled="!writable" rows="3"></textarea>
                <select v-else-if="f.type === 'select'" v-model="model[f.key]" :disabled="!writable">
                  <option value="">— 请选择 —</option>
                  <option v-for="o in f.options" :key="o" :value="o">{{ o }}</option>
                </select>
                <select v-else-if="f.type === 'level'" v-model="model[f.key]" :disabled="!writable">
                  <option value="">— 请选择 —</option>
                  <option v-for="lv in LEVELS" :key="lv.v" :value="lv.v">{{ lv.l }}</option>
                </select>
                <select v-else-if="f.type === 'score'" v-model="model[f.key]" :disabled="!writable">
                  <option value="">—</option>
                  <option v-for="n in [0,1,2,3,4,5]" :key="n" :value="n">{{ n }}</option>
                </select>
                <input v-else-if="f.type === 'date'" type="date" v-model="model[f.key]" :disabled="!writable" />
                <input v-else-if="f.type === 'number'" type="number" v-model="model[f.key]" :disabled="!writable" />
                <input v-else type="text" v-model="model[f.key]" :disabled="!writable" />
              </component>
            </div>
          </div>

          <!-- 明细表（可增删行） -->
          <div v-for="list in sec.lists" :key="list.key" class="listblock">
            <div class="list-h">
              <b>{{ list.label }}</b>
              <button class="btn ghost sm" :disabled="!writable" @click="addRow(list)">+ 添加行</button>
            </div>
            <table class="list-tb">
              <thead>
                <tr>
                  <th v-for="c in list.columns" :key="c.key">{{ c.label }}</th>
                  <th style="width:48px"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in model[list.key]" :key="ri">
                  <td v-for="c in list.columns" :key="c.key">
                    <textarea v-if="c.type === 'textarea'" v-model="row[c.key]" :disabled="!writable" rows="2"></textarea>
                    <select v-else-if="c.type === 'level'" v-model="row[c.key]" :disabled="!writable">
                      <option value="">—</option>
                      <option v-for="lv in LEVELS" :key="lv.v" :value="lv.v">{{ lv.l }}</option>
                    </select>
                    <select v-else-if="c.type === 'select'" v-model="row[c.key]" :disabled="!writable">
                      <option value="">—</option>
                      <option v-for="o in c.options" :key="o" :value="o">{{ o }}</option>
                    </select>
                    <select v-else-if="c.type === 'score'" v-model="row[c.key]" :disabled="!writable">
                      <option value="">—</option>
                      <option v-for="n in [0,1,2,3,4,5]" :key="n" :value="n">{{ n }}</option>
                    </select>
                    <input v-else-if="c.type === 'date'" type="date" v-model="row[c.key]" :disabled="!writable" />
                    <input v-else type="text" v-model="row[c.key]" :disabled="!writable" />
                  </td>
                  <td>
                    <button class="btn ghost sm danger" :disabled="!writable" @click="removeRow(list, ri)">删</button>
                  </td>
                </tr>
                <tr v-if="!model[list.key] || !model[list.key].length">
                  <td :colspan="list.columns.length + 1" class="muted" style="text-align:center;padding:12px">暂无明细，点「添加行」</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { api } from '../api/client'
import { canWrite } from '../auth'

const props = defineProps({ assessmentId: { type: [Number, String], default: null } })
const emit = defineEmits(['saved'])

const LEVELS = [
  { v: 'VERY_LOW', l: '极低' }, { v: 'LOW', l: '低' }, { v: 'MID', l: '中' },
  { v: 'HIGH', l: '高' }, { v: 'VERY_HIGH', l: '极高' }
]
const lvlLabel = (v) => (LEVELS.find(x => x.v === v)?.l) || v

const loading = ref(false)
const error = ref('')
const okMsg = ref('')
const saving = ref(false)
const view = ref({ hasForm: false })
// 可填写值模型：标量 model[key]=值；明细表 model[listKey]=[{col:值}]
const model = reactive({})

const writable = canWrite('risk')

// 依据 schema 初始化填写模型（合并已存答案）
function buildModel(schema, answers) {
  Object.keys(model).forEach(k => delete model[k])
  const a = answers || {}
  for (const sec of (schema.sections || [])) {
    for (const f of (sec.fields || [])) {
      model[f.key] = a[f.key] !== undefined && a[f.key] !== null ? a[f.key] : ''
    }
    for (const list of (sec.lists || [])) {
      const rows = Array.isArray(a[list.key]) ? a[list.key] : []
      // 规整每行包含全部列键
      model[list.key] = rows.map(r => {
        const o = {}
        for (const c of list.columns) o[c.key] = r && r[c.key] !== undefined ? r[c.key] : ''
        return o
      })
    }
  }
}

function emptyRow(list) {
  const o = {}
  for (const c of list.columns) o[c.key] = ''
  return o
}
function addRow(list) {
  if (!Array.isArray(model[list.key])) model[list.key] = []
  model[list.key].push(emptyRow(list))
}
function removeRow(list, i) {
  model[list.key].splice(i, 1)
}

async function load() {
  if (!props.assessmentId) return
  loading.value = true
  error.value = ''
  okMsg.value = ''
  try {
    const v = await api.get('/assessments/' + props.assessmentId + '/form')
    view.value = v
    if (v.hasForm) buildModel(v.schema, v.answers)
  } catch (e) {
    error.value = e.message
    view.value = { hasForm: false, reason: e.message }
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  okMsg.value = ''
  error.value = ''
  try {
    // 直接提交模型（标量 + 明细表数组），后端按 key 存 JSON
    const res = await api.put('/assessments/' + props.assessmentId + '/answers', JSON.parse(JSON.stringify(model)))
    okMsg.value = res && res.riskLevel ? '已保存 · 整体残余等级：' + lvlLabel(res.riskLevel) : '已保存'
    emit('saved', res && res.riskLevel)   // 通知父组件刷新签批面板的整体等级
    setTimeout(() => (okMsg.value = ''), 3000)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

watch(() => props.assessmentId, load, { immediate: true })
</script>

<style scoped>
.aff { margin-bottom: 16px; }
.aff .ch { display: flex; align-items: center; gap: 8px; }
.aff .ch .sp { flex: 1; }
.aff .sub { color: var(--text-3); font-size: 12px; }
.muted { color: var(--text-3); }
.err { color: var(--danger); }
.ok { color: var(--success); margin-bottom: 8px; font-weight: 600; }
.empty .hint { color: var(--text-3); font-size: 12px; margin-top: 4px; }
.sec { margin-bottom: 18px; }
.sec-t { margin: 0 0 10px; padding-left: 8px; border-left: 3px solid var(--accent-strong); font-size: 14px; }
.fields { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 12px; color: var(--text-2); }
.field input, .field select, .field textarea,
.list-tb input, .list-tb select, .list-tb textarea {
  width: 100%; padding: 6px 8px; border: 1px solid var(--border);
  border-radius: 6px; background: var(--surface); color: var(--text-1); font: inherit; box-sizing: border-box;
}
.listblock { margin-top: 14px; }
.list-h { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.list-tb { width: 100%; border-collapse: collapse; }
.list-tb th, .list-tb td { border: 1px solid var(--border); padding: 4px 6px; vertical-align: top; }
.list-tb th { background: var(--surface-2); font-size: 12px; text-align: left; }
.btn.sm { padding: 2px 8px; font-size: 12px; }
.btn.danger { color: var(--danger); }
</style>
