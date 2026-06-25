// =============================================================
// 前端认证 + 权限状态（增强③ R1/R4）
// 说明：登录后由 httpOnly Cookie 维持会话；本模块缓存当前用户与有效权限，
//   供路由守卫、菜单过滤、按钮门控使用。
//   perms：{resourceCode: 'RW'|'RO'}，缺省即 HIDDEN（默认拒绝）。
// =============================================================
import { reactive } from 'vue'
import { api } from '@/api/client.js'

export const authState = reactive({ ready: false, user: null, perms: {} })

/** 探测会话 + 拉取有效权限。 */
export async function refreshAuth() {
  try {
    authState.user = await api.get('/auth/me')
    authState.perms = await api.get('/me/permissions')
  } catch (e) {
    authState.user = null
    authState.perms = {}
  }
  authState.ready = true
  return authState.user
}

/** 登录成功后：记录用户并拉取权限。 */
export async function setUser(u) {
  authState.user = u
  try {
    authState.perms = await api.get('/me/permissions')
  } catch (e) {
    authState.perms = {}
  }
  authState.ready = true
}

/** 登出/失效后清除。 */
export function clearUser() {
  authState.user = null
  authState.perms = {}
  authState.ready = true
}

/** 对资源是否可见（非 HIDDEN，即 RW 或 RO）。 */
export function canSee(code) {
  return authState.perms[code] !== undefined
}

/** 对资源是否有读写权（RW）。 */
export function canWrite(code) {
  return authState.perms[code] === 'RW'
}
