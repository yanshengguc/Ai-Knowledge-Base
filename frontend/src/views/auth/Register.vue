<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">{{ t('auth.registerTitle') }}</h1>
      <p class="auth-sub">{{ t('auth.registerSub') }}</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="t('auth.username')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" :placeholder="t('auth.nickname')" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('auth.password')"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('auth.confirmPassword')"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-btn" :loading="loading" @click="onSubmit">
            {{ t('auth.register') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        {{ t('auth.hasAccount') }}
        <router-link to="/login">{{ t('auth.goLogin') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

const rules: FormRules = {
  username: [{ required: true, message: t('auth.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: t('auth.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('auth.passwordMin'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('auth.confirmRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error(t('auth.passwordMismatch')))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
    })
    ElMessage.success(t('auth.registerSuccess'))
    router.push('/login')
  } catch {
    // 错误提示已在拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  background: linear-gradient(160deg, $color-primary-light 0%, $color-bg 60%);
}

.auth-card {
  width: 360px;
  padding: $space-8 $space-6;
  background: $color-bg-card;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;

  @media (max-width: $bp-sm) {
    width: 90%;
  }
}

.auth-title {
  text-align: center;
  font-size: $font-size-lg + 4px;
}

.auth-sub {
  text-align: center;
  color: $color-text-muted;
  margin: $space-2 0 $space-6;
}

.auth-btn {
  width: 100%;
}

.auth-footer {
  text-align: center;
  color: $color-text-secondary;
  font-size: $font-size-sm;
}
</style>
