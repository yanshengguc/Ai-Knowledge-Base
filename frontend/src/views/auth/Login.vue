<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">{{ t('app.name') }}</h1>
      <p class="auth-sub">{{ t('app.tagline') }}</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="t('auth.username')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('auth.password')"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="auth-btn" :loading="loading" @click="onSubmit">
            {{ t('auth.login') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        {{ t('auth.noAccount') }}
        <router-link to="/register">{{ t('auth.goRegister') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: t('auth.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('auth.passwordRequired'), trigger: 'blur' }],
}

async function onSubmit() {
  if (!formRef.value) return
  let ok = false
  try {
    await formRef.value.validate()
    loading.value = true
    await userStore.login({ username: form.username, password: form.password })
    ok = true
  } catch (e) {
    console.error(t('auth.loginFail'), e)
    // 错误提示已在拦截器处理
  } finally {
    loading.value = false
  }
  if (ok) {
    ElMessage.success(t('auth.loginSuccess'))
    console.log('→ /knowledge')
    router.push('/knowledge')
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
  color: $color-text;
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
