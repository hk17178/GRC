// =============================================================
// 后端 API 客户端
// 说明：统一封装对后端 REST(/api/*) 的调用。
//  - 同源走 Vite 代理(/api → 后端 :8080)，免 CORS；
//  - 统一附带身份头 X-User（当前为开发桩；真实 AD/本地登录留待联测阶段替换，
//    届时此处改为从已认证会话取主体即可，调用方无需改动）；
//  - 统一 JSON 解析与错误处理（非 2xx 抛出带状态码与后端消息的 Error）。
// 设计依据：D1-5 接口设计、D2-5（前后端真联调，前端不得用静态假数据）。
// =============================================================

const BASE = '/api'

// 开发期当前用户桩：决定后端按哪个主体的 visibleOrgs 做 RLS 裁剪。
// group_admin=集团可见全部；pay_user/cf_user=仅本子公司。真实登录后由会话提供。
let currentUser = localStorage.getItem('grc-dev-user') || 'group_admin'

/** 设置开发期当前用户（供联调切换主体观察隔离效果）。 */
export function setDevUser(u) {
  currentUser = u
  localStorage.setItem('grc-dev-user', u)
}

/** 当前开发期用户。 */
export function getDevUser() {
  return currentUser
}

async function request(method, path, body) {
  const headers = { 'X-User': currentUser }
  const opts = { method, headers }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(body)
  }
  const resp = await fetch(BASE + path, opts)
  const text = await resp.text()
  const data = text ? JSON.parse(text) : null
  if (!resp.ok) {
    // 后端业务异常/校验失败：抛出带状态码与消息，供页面提示
    const msg = (data && (data.message || data.error)) || ('HTTP ' + resp.status)
    const err = new Error(msg)
    err.status = resp.status
    err.body = data
    throw err
  }
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path)
}
