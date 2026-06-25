// =============================================================
// 前端认证状态（增强③ R1）
// 说明：登录后由 httpOnly Cookie 维持会话；本模块缓存当前用户，
//   供路由守卫判定是否放行、AppShell 展示登录人。
//   ready=false 表示尚未探测过；探测经 GET /api/auth/me。
// =============================================================
import { reactive } from 'vue'
import { api } from '@/api/client.js'

export const authState = reactive({ ready: false, user: null })

/** 探测当前会话（/auth/me）；未登录则 user=null。 */
export async function refreshAuth() {
  try {
    authState.user = await api.get('/auth/me')
  } catch (e) {
    authState.user = null
  }
  authState.ready = true
  return authState.user
}

/** 登录成功后写入当前用户。 */
export function setUser(u) {
  authState.user = u
  authState.ready = true
}

/** 登出/失效后清除。 */
export function clearUser() {
  authState.user = null
  authState.ready = true
}
