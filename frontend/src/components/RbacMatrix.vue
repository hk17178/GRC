<!--
  角色 × 资源 权限矩阵（增强③ R5；⑧ 抽为组件，供「权限管理」页内嵌为一个 Tab）。
  功能真源 = 后端 /api/rbac。集团统一定义角色与矩阵；写操作受后端 @RequiresPermission("rbacconfig") 限制。
-->
<template>
  <div class="rbac-matrix">
    <div class="g">
      <!-- 左：角色列表 -->
      <div class="card">
        <div class="ch">
          <h3>{{ $t('rbac.roles') }}</h3><span class="cnt">{{ roles.length }}</span>
          <button class="btn ghost sm" style="margin-left: auto" @click="openCreate">{{ $t('rbac.newRole') }}</button>
        </div>
        <div class="cb" style="padding-top: 0">
          <div v-for="r in roles" :key="r.id" class="roleitem" :class="{ on: r.id === selId }" @click="selectRole(r)">
            <span class="rn">{{ r.name }}</span>
            <span class="rc">{{ r.code }}</span>
            <span v-if="r.superadmin" class="sup">{{ $t('rbac.super') }}</span>
          </div>
        </div>
      </div>

      <!-- 右：权限矩阵 -->
      <div class="card">
        <div class="ch">
          <h3>{{ $t('rbac.matrix') }}</h3>
          <span v-if="sel" class="sub">{{ sel.name }}（{{ sel.code }}）</span>
          <button v-if="sel && !sel.superadmin" class="btn sm" style="margin-left: auto" :disabled="saving" @click="save">{{ saving ? $t('common.submitting') : $t('rbac.save') }}</button>
        </div>
        <div class="cb" style="padding-top: 0; max-height: 560px; overflow-y: auto">
          <div v-if="!sel" class="hint">{{ $t('rbac.selectHint') }}</div>
          <div v-else-if="sel.superadmin" class="hint">{{ $t('rbac.superNote') }}</div>
          <table v-else>
            <thead><tr><th>{{ $t('rbac.resource') }}</th><th style="width: 220px">{{ $t('rbac.level') }}</th></tr></thead>
            <tbody>
              <template v-for="menu in menus" :key="menu.code">
                <tr class="menurow">
                  <td><b>{{ menu.name }}</b> <span class="code">{{ menu.code }}</span></td>
                  <td><div class="seg">
                    <button v-for="lv in LEVELS" :key="lv" class="lvbtn" :class="[lv.cls, { on: levels[menu.code] === lv.v }]" @click="set(menu.code, lv.v)">{{ $t('rbac.lv.' + lv.v) }}</button>
                  </div></td>
                </tr>
                <tr v-for="act in actionsOf(menu.code)" :key="act.code" class="actrow">
                  <td><span class="acttxt">— {{ act.name }}</span> <span class="code">{{ act.code }}</span></td>
                  <td><div class="seg">
                    <button v-for="lv in LEVELS" :key="lv" class="lvbtn" :class="[lv.cls, { on: levels[act.code] === lv.v }]" @click="set(act.code, lv.v)">{{ $t('rbac.lv.' + lv.v) }}</button>
                  </div></td>
                </tr>
              </template>
            </tbody>
          </table>
          <p v-if="opError" class="cerr">{{ opError }}</p>
        </div>
      </div>
    </div>

    <!-- 新建角色弹窗 -->
    <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
      <div class="modal-card">
        <h3>{{ $t('rbac.newRole') }}</h3>
        <label class="fld">{{ $t('rbac.roleCode') }}<input v-model="cf.code" placeholder="COMPLIANCE_VIEWER" /></label>
        <label class="fld">{{ $t('rbac.roleName') }}<input v-model="cf.name" /></label>
        <p v-if="opError" class="cerr">{{ opError }}</p>
        <div class="modal-actions">
          <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
          <button class="btn" :disabled="!cf.code || !cf.name || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('rbac.create') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api } from '@/api/client.js'

const LEVELS = [
  { v: 'RW', cls: 'rw' },
  { v: 'RO', cls: 'ro' },
  { v: 'HIDDEN', cls: 'hd' }
]
const roles = ref([])
const resources = ref([])
const selId = ref(0)
const levels = reactive({})
const saving = ref(false)
const opError = ref('')

const sel = computed(() => roles.value.find((r) => r.id === selId.value))
const menus = computed(() => resources.value.filter((r) => r.type === 'MENU'))
const actionsOf = (menuCode) => resources.value.filter((r) => r.type === 'ACTION' && r.parentMenu === menuCode)

async function loadRoles() { try { roles.value = await api.get('/rbac/roles') } catch (e) { roles.value = [] } }
async function loadResources() { try { resources.value = await api.get('/rbac/resources') } catch (e) { resources.value = [] } }

async function selectRole(r) {
  selId.value = r.id
  opError.value = ''
  for (const k of Object.keys(levels)) delete levels[k]
  for (const res of resources.value) levels[res.code] = 'HIDDEN'
  if (!r.superadmin) {
    try {
      const m = await api.get('/rbac/roles/' + r.id + '/permissions')
      for (const [code, lv] of Object.entries(m)) levels[code] = lv
    } catch (e) { /* 忽略 */ }
  }
}
function set(code, v) { levels[code] = v }

async function save() {
  saving.value = true; opError.value = ''
  try {
    const payload = {}
    for (const res of resources.value) payload[res.code] = levels[res.code] || 'HIDDEN'
    await api.put('/rbac/roles/' + selId.value + '/permissions', payload)
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

const showCreate = ref(false)
const cf = reactive({ code: '', name: '' })
function openCreate() { cf.code = ''; cf.name = ''; opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    const r = await api.post('/rbac/roles', { code: cf.code, name: cf.name })
    showCreate.value = false
    await loadRoles()
    const nr = roles.value.find((x) => x.id === r.id)
    if (nr) selectRole(nr)
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(() => { loadRoles(); loadResources() })
</script>

<style scoped>
.g { display: grid; grid-template-columns: 260px 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 5px 11px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 8px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .sub { font-size: 11.5px; color: var(--text-3); }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 24px 8px; text-align: center; }
.roleitem { display: flex; align-items: center; gap: 8px; padding: 9px 11px; border-radius: var(--radius-md); cursor: pointer; border: 1px solid transparent; }
.roleitem:hover { background: var(--accent-tint); }
.roleitem.on { background: var(--accent-tint); border-color: var(--accent); }
.roleitem .rn { font-size: 12.5px; font-weight: 600; color: var(--text-1); }
.roleitem .rc { font-size: 10.5px; color: var(--text-3); font-family: var(--font-mono, monospace); }
.roleitem .sup { margin-left: auto; font-size: 10px; font-weight: 700; color: var(--danger); background: var(--danger-tint, rgba(180,35,45,.1)); padding: 1px 6px; border-radius: 999px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 8px; position: sticky; top: 0; background: var(--surface); }
tbody td { padding: 7px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; vertical-align: middle; }
.menurow td { background: var(--bg); }
.menurow b { font-size: 12.5px; }
.actrow .acttxt { color: var(--text-2); }
.code { font-size: 10px; color: var(--text-3); font-family: var(--font-mono, monospace); margin-left: 6px; }
.seg { display: inline-flex; border: 1px solid var(--surface-border); border-radius: var(--radius-md); overflow: hidden; }
.lvbtn { border: 0; background: var(--surface); color: var(--text-2); font-size: 11px; padding: 4px 11px; cursor: pointer; font-family: inherit; border-right: 1px solid var(--surface-border); }
.lvbtn:last-child { border-right: 0; }
.lvbtn.on.rw { background: var(--success); color: #fff; }
.lvbtn.on.ro { background: #a87d22; color: #fff; }
.lvbtn.on.hd { background: var(--text-3); color: #fff; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 10px 0 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
