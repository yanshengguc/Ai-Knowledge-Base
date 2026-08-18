<template>
  <el-container class="layout">
    <el-aside :width="isMobile ? '0px' : '200px'" class="aside">
      <div v-if="!isMobile" class="logo">📚 {{ t('app.name') }}</div>
      <el-menu router :default-active="$route.path" class="menu">
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon>
          <span>{{ t('nav.knowledge') }}</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ t('nav.chat') }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon v-if="isMobile" class="menu-toggle" @click="mobileMenuVisible = !mobileMenuVisible">
          <Expand />
        </el-icon>
        <div class="header-right">
          <span class="username">{{ userStore.username || t('nav.user') }}</span>
          <el-dropdown @command="onLang">
            <el-button text>
              <el-icon><Operation /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh" :class="{ active: locale === 'zh' }">简体中文</el-dropdown-item>
                <el-dropdown-item command="en" :class="{ active: locale === 'en' }">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown @command="onCommand">
            <el-button text>
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">{{ t('nav.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 移动端抽屉菜单 -->
      <el-drawer v-model="mobileMenuVisible" direction="ltr" size="200px" :with-header="false">
        <div class="logo">📚 {{ t('app.name') }}</div>
        <el-menu router :default-active="$route.path" @select="mobileMenuVisible = false">
          <el-menu-item index="/knowledge">
            <el-icon><Collection /></el-icon>
            <span>{{ t('nav.knowledge') }}</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ t('nav.chat') }}</span>
          </el-menu-item>
        </el-menu>
      </el-drawer>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Operation } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

const router = useRouter()
const userStore = useUserStore()
const { t, locale } = useI18n()

const isMobile = ref(window.innerWidth <= 768)
const mobileMenuVisible = ref(false)

function onResize() {
  isMobile.value = window.innerWidth <= 768
}

onMounted(() => window.addEventListener('resize', onResize))
onBeforeUnmount(() => window.removeEventListener('resize', onResize))

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    ElMessage.success(t('auth.logoutSuccess'))
    router.push('/login')
  }
}

function onLang(lang: 'zh' | 'en') {
  setLocale(lang)
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.layout {
  height: 100%;
}

.aside {
  background: $color-bg-card;
  border-right: 1px solid $color-border;

  .logo {
    padding: $space-4 $space-3;
    font-size: $font-size-md;
    font-weight: 600;
  }

  .menu {
    border-right: none;
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $color-bg-card;
  border-bottom: 1px solid $color-border;

  .menu-toggle {
    font-size: 20px;
    cursor: pointer;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: $space-2;
  }

  .username {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
}

.main {
  padding: $space-4;
  overflow: auto;
}
.el-dropdown-menu__item.active {
  font-weight: 600;
  color: $color-primary;
  background: $color-primary-light;
}
</style>
