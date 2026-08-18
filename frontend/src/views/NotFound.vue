<template>
  <div class="not-found">
    <div class="code">404</div>
    <div class="desc">{{ t('notFound.title') }}</div>
    <el-button type="primary" @click="router.push('/knowledge')">{{ t('notFound.back') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
const router = useRouter()
const { t } = useI18n()
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: $space-4;

  .code {
    font-size: 64px;
    font-weight: 700;
    color: $color-primary;
  }

  .desc {
    color: $color-text-secondary;
    margin-bottom: $space-4;
  }
}
</style>
