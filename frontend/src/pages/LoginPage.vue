<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <aside class="login-panel login-panel--brand">
      <div class="brand-bg">
        <div class="brand-bg__circle brand-bg__circle--1" />
        <div class="brand-bg__circle brand-bg__circle--2" />
        <div class="brand-bg__circle brand-bg__circle--3" />
      </div>

      <div class="brand-content">
        <div class="brand-logo">
          <div class="brand-logo__mark">
            <RobotOutlined />
          </div>
          <div class="brand-logo__text">
            <h1 class="brand-logo__name">Urban Agent</h1>
            <p class="brand-logo__sub">城管 AI 智能体平台</p>
          </div>
        </div>

        <div class="brand-features">
          <div v-for="feature in features" :key="feature.title" class="feature-item">
            <div class="feature-item__icon">
              <component :is="feature.icon" />
            </div>
            <div class="feature-item__content">
              <h3 class="feature-item__title">{{ feature.title }}</h3>
              <p class="feature-item__desc">{{ feature.desc }}</p>
            </div>
          </div>
        </div>

        <div class="brand-footer">
          <p>面向城管业务场景的智能体、知识检索、智能问数与审计工作台</p>
        </div>
      </div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="login-panel login-panel--form">
      <div class="login-card">
        <!-- 移动端品牌 -->
        <div class="login-card__mobile-brand">
          <div class="brand-logo__mark brand-logo__mark--small">
            <RobotOutlined />
          </div>
          <span class="brand-logo__name brand-logo__name--small">Urban Agent</span>
        </div>

        <div class="login-card__header">
          <h2 class="login-card__title">欢迎回来</h2>
          <p class="login-card__subtitle">请登录您的账号以继续使用平台</p>
        </div>

        <a-form
          ref="formRef"
          :model="formState"
          :rules="rules"
          layout="vertical"
          class="login-form"
          @finish="handleLogin"
        >
          <a-form-item label="用户 ID" name="userId">
            <a-input
              v-model:value="formState.userId"
              placeholder="请输入用户 ID"
              size="large"
              autocomplete="username"
            >
              <template #prefix>
                <UserOutlined class="input-icon" />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item label="密码" name="password">
            <a-input-password
              v-model:value="formState.password"
              placeholder="请输入密码"
              size="large"
              autocomplete="current-password"
            >
              <template #prefix>
                <LockOutlined class="input-icon" />
              </template>
            </a-input-password>
          </a-form-item>

          <div class="login-form__options">
            <a-checkbox>记住登录状态</a-checkbox>
            <a class="login-form__forgot">忘记密码？</a>
          </div>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="loading"
              class="login-btn"
            >
              登 录
            </a-button>
          </a-form-item>
        </a-form>

        <a-alert
          v-if="errorMsg"
          type="error"
          show-icon
          :message="errorMsg"
          class="login-error"
        />

        <div class="login-card__footer">
          <span>默认演示账号</span>
          <code>demo-user / Urban2026!</code>
        </div>
      </div>

      <p class="login-form__copyright">
        © {{ currentYear }} 城管 AI 智能体平台 · 政务内网版
      </p>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ApiOutlined,
  AuditOutlined,
  BookOutlined,
  LockOutlined,
  MessageOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const errorMsg = ref('')
const formRef = ref()

const currentYear = computed(() => new Date().getFullYear())

const features = [
  {
    icon: MessageOutlined,
    title: '智能会话',
    desc: '基于 ReAct 推理的智能体，支持多轮对话、工具调用与来源引用',
  },
  {
    icon: BookOutlined,
    title: '政策法规知识库',
    desc: 'RAG 检索引擎，确保政策咨询答案可追溯、有出处',
  },
  {
    icon: AuditOutlined,
    title: '全链路审计',
    desc: '工具调用、SQL 执行、风险事件全程记录，支持事后回溯',
  },
  {
    icon: ApiOutlined,
    title: '智能问数',
    desc: '自然语言转 SQL，权限改写、脱敏与口径说明一体化',
  },
]

const formState = reactive({
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
    const ok = await authStore.login(formState.userId, formState.password)
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
/* ===== 页面布局 ===== */
.login-page {
  display: flex;
  min-height: 100vh;
  background: var(--bg-base);
}

/* ===== 左侧品牌区 ===== */
.login-panel--brand {
  flex: 0 0 480px;
  background: var(--color-primary);
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: var(--space-10);
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.brand-bg__circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}

.brand-bg__circle--1 {
  width: 480px;
  height: 480px;
  top: -200px;
  right: -180px;
}

.brand-bg__circle--2 {
  width: 280px;
  height: 280px;
  bottom: -80px;
  left: -60px;
}

.brand-bg__circle--3 {
  width: 160px;
  height: 160px;
  bottom: 180px;
  right: 40px;
}

.brand-content {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.brand-logo__mark {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 24px;
  flex: 0 0 auto;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.brand-logo__mark--small {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-lg);
  font-size: 16px;
}

.brand-logo__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-logo__name {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: #fff;
  line-height: var(--leading-tight);
}

.brand-logo__name--small {
  font-size: var(--text-md);
  color: var(--text-primary);
}

.brand-logo__sub {
  font-size: var(--text-sm);
  color: rgba(255, 255, 255, 0.65);
  line-height: var(--leading-normal);
  margin: 0;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}

.feature-item__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.9);
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  font-size: 18px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.feature-item__content {
  flex: 1;
  min-width: 0;
}

.feature-item__title {
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  color: #fff;
  line-height: var(--leading-snug);
  margin: 0 0 4px;
}

.feature-item__desc {
  font-size: var(--text-sm);
  color: rgba(255, 255, 255, 0.55);
  line-height: var(--leading-relaxed);
  margin: 0;
}

.brand-footer p {
  font-size: var(--text-xs);
  color: rgba(255, 255, 255, 0.4);
  line-height: var(--leading-relaxed);
  margin: 0;
}

/* ===== 右侧表单区 ===== */
.login-panel--form {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  gap: var(--space-6);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: var(--space-10) var(--space-8);
  background: var(--bg-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--border-color);
}

.login-card__mobile-brand {
  display: none;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.login-card__header {
  margin-bottom: var(--space-8);
}

.login-card__title {
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
  color: var(--text-primary);
  line-height: var(--leading-tight);
  margin: 0 0 var(--space-2);
}

.login-card__subtitle {
  font-size: var(--text-md);
  color: var(--text-secondary);
  line-height: var(--leading-normal);
  margin: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
}

.login-form .ant-form-item-label > label {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--text-secondary);
}

.input-icon {
  color: var(--text-tertiary);
  font-size: 16px;
}

.login-form__options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-1);
  margin-bottom: var(--space-5);
  font-size: var(--text-sm);
}

.login-form__forgot {
  font-size: var(--text-sm);
  color: var(--color-primary);
}

.login-form__forgot:hover {
  color: var(--color-primary-hover);
}

.login-btn.ant-btn {
  height: 44px;
  font-size: var(--text-md);
  font-weight: var(--font-semibold);
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-primary);
  transition: all var(--duration-normal) var(--ease-default);
}

.login-btn.ant-btn:hover {
  background: var(--color-primary-hover) !important;
  border-color: var(--color-primary-hover) !important;
  transform: translateY(-1px);
}

.login-btn.ant-btn:active {
  transform: translateY(0);
}

.login-error {
  margin-top: var(--space-4);
  border-radius: var(--radius-lg);
}

.login-card__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--border-color-light);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.login-card__footer code {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-secondary);
  background: var(--bg-inset);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color-light);
}

.login-form__copyright {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  text-align: center;
  margin: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .login-panel--brand {
    display: none;
  }

  .login-panel--form {
    padding: var(--space-6);
  }

  .login-card {
    padding: var(--space-8) var(--space-6);
  }

  .login-card__mobile-brand {
    display: flex;
  }
}

@media (max-width: 480px) {
  .login-panel--form {
    padding: var(--space-4);
    justify-content: flex-start;
    padding-top: var(--space-10);
  }

  .login-card {
    padding: var(--space-6) var(--space-4);
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-lg);
  }
}
</style>