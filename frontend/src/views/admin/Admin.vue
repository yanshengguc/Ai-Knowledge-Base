<template>
  <div class="admin">
    <div class="page-header">
      <h2>{{ t('admin.title') }}</h2>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">{{ t('admin.refresh') }}</el-button>
    </div>

    <!-- 失败态:与"真的没有数据"区分开 -->
    <el-card v-if="loadError" class="panel" shadow="never">
      <el-empty :description="t('admin.loadFailed')">
        <el-button type="primary" @click="loadAll">{{ t('common.retry') }}</el-button>
      </el-empty>
    </el-card>

    <template v-else>
      <div v-loading="loading" class="stat-grid">
        <el-card v-for="card in statCards" :key="card.key" class="stat-card" shadow="never">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </el-card>
      </div>

      <el-card class="panel" shadow="never">
        <template #header>{{ t('admin.fileStatusTitle') }}</template>
        <div class="status-row">
          <div v-for="s in statusList" :key="s.key" class="status-item">
            <el-tag :type="s.tag" effect="light" size="small">{{ s.label }}</el-tag>
            <span class="status-count">{{ s.count }}</span>
          </div>
          <span v-if="statusList.length === 0" class="muted">{{ t('admin.noData') }}</span>
        </div>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>{{ t('admin.tokenTitle') }}</span>
            <span class="muted">{{ t('admin.tokenHint') }}</span>
          </div>
        </template>
        <div class="token-row">
          <div v-for="item in tokenItems" :key="item.key" class="token-item">
            <div class="stat-label">{{ item.label }}</div>
            <div class="stat-value-sm">{{ item.value }}</div>
          </div>
        </div>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <div class="panel-header">
            <span>{{ t('admin.usersTitle') }}</span>
            <el-input
              v-model="keyword"
              :placeholder="t('admin.userSearch')"
              :prefix-icon="Search"
              clearable
              size="small"
              class="user-search"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
          </div>
        </template>
        <el-table v-loading="usersLoading" :data="users" size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="username" :label="t('admin.username')" />
          <el-table-column :label="t('admin.nickname')">
            <template #default="{ row }">{{ row.nickname || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('admin.role')" width="120">
            <template #default="{ row }">
              <el-tag :type="row.role === 'admin' ? 'success' : 'info'" effect="light" size="small">
                {{ row.role === 'admin' ? t('admin.roleAdmin') : t('admin.roleUser') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('admin.actions')" width="240">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? t('admin.selfOperationDisabled') : undefined"
                @click="onChangeRole(row)"
              >
                {{ row.role === 'admin' ? t('admin.demote') : t('admin.promote') }}
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? t('admin.selfOperationDisabled') : undefined"
                @click="onResetPassword(row)"
              >
                {{ t('admin.resetPassword') }}
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? t('admin.selfOperationDisabled') : undefined"
                @click="onDeleteUser(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!usersLoading && users.length === 0" :description="t('admin.userEmpty')" />
        <el-pagination
          v-if="total > size"
          class="pager"
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  deleteAdminUser,
  getAdminOverview,
  getAdminUsers,
  resetAdminUserPassword,
  updateAdminUserRole,
  type AdminOverview,
  type AdminRole,
  type AdminUser,
} from '@/api/modules/admin'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()

const loading = ref(false)
const loadError = ref(false)
const overview = ref<AdminOverview | null>(null)

const usersLoading = ref(false)
const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const userStore = useUserStore()
/** 当前登录用户 id:用于禁用"自己这一行"的写操作按钮 */
const currentUserId = computed(() => userStore.id)

const STATUS_LABELS: Record<string, { label: string; tag: 'success' | 'danger' | 'warning' | 'info' }> = {
  SUCCESS: { label: 'SUCCESS', tag: 'success' },
  FAILED: { label: 'FAILED', tag: 'danger' },
  PROCESSING: { label: 'PROCESSING', tag: 'warning' },
}

function formatNumber(value?: number) {
  return (value ?? 0).toLocaleString()
}

function formatCost(value?: number) {
  // 成本以元计价,保留 4 位:小站用量少,2 位会被舍成 0.00 看不出差别
  return `${(value ?? 0).toFixed(4)} CNY`
}

const statCards = computed(() => [
  { key: 'user', label: t('admin.userCount'), value: formatNumber(overview.value?.userCount) },
  { key: 'knowledge', label: t('admin.knowledgeCount'), value: formatNumber(overview.value?.knowledgeCount) },
  { key: 'file', label: t('admin.fileCount'), value: formatNumber(overview.value?.fileCount) },
  { key: 'chunk', label: t('admin.chunkCount'), value: formatNumber(overview.value?.chunkCount) },
])

const statusList = computed(() => {
  const dist = overview.value?.fileStatus ?? {}
  return Object.keys(dist).map((key) => ({
    key,
    label: STATUS_LABELS[key]?.label ?? key,
    tag: STATUS_LABELS[key]?.tag ?? 'info',
    count: formatNumber(dist[key]),
  }))
})

const tokenItems = computed(() => {
  const usage = overview.value?.tokenUsage ?? {}
  return [
    { key: 'todayTokens', label: t('admin.todayTokens'), value: formatNumber(usage.todayTokens) },
    { key: 'totalTokens', label: t('admin.totalTokens'), value: formatNumber(usage.totalTokens) },
    { key: 'chatTokens', label: t('admin.chatTokens'), value: formatNumber(usage.chatTokens) },
    { key: 'embeddingTokens', label: t('admin.embeddingTokens'), value: formatNumber(usage.embeddingTokens) },
    { key: 'totalCost', label: t('admin.totalCost'), value: formatCost(usage.totalCost) },
  ]
})

async function loadOverview() {
  const res = await getAdminOverview()
  overview.value = res.data
}

async function loadUsers() {
  usersLoading.value = true
  try {
    const res = await getAdminUsers({ page: page.value, size, keyword: keyword.value || undefined })
    users.value = res.data?.list ?? []
    total.value = res.data?.total ?? 0
  } finally {
    usersLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  loadError.value = false
  try {
    await Promise.all([loadOverview(), loadUsers()])
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadUsers()
}

function onPageChange(next: number) {
  page.value = next
  loadUsers()
}

/** 自己这一行禁用写操作(后端亦会拒绝自我操作,此处仅做前端防误触) */
function isSelf(row: AdminUser) {
  return currentUserId.value != null && row.id === currentUserId.value
}

async function onChangeRole(row: AdminUser) {
  const nextRole: AdminRole = row.role === 'admin' ? 'user' : 'admin'
  const roleLabel = nextRole === 'admin' ? t('admin.roleAdmin') : t('admin.roleUser')
  try {
    await ElMessageBox.confirm(
      t('admin.changeRoleConfirm', { name: row.username, role: roleLabel }),
      t('common.confirm'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
    )
  } catch {
    return
  }
  try {
    await updateAdminUserRole(row.id, nextRole)
    ElMessage.success(t('admin.roleUpdated'))
    await loadUsers()
  } catch {
    /* 请求拦截器已提示失败原因 */
  }
}

async function onResetPassword(row: AdminUser) {
  let value: string
  try {
    const res = await ElMessageBox.prompt(t('admin.resetPasswordPrompt', { name: row.username }), t('common.confirm'), {
      inputType: 'password',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      inputValidator: (v: string) => (v && v.trim() ? true : t('admin.resetPasswordRequired')),
    })
    value = res.value
  } catch {
    return
  }
  try {
    await resetAdminUserPassword(row.id, value.trim())
    ElMessage.success(t('admin.resetPasswordSuccess'))
  } catch {
    /* 请求拦截器已提示失败原因 */
  }
}

async function onDeleteUser(row: AdminUser) {
  try {
    await ElMessageBox.confirm(t('admin.deleteUserConfirm', { name: row.username }), t('common.confirm'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  try {
    await deleteAdminUser(row.id)
    ElMessage.success(t('admin.userDeleted'))
    await loadUsers()
  } catch {
    /* 请求拦截器已提示失败原因 */
  }
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $space-4;

  h2 {
    margin: 0;
    font-size: $font-size-lg;
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: $space-3;
  margin-bottom: $space-4;
}

.stat-card {
  text-align: center;
}

.stat-label {
  color: $color-text-secondary;
  font-size: $font-size-sm;
}

.stat-value {
  margin-top: $space-2;
  font-size: 24px;
  font-weight: 600;
  color: $color-primary;
}

.stat-value-sm {
  margin-top: $space-1;
  font-size: $font-size-md;
  font-weight: 600;
}

.panel {
  margin-bottom: $space-4;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-3;
}

.user-search {
  width: min(240px, 45vw);
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-4;
}

.status-item {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.status-count {
  font-weight: 600;
}

.token-row {
  display: flex;
  flex-wrap: wrap;
  gap: $space-6;
}

.muted {
  color: $color-text-secondary;
  font-size: $font-size-sm;
}

.pager {
  margin-top: $space-3;
  justify-content: flex-end;
}
</style>