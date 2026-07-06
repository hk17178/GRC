<template>
  <!-- 安全加固包 B17：修改口令页（首登强制改密的落点，也供日常改密）。极简卡片，复用登录页配色。 -->
  <div class="cp-root">
    <div class="cp-card">
      <h2>{{ $t('changepwd.title') }}</h2>
      <p class="sub">{{ forced ? $t('changepwd.forcedSub') : $t('changepwd.sub') }}</p>

      <label class="fld">{{ $t('changepwd.oldLabel') }}
        <input v-model="oldPwd" type="password" autocomplete="off" :placeholder="$t('changepwd.oldPh')" />
      </label>
      <label class="fld">{{ $t('changepwd.newLabel') }}
        <input v-model="newPwd" type="password" autocomplete="new-password" :placeholder="$t('changepwd.newPh')" />
      </label>
      <label class="fld">{{ $t('changepwd.confirmLabel') }}
        <input v-model="confirmPwd" type="password" autocomplete="new-password" :placeholder="$t('changepwd.confirmPh')" />
      </label>

      <p v-if="err" class="err">{{ err }}</p>
      <p v-if="ok" class="ok">{{ $t('changepwd.okMsg') }}</p>

      <div class="actions">
        <button v-if="!forced" class="btn ghost" @click="goBack">{{ $t('common.cancel') }}</button>
        <button class="btn" :disabled="busy" @click="submit">{{ busy ? $t('common.submitting') : $t('changepwd.submit') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/client.js'
import { authState, refreshAuth } from '@/auth.js'

const { t } = useI18n()
const router = useRouter()
const forced = computed(() => !!(authState.user && authState.user.mustChangePassword))

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const err = ref('')
const ok = ref(false)
const busy = ref(false)

function goBack() { router.push('/dashboard') }

async function submit() {
  err.value = ''
  if (newPwd.value.length < 8) { err.value = t('changepwd.errMin'); return }
  if (newPwd.value === 'demo1234') { err.value = t('changepwd.errDemo'); return }
  if (newPwd.value !== confirmPwd.value) { err.value = t('changepwd.errMismatch'); return }
  busy.value = true
  try {
    await api.post('/auth/change-password', { oldPassword: oldPwd.value, newPassword: newPwd.value })
    ok.value = true
    await refreshAuth() // 刷新会话（mustChangePassword 已被后端清零）
    setTimeout(() => router.push('/dashboard'), 800)
  } catch (e) {
    err.value = (e.body && e.body.message) || e.message
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.cp-root { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg); }
.cp-card { width: 400px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 30px 32px; }
.cp-card h2 { margin: 0 0 6px; font-size: 20px; font-family: var(--font-display); }
.sub { margin: 0 0 20px; font-size: 12.5px; color: var(--text-2); }
.fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 14px; }
.fld input { display: block; width: 100%; height: 40px; margin-top: 6px; padding: 0 12px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 14px; font-family: inherit; outline: none; box-sizing: border-box; }
.err { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.ok { color: var(--success); font-size: 12.5px; margin: 0 0 12px; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
.btn { background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 9px 18px; font-size: 13px; font-weight: 600; cursor: pointer; }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
</style>
