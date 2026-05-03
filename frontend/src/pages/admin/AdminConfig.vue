<template>
  <div class="admin-config">
    <div class="page-header">
      <div class="page-header-copy">
        <h2 class="page-title">系统配置</h2>
        <p class="page-description">大模型参数、检索策略与数据安全规则</p>
      </div>
    </div>

    <a-form layout="vertical" :model="form" @finish="handleSave">
      <!-- 大模型配置 -->
      <a-card class="section-card" :bordered="false" title="大模型参数">
        <a-row :gutter="24">
          <a-col :span="12">
            <a-form-item label="模型名称">
              <a-select v-model:value="form.modelProvider" :options="providerOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="Temperature">
              <a-slider
                v-model:value="form.temperature"
                :min="0" :max="2" :step="0.1"
                :marks="{ 0: '0', 1: '1.0', 2: '2.0' }"
              />
              <div class="slider-value">当前值：{{ form.temperature }}</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="最大回复 Token">
              <a-input-number v-model:value="form.maxTokens" :min="100" :max="8192" :step="256" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="Top-P">
              <a-slider v-model:value="form.topP" :min="0" :max="1" :step="0.05" :marks="{ 0: '0', 0.5: '0.5', 1: '1.0' }" />
              <div class="slider-value">当前值：{{ form.topP }}</div>
            </a-form-item>
          </a-col>
        </a-row>
      </a-card>

      <!-- 检索策略 -->
      <a-card class="section-card" :bordered="false" title="检索策略配置">
        <a-row :gutter="24">
          <a-col :span="12">
            <a-form-item label="Top-K（召回片段数）">
              <a-input-number v-model:value="form.topK" :min="1" :max="50" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="相似度阈值">
              <a-slider
                v-model:value="form.similarityThreshold"
                :min="0" :max="1" :step="0.05"
                :marks="{ 0: '0', 0.5: '0.5', 1: '1.0' }"
              />
              <div class="slider-value">当前值：{{ form.similarityThreshold }}</div>
            </a-form-item>
          </a-col>
        </a-row>
      </a-card>

      <!-- 数据安全 -->
      <a-card class="section-card" :bordered="false" title="数据安全与脱敏">
        <a-form-item label="脱敏规则">
          <a-space direction="vertical">
            <a-switch v-model:checked="form.maskPhone" /> 手机号脱敏（138****5678）
            <a-switch v-model:checked="form.maskIdCard" /> 身份证号脱敏
            <a-switch v-model:checked="form.maskAddress" /> 详细地址脱敏
            <a-switch v-model:checked="form.logAudit" /> 审计日志记录
          </a-space>
        </a-form-item>
      </a-card>

      <div class="config-actions">
        <a-button @click="resetForm">重置</a-button>
        <a-button type="primary" html-type="submit" :loading="saving">保存配置</a-button>
      </div>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { ref } from 'vue'
import { saveSystemConfig } from '@/api/admin'

const saving = ref(false)

interface ConfigForm {
  modelProvider: string
  temperature: number
  maxTokens: number
  topP: number
  topK: number
  similarityThreshold: number
  maskPhone: boolean
  maskIdCard: boolean
  maskAddress: boolean
  logAudit: boolean
}

const form = ref<ConfigForm>({
  modelProvider: 'openai',
  temperature: 0.7,
  maxTokens: 2048,
  topP: 0.9,
  topK: 5,
  similarityThreshold: 0.75,
  maskPhone: true,
  maskIdCard: true,
  maskAddress: false,
  logAudit: true,
})

const providerOptions = [
  { label: 'OpenAI GPT-4', value: 'openai' },
  { label: '阿里通义千问', value: 'qwen' },
  { label: '百度文心一言', value: 'wenxin' },
  { label: 'DeepSeek', value: 'deepseek' },
]

function resetForm() {
  form.value = {
    modelProvider: 'openai',
    temperature: 0.7,
    maxTokens: 2048,
    topP: 0.9,
    topK: 5,
    similarityThreshold: 0.75,
    maskPhone: true,
    maskIdCard: true,
    maskAddress: false,
    logAudit: true,
  }
}

async function handleSave() {
  saving.value = true
  try {
    await saveSystemConfig(form.value)
    message.success('配置已保存')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.slider-value {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-top: 4px;
  font-family: var(--font-mono);
}

.config-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-5);
}
</style>