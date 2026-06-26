<!-- =============================================================
     登录页（严格复原 前端原型/登录页·5主题.html）
     复原要点：
       1. 左品牌侧（垂直+水平居中）+ 右登录卡片，5 主题渐变侧栏；
       2. 认证标签「统一身份登录(AD)」默认在前、「本地账号」在后；
       3. 主题切换 + 中英语言切换（持久化）；
       4. 标题/标语有默认值，并读取 localStorage 覆盖项
          （grc-login-title-zh/en、grc-login-slogan-zh/en、
           grc-brand-name/sub、grc-logo-img/text、grc-forgot-url）；
       5. 配色/间距/圆角全部复用 tokens.css 令牌，不另起色板。
     注意：登录页品牌侧采用「渐变侧栏」专用变量（--side-bg/--side-fg/
     --surface-border 等），与原型一致，故在本组件内按主题覆盖。
     ============================================================= -->
<template>
  <div class="login-root">
    <!-- ===== 品牌侧 ===== -->
    <div class="brandpane">
      <div class="crest"></div>
      <div class="bp-top">
        <!-- Logo：可被 grc-logo-img(背景图) / grc-logo-text(字符) 覆盖 -->
        <div class="logo" :style="logoStyle">{{ logoText }}</div>
        <div>
          <b>{{ brandName }}</b>
          <span>{{ brandSub }}</span>
        </div>
      </div>
      <div class="bp-mid">
        <!-- 标题/标语含 <br>，用 v-html 渲染；读 localStorage 覆盖 -->
        <h1 v-html="loginTitle"></h1>
        <p v-html="loginSlogan"></p>
        <div class="feats">
          <div class="feat">
            <span class="ic">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                <path d="M12 2 3 7v6c0 5 3.8 8.5 9 10 5.2-1.5 9-5 9-10V7z" />
              </svg>
            </span>
            <div>
              <b>{{ $t('login.feat1Title') }}</b>
              <em>{{ $t('login.feat1Desc') }}</em>
            </div>
          </div>
          <div class="feat">
            <span class="ic">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                <path
                  d="M9 11l3 3 8-8M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"
                />
              </svg>
            </span>
            <div>
              <b>{{ $t('login.feat2Title') }}</b>
              <em>{{ $t('login.feat2Desc') }}</em>
            </div>
          </div>
          <div class="feat">
            <span class="ic">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                <path
                  d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
                />
              </svg>
            </span>
            <div>
              <b>{{ $t('login.feat3Title') }}</b>
              <em>{{ $t('login.feat3Desc') }}</em>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 表单侧 ===== -->
    <div class="formpane">
      <div class="fp-bar">
        <LangSwitch />
        <ThemeSwitch />
      </div>

      <div class="fp-body">
        <form class="card" autocomplete="off" @submit.prevent="onSubmit">
          <h2>{{ $t('login.welcome') }}</h2>
          <div class="sub">{{ $t('login.sub') }}</div>

          <!-- 认证标签：统一身份(AD) 默认在前，本地账号在后 -->
          <div class="authtabs">
            <button
              type="button"
              class="at"
              :class="{ on: pane === 'sso' }"
              @click="pane = 'sso'"
            >
              {{ $t('login.tabSso') }}
            </button>
            <button
              type="button"
              class="at"
              :class="{ on: pane === 'local' }"
              @click="pane = 'local'"
            >
              {{ $t('login.tabLocal') }}
            </button>
          </div>

          <!-- 登录错误提示（增强③ R1）-->
          <div v-if="loginError" class="loginerr">{{ loginError }}</div>

          <!-- 统一身份(AD)：默认登录方式，输入域账号 -->
          <div v-show="pane === 'sso'" class="apane show">
            <div class="ssohint">{{ $t('login.ssoHint') }}</div>
            <div class="field">
              <label>{{ $t('login.domainAccount') }}</label>
              <div class="ipt">
                <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                  <circle cx="12" cy="7" r="4" />
                </svg>
                <input
                  v-model="sso.account"
                  type="text"
                  :placeholder="$t('login.domainPlaceholder')"
                />
              </div>
            </div>
            <div class="field">
              <label>{{ $t('common.password') }}</label>
              <div class="ipt">
                <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                  <rect x="3" y="11" width="18" height="11" rx="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
                <input
                  v-model="sso.pwd"
                  :type="ssoPwdShow ? 'text' : 'password'"
                />
                <svg
                  class="eye"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke-width="1.8"
                  @click="ssoPwdShow = !ssoPwdShow"
                >
                  <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </div>
            </div>
            <div class="field">
              <label>{{ $t('common.captcha') }}</label>
              <div class="capt">
                <div class="ipt">
                  <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                    <path d="M9 12l2 2 4-4" />
                    <circle cx="12" cy="12" r="9" />
                  </svg>
                  <input v-model="sso.captcha" type="text" />
                </div>
                <div class="captbox" title="点击刷新" @click="refreshCaptcha">
                  {{ captcha }}
                </div>
              </div>
            </div>
            <div class="row">
              <label class="chk">
                <input v-model="sso.remember" type="checkbox" />
                <span>{{ $t('common.rememberMe') }}</span>
              </label>
              <a
                class="link"
                :href="forgotUrl || '#'"
                :target="forgotUrl ? '_blank' : undefined"
                rel="noopener"
                >{{ $t('common.forgotPwd') }}</a
              >
            </div>
            <button class="btn" type="submit">{{ $t('common.signIn') }}</button>
          </div>

          <!-- 本地账号：仅 AD 不可用时的应急 / 管理员登录 -->
          <div v-show="pane === 'local'" class="apane show">
            <div class="field">
              <label>{{ $t('login.localAccount') }}</label>
              <div class="ipt">
                <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                  <circle cx="12" cy="7" r="4" />
                </svg>
                <input v-model="local.account" type="text" />
              </div>
            </div>
            <div class="field">
              <label>{{ $t('common.password') }}</label>
              <div class="ipt">
                <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                  <rect x="3" y="11" width="18" height="11" rx="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
                <input
                  v-model="local.pwd"
                  :type="localPwdShow ? 'text' : 'password'"
                />
                <svg
                  class="eye"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke-width="1.8"
                  @click="localPwdShow = !localPwdShow"
                >
                  <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </div>
            </div>
            <div class="field">
              <label>{{ $t('common.captcha') }}</label>
              <div class="capt">
                <div class="ipt">
                  <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8">
                    <path d="M9 12l2 2 4-4" />
                    <circle cx="12" cy="12" r="9" />
                  </svg>
                  <input v-model="local.captcha" type="text" />
                </div>
                <div class="captbox" title="点击刷新" @click="refreshCaptcha">
                  {{ captcha }}
                </div>
              </div>
            </div>
            <div class="row">
              <label class="chk">
                <input v-model="local.remember" type="checkbox" />
                <span>{{ $t('common.rememberMe') }}</span>
              </label>
              <a
                class="link"
                :href="forgotUrl || '#'"
                :target="forgotUrl ? '_blank' : undefined"
                rel="noopener"
                >{{ $t('common.forgotPwd') }}</a
              >
            </div>
            <button class="btn" type="submit">{{ $t('common.signIn') }}</button>
            <div class="localnote">{{ $t('login.localNote') }}</div>
          </div>
        </form>
      </div>

      <div class="foot">
        <span>{{ $t('login.foot1') }}</span><br />
        <span>{{ $t('login.foot2') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
// ——登录页逻辑：认证方式切换、密码显隐、验证码刷新、后台可配置文案/品牌——
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import LangSwitch from '@/components/LangSwitch.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import { api } from '@/api/client.js'
import { setUser } from '@/auth.js'

const { t, locale } = useI18n()
const router = useRouter()
const loginError = ref('')
const submitting = ref(false)

// 认证面板：默认统一身份(AD)
const pane = ref('sso')

// 密码显隐
const ssoPwdShow = ref(false)
const localPwdShow = ref(false)

// 两套表单数据（预填可直接登录的种子账号，便于演示；上线改回域账号占位）
const sso = ref({
  account: 'group_admin',
  pwd: 'demo1234',
  captcha: '',
  remember: true
})
const local = ref({
  account: 'pay_user',
  pwd: 'demo1234',
  captcha: '',
  remember: false
})

// 验证码（点击刷新，逻辑参考原型字符集）
const captcha = ref('7K9Q')
function refreshCaptcha() {
  const caps = 'ABCDEFGHJKLMNPQRSTUVWXY3456789'
  let s = ''
  for (let i = 0; i < 4; i++) {
    s += caps[Math.floor(Math.random() * caps.length)]
  }
  captcha.value = s
}

// ——可后台配置项：优先后端 /api/branding（系统设置→登录页与品牌，全局生效），
//   其次 localStorage 覆盖项，最后 i18n 默认——
const brand = ref({})
onMounted(async () => {
  try { brand.value = await api.get('/branding') || {} } catch (e) { brand.value = {} }
})

// 平台名称 / 副名
const brandName = computed(
  () => brand.value.brandName || localStorage.getItem('grc-brand-name') || t('common.brandName')
)
const brandSub = computed(
  () => brand.value.brandSub || localStorage.getItem('grc-brand-sub') || t('common.brandSub')
)

// Logo：图片优先，其次字符，默认 'G'
const logoImg = computed(() => brand.value.logoImg || localStorage.getItem('grc-logo-img') || '')
const logoText = computed(() =>
  logoImg.value ? '' : (brand.value.logoText || localStorage.getItem('grc-logo-text') || 'G')
)
const logoStyle = computed(() =>
  logoImg.value
    ? {
        backgroundImage: `url(${logoImg.value})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center'
      }
    : {}
)

// 标题 / 标语：后端 → localStorage 覆盖项（按语言取 zh/en）→ i18n 默认，换行符转 <br>
function nl2br(s) {
  return s.replace(/\n/g, '<br>')
}
const loginTitle = computed(() => {
  const en = locale.value === 'en'
  const back = en ? brand.value.loginTitleEn : brand.value.loginTitleZh
  const v = back || localStorage.getItem(en ? 'grc-login-title-en' : 'grc-login-title-zh')
  return v ? nl2br(v) : t('login.title')
})
const loginSlogan = computed(() => {
  const en = locale.value === 'en'
  const back = en ? brand.value.loginSloganEn : brand.value.loginSloganZh
  const v = back || localStorage.getItem(en ? 'grc-login-slogan-en' : 'grc-login-slogan-zh')
  return v ? nl2br(v) : t('login.slogan')
})

// 忘记密码链接
const forgotUrl = computed(() => brand.value.forgotUrl || localStorage.getItem('grc-forgot-url') || '')

// 登录提交：调真实后端 /api/auth/login（增强③ R1）。
// 成功→后端置 httpOnly Cookie，前端记录用户并进仪表盘；失败→提示。
async function onSubmit() {
  if (submitting.value) return
  loginError.value = ''
  submitting.value = true
  const acct = pane.value === 'sso' ? sso.value.account : local.value.account
  const pwd = pane.value === 'sso' ? sso.value.pwd : local.value.pwd
  try {
    const u = await api.post('/auth/login', { username: acct, password: pwd })
    await setUser(u)
    router.push('/dashboard')
  } catch (e) {
    loginError.value = e.status === 401 ? t('login.badCred') : t('login.loginFail') + e.message
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* =========================================================
   品牌侧专用主题变量（与「登录页·5主题.html」严格一致）：
   登录页品牌侧使用渐变侧栏，与驾驶舱版的实底侧栏不同，故在此覆盖。
   ========================================================= */
.login-root {
  /* 原型中 body 本身即两栏 flex 容器；本工程多包了一层 .login-root，
     故显式用视口尺寸撑满，避免依赖祖先 body 宽度（更稳健，且与原型满屏两栏一致）。 */
  width: 100vw;
  min-height: 100vh;
  display: flex;
}
.login-root {
  --side-bg: linear-gradient(150deg, #a81e22, #7e1216);
  --side-fg: #f0cdce;
  --surface-border: #e8e3d8;
}
/* 整条选择器都放进 :global()——否则 Vue 只把 body.t-* 设为全局、给 .login-root 加作用域属性，
   导致按主题切换品牌侧背景的覆盖不生效（品牌侧始终停留在默认朱砂红）。 */
:global(body.t-sand .login-root) {
  --side-bg: linear-gradient(150deg, #2a7d63, #16513f);
  --side-fg: #d6e7df;
  --surface-border: #e7e1d6;
}
:global(body.t-glass .login-root) {
  --side-bg: linear-gradient(150deg, #1f9aa6, #147884);
  --side-fg: #dff1f3;
  --surface-border: #e3eaf0;
}
:global(body.t-emerald .login-root) {
  --side-bg: linear-gradient(160deg, #0f172a, #1b2a44);
  --side-fg: #9aa6b8;
  --surface-border: #e6e9ef;
}
:global(body.t-editorial .login-root) {
  --side-bg: linear-gradient(155deg, #2f6645, #1d4730);
  --side-fg: #dbe7df;
  --surface-border: #e4e2d6;
}

/* ===== 品牌侧 ===== */
.brandpane {
  flex: 1.15;
  min-width: 0;
  position: relative;
  overflow: hidden;
  background: var(--side-bg);
  color: var(--side-fg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 52px 56px;
}
.brandpane::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
      680px 480px at 78% 18%,
      rgba(255, 255, 255, 0.13),
      transparent 60%
    ),
    radial-gradient(
      560px 420px at 12% 88%,
      rgba(0, 0, 0, 0.18),
      transparent 60%
    );
  pointer-events: none;
}
.bp-top {
  display: flex;
  align-items: center;
  gap: 13px;
  position: absolute;
  top: 46px;
  left: 56px;
  z-index: 3;
}
.logo {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 21px;
  background: linear-gradient(145deg, #f0d690, var(--accent-gold));
  color: var(--accent-strong);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.2);
}
.bp-top b {
  font-size: 19px;
  letter-spacing: 0.5px;
  color: #fff;
  font-family: var(--font-display);
}
.bp-top span {
  display: block;
  font-size: 12px;
  opacity: 0.8;
  margin-top: 2px;
  letter-spacing: 3px;
}
.bp-mid {
  position: relative;
  z-index: 2;
  animation: fadeIn 0.6s ease both;
  max-width: 560px;
}
.bp-mid h1 {
  font-size: 36px;
  line-height: 1.3;
  color: #fff;
  font-weight: 760;
  letter-spacing: 0.5px;
  font-family: var(--font-display);
}
.bp-mid h1::after {
  content: '';
  display: block;
  width: 54px;
  height: 3px;
  border-radius: 3px;
  background: var(--accent-gold);
  margin: 18px auto 0;
}
.bp-mid p {
  margin: 18px auto 0;
  font-size: 15.5px;
  line-height: 1.92;
  max-width: 470px;
  opacity: 0.94;
}
.feats {
  margin: 30px auto 0;
  display: flex;
  flex-direction: column;
  gap: 13px;
  max-width: 420px;
  text-align: left;
}
.feat {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  font-size: 13px;
  opacity: 0.94;
}
.feat .ic {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.16);
}
.feat .ic svg {
  width: 16px;
  height: 16px;
  stroke: #fff;
}
.feat b {
  display: block;
  color: #fff;
  font-weight: 650;
  margin-bottom: 1px;
  font-size: 13px;
}
.feat em {
  font-style: normal;
  opacity: 0.78;
}
.crest {
  position: absolute;
  right: -70px;
  bottom: -60px;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  z-index: 1;
  animation: floatY 7s ease-in-out infinite;
}
.crest::before {
  content: '';
  position: absolute;
  inset: 38px;
  border-radius: 50%;
  border: 1px dashed rgba(255, 255, 255, 0.12);
}

/* ===== 表单侧 ===== */
.formpane {
  flex: 0.85;
  min-width: 440px;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.fp-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  padding: 22px 40px;
}
.fp-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 40px 30px;
}
.card {
  width: 100%;
  max-width: 392px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: calc(var(--radius-lg) + 6px);
  box-shadow: var(--shadow-2);
  padding: 38px 38px 32px;
  animation: fadeUp 0.55s ease both;
}
.card h2 {
  font-size: 23px;
  font-weight: 740;
  letter-spacing: 0.3px;
  font-family: var(--font-display);
}
.card .sub {
  color: var(--text-3);
  font-size: 13px;
  margin-top: 7px;
  margin-bottom: 20px;
}
.authtabs {
  display: flex;
  gap: 5px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 4px;
  margin-bottom: 22px;
}
.authtabs .at {
  flex: 1;
  border: 0;
  background: transparent;
  padding: 9px 6px;
  border-radius: calc(var(--radius-md) - 1px);
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  font-family: inherit;
  font-weight: 550;
  transition: 0.16s;
}
.authtabs .at.on {
  background: var(--surface);
  color: var(--accent-strong);
  box-shadow: var(--shadow-2);
  font-weight: 660;
}
.apane.show {
  display: block;
  animation: fadeUp 0.32s ease both;
}
.ssohint {
  font-size: 12.5px;
  color: var(--text-3);
  line-height: 1.7;
  margin-bottom: 16px;
  text-align: center;
}
.loginerr {
  margin-bottom: 16px;
  padding: 9px 12px;
  font-size: 12.5px;
  color: var(--danger);
  background: var(--danger-tint, rgba(180, 35, 45, 0.1));
  border: 1px solid var(--danger);
  border-radius: var(--radius-md);
}
.localnote {
  margin-top: 16px;
  font-size: 11.5px;
  color: var(--text-4);
  line-height: 1.65;
  text-align: center;
}
.field {
  margin-bottom: 16px;
}
.field label {
  display: block;
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 7px;
  font-weight: 550;
}
.ipt {
  position: relative;
}
.ipt > svg {
  position: absolute;
  left: 13px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  stroke: var(--text-4);
}
.ipt input {
  width: 100%;
  height: 44px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  padding: 0 14px 0 39px;
  font-size: 14px;
  color: var(--text-1);
  font-family: inherit;
  transition: 0.18s;
  outline: none;
}
.ipt input:focus {
  border-color: var(--accent);
  background: var(--surface);
  box-shadow: 0 0 0 3px var(--accent-tint);
}
.ipt .eye {
  left: auto;
  right: 13px;
  cursor: pointer;
  stroke: var(--text-3);
}
.capt {
  display: flex;
  gap: 10px;
}
.capt .ipt {
  flex: 1;
}
.captbox {
  width: 108px;
  height: 44px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: linear-gradient(110deg, var(--accent-weak), var(--surface));
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 19px;
  letter-spacing: 5px;
  color: var(--accent-strong);
  font-family: var(--font-serif);
  font-style: italic;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 22px;
  font-size: 12.5px;
}
.chk {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-2);
  cursor: pointer;
}
.chk input {
  width: 15px;
  height: 15px;
  accent-color: var(--accent);
}
.link {
  color: var(--accent);
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
.btn {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: #fff;
  font-size: 15px;
  font-weight: 650;
  cursor: pointer;
  font-family: inherit;
  letter-spacing: 1px;
  transition: 0.18s;
  box-shadow: 0 6px 16px -6px var(--accent);
}
.btn:hover {
  background: var(--accent-strong);
}
.btn:active {
  transform: translateY(1px);
}
.foot {
  text-align: center;
  color: var(--text-4);
  font-size: 11.5px;
  padding: 0 40px 24px;
  line-height: 1.7;
}

/* 响应式（与原型 @media max-width:900px 一致） */
@media (max-width: 900px) {
  .login-root {
    flex-direction: column;
  }
  .brandpane {
    flex: none;
    padding: 30px 26px;
  }
  .crest,
  .feats,
  .bp-mid p {
    display: none;
  }
  .formpane {
    min-width: 0;
  }
  .fp-bar {
    padding: 16px 20px;
  }
  .fp-body {
    padding: 6px 20px 26px;
  }
  .card {
    padding: 28px 24px;
  }
}
</style>
