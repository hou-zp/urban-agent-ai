<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-card__header">
        <div class="login-card__brand">AI</div>
        <h1 class="login-card__title">城市风险提升综合智能体</h1>
        <p class="login-card__subtitle">请登录您的账号</p>
      </div>

      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
        @finish="handleLogin"
      >
        <a-form-item label="用户 ID" name="userId">
          <a-input
            v-model:value="formState.userId"
            placeholder="请输入用户 ID"
            size="large"
            autocomplete="username"
          />
        </a-form-item>

        <a-form-item label="密码" name="password">
          <a-input-password
            v-model:value="formState.password"
            placeholder="请输入密码"
            size="large"
            autocomplete="current-password"
            @pressEnter="handleLogin"
          />
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
          >
            登录
          </a-button>
        </a-form-item>

        <a-alert v-if="errorMsg" type="error" show-icon :message="errorMsg" style="margin-top: 8px" />
      </a-form>

      <div class="login-card__footer">
        <p class="login-card__hint">默认账号：demo-user / Urban2026!</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMsg = ref('')
const formRef = ref()
const isDark = ref(false)

onMounted(() => {
  isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    isDark.value = e.matches
  })
})

const cardClass = computed(() => (isDark.value ? 'dark' : ''))

const formState = ref({
  userId: 'demo-user',
  password: 'Urban2026!',
})

const rules = {
  userId: [{ required: true, message: '请输入用户 ID' }],
  password: [{ required: true, message: '请输入密码' }],
}

async function handleLogin() {
  loading.value = true
  errorMsg.value = ''
  try {
    const ok = await auth.login(formState.value.userId, formState.value.password)
    if (ok) {
      router.push('/')
    } else {
      errorMsg.value = '用户名或密码错误'
    }
  } catch (err: unknown) {
    errorMsg.value = (err as Error).message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.login-card__header {
  text-align: center;
  margin-bottom: 32px;
}

.login-card__brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 12px;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 16px;
}

.login-card__title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 8px 0;
}

.login-card__subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.login-card__footer {
  margin-top: 24px;
  text-align: center;
}

.login-card__hint {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

@media (prefers-color-scheme: dark) {
  .login-page {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }

  .login-card {
    background: #1e2533;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  }

  .login-card__title {
    color: #e2e8f0;
  }

  .login-card__subtitle,
  .login-card__hint {
    color: #94a3b8;
  }
}
</style>