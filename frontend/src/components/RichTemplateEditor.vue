<!-- =============================================================
     通知模板富文本编辑器 + 实时预览
     - 工具条：加粗/颜色/字号/对齐 + 变量插入
     - contenteditable 产出 HTML（含 {变量} 占位，后端 replace 渲染）
     - 预览：用样例值代入变量后 v-html 渲染
     安全：预览为作者自己的模板；对外渲染（站内）须在展示端清理，邮件通道用 HTML。
     ============================================================= -->
<template>
  <div class="rte">
    <div class="rte-tools">
      <button type="button" title="加粗" @mousedown.prevent="cmd('bold')"><b>B</b></button>
      <label class="rte-color" title="字体颜色">A<input type="color" @input="cmd('foreColor', $event.target.value)" /></label>
      <select title="字号" @change="cmd('fontSize', $event.target.value); $event.target.selectedIndex = 0">
        <option value="">字号</option><option value="2">小</option><option value="3">正常</option><option value="5">大</option><option value="7">特大</option>
      </select>
      <button type="button" title="左对齐" @mousedown.prevent="cmd('justifyLeft')">⇤</button>
      <button type="button" title="居中" @mousedown.prevent="cmd('justifyCenter')">☰</button>
      <button type="button" title="右对齐" @mousedown.prevent="cmd('justifyRight')">⇥</button>
      <select title="插入变量" @change="insertVar($event.target.value); $event.target.selectedIndex = 0">
        <option value="">插入变量</option>
        <option v-for="v in varList" :key="v" :value="v">{{ v }}</option>
      </select>
      <button type="button" class="rte-pv" :class="{ on: showPreview }" @click="showPreview = !showPreview">{{ showPreview ? '编辑' : '预览' }}</button>
    </div>
    <div v-show="!showPreview" ref="editor" class="rte-edit" contenteditable="true" @input="onInput" @blur="onInput"></div>
    <div v-show="showPreview" class="rte-preview">
      <div class="rte-pv-tag">预览（样例数据代入）</div>
      <div v-html="previewHtml"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  vars: { type: String, default: '' }   // 可用变量串，如 "{标题} {剩余天数} {开始日}"
})
const emit = defineEmits(['update:modelValue'])

const editor = ref(null)
const showPreview = ref(false)

// 可插入变量列表（从 vars 串抽 {xxx} token）
const varList = computed(() => (props.vars.match(/\{[^}]+\}/g) || []))

// 样例值：变量名去掉花括号，个别常见变量给拟真样例
const SAMPLE = { '{标题}': '数据安全管理办法评估', '{剩余天数}': '3', '{逾期天数}': '5', '{开始日}': '2026-07-15',
  '{责任人}': '张三', '{类型}': '内部审计', '{滞留天数}': '7', '{发布机构}': '中国人民银行',
  '{指标}': '高危漏洞数', '{数值}': '12', '{单位}': '个', '{级别}': '严重' }
function sampleOf(token) { return SAMPLE[token] || token.replace(/[{}]/g, '') }

const previewHtml = computed(() => {
  let h = props.modelValue || '<span style="color:#999">（空模板）</span>'
  for (const token of (h.match(/\{[^}]+\}/g) || [])) {
    h = h.split(token).join('<mark>' + sampleOf(token) + '</mark>')
  }
  return h
})

function cmd(command, value) {
  editor.value && editor.value.focus()
  document.execCommand(command, false, value)
  onInput()
}
function insertVar(token) {
  if (!token) return
  editor.value && editor.value.focus()
  document.execCommand('insertText', false, token)
  onInput()
}
function onInput() {
  if (editor.value) emit('update:modelValue', editor.value.innerHTML)
}

// 外部值变化（如切换数据源填入默认模板）时同步进编辑器（编辑器未聚焦时才覆盖，避免打断输入）
watch(() => props.modelValue, (v) => {
  if (editor.value && document.activeElement !== editor.value && editor.value.innerHTML !== (v || '')) {
    editor.value.innerHTML = v || ''
  }
})
onMounted(() => { if (editor.value) editor.value.innerHTML = props.modelValue || '' })
</script>

<style scoped>
.rte { border: 1px solid var(--surface-border); border-radius: var(--radius-md, 10px); overflow: hidden; }
.rte-tools { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; padding: 6px 8px; background: var(--surface-2, rgba(0,0,0,0.02)); border-bottom: 1px solid var(--surface-border); }
.rte-tools button, .rte-tools select { border: 1px solid transparent; background: none; color: var(--text-2); font-size: 12px; padding: 3px 7px; border-radius: 6px; cursor: pointer; font-family: inherit; }
.rte-tools button:hover, .rte-tools select:hover { background: var(--bg); border-color: var(--surface-border); }
.rte-color { display: inline-flex; align-items: center; gap: 2px; font-size: 12px; color: var(--text-2); cursor: pointer; padding: 3px 5px; }
.rte-color input { width: 18px; height: 18px; border: 0; padding: 0; background: none; cursor: pointer; }
.rte-pv.on { background: var(--accent-tint); color: var(--accent-strong); }
.rte-edit { min-height: 84px; padding: 10px 12px; font-size: 13px; line-height: 1.7; color: var(--text-1); outline: none; background: var(--bg); }
.rte-edit:empty::before { content: '在此编辑通知内容，用「插入变量」引用 {变量}…'; color: var(--text-3); }
.rte-preview { min-height: 84px; padding: 10px 12px; font-size: 13px; line-height: 1.7; background: var(--bg); }
.rte-pv-tag { font-size: 10.5px; color: var(--text-3); margin-bottom: 6px; }
.rte-preview :deep(mark) { background: var(--accent-weak, #fde8e6); color: var(--accent-strong); padding: 0 3px; border-radius: 3px; }
</style>
