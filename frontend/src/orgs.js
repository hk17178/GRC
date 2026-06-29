// =============================================================
// 组织树共享数据（供各业务表单的「所属组织」下拉统一使用）
// 说明：从后端 /api/orgs/tree/1 读真实组织树（物化路径），全局缓存一次；
//   新增/重命名/删除子公司后调 reloadOrgs() 刷新，各下拉即时联动。
//   这样业务数据的「所属组织」不再写死 12/13，新增子公司即可被选为归属——
//   真正把组织树贯穿到业务数据归属（隔离/权限本就按 org 生效）。
// =============================================================
import { ref } from 'vue'
import { api } from '@/api/client.js'

// 全局共享的组织列表（[{id,parentId,orgType,code,name,path}]）
export const orgs = ref([])
let loaded = false

async function load() {
  try { orgs.value = await api.get('/orgs/tree/1') } catch (e) { orgs.value = [] }
}

/** 取共享组织列表（首次调用触发加载）。 */
export function useOrgs() {
  if (!loaded) { loaded = true; load() }
  return orgs
}

/** 组织变更后强制刷新（组织管理页增删改后调用）。 */
export function reloadOrgs() { load() }

/** 层级深度：path '/1'→1，'/1/12'→2（用于下拉缩进显示）。 */
export function orgDepth(o) { return ((o.path || '').match(/\//g) || []).length }

/** 下拉显示文案：按层级缩进 + 名称。 */
export function orgLabel(o) { return '　'.repeat(Math.max(0, orgDepth(o) - 1)) + o.name }
