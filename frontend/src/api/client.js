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

// 增强③ R1：身份改为真实登录的 httpOnly Cookie(grc_token)，请求自动携带(credentials:'include')；
// 不再发送 X-User 头——前端强制登录。未登录/令牌失效时后端返回 401，本客户端统一跳登录页。
// 回调（由 main.js 注入）：收到 401 时执行（通常 router → /login）。
let onUnauthorized = null
export function setUnauthorizedHandler(fn) { onUnauthorized = fn }

async function request(method, path, body) {
  const headers = {}
  const opts = { method, headers, credentials: 'include' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(body)
  }
  const resp = await fetch(BASE + path, opts)
  const text = await resp.text()
  const data = text ? JSON.parse(text) : null
  if (!resp.ok) {
    // 401 未认证：跳登录（登录接口自身的 401 由调用方处理，不在此跳转）
    if (resp.status === 401 && !path.startsWith('/auth/') && onUnauthorized) {
      onUnauthorized()
    }
    const msg = (data && (data.message || data.error)) || ('HTTP ' + resp.status)
    const err = new Error(msg)
    err.status = resp.status
    err.body = data
    throw err
  }
  return data
}

// 文件上传：发送 multipart/form-data（不手设 Content-Type，由浏览器带 boundary）。
// 用于风险评估表单引擎上传 .docx 模板等场景。错误处理与 request 一致。
async function upload(path, formData) {
  const resp = await fetch(BASE + path, { method: 'POST', body: formData, credentials: 'include' })
  const text = await resp.text()
  const data = text ? JSON.parse(text) : null
  if (!resp.ok) {
    if (resp.status === 401 && !path.startsWith('/auth/') && onUnauthorized) {
      onUnauthorized()
    }
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
  del: (path) => request('DELETE', path),
  upload
}
