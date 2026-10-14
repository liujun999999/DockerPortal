<template>
  <div class="audit-log-list">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <div class="filters">
            <el-select v-model="level" placeholder="全部分级" clearable style="width:130px" @change="reload">
              <el-option v-for="l in LEVELS" :key="l.value" :value="l.value" :label="l.label" />
            </el-select>
            <el-input v-model="username" placeholder="用户名" clearable style="width:140px"
              @keyup.enter="reload" @clear="reload" />
            <el-button @click="reload" :icon="Refresh">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column label="分级" width="96">
          <template #default="{ row }">
            <el-tag v-if="row.level" :type="levelTag(row.level)" size="small" effect="light">
              {{ levelLabel(row.level) }}
            </el-tag>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户" width="110" />
        <el-table-column prop="action" label="操作" width="180" show-overflow-tooltip />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'FAILURE'" type="danger" size="small" effect="plain">失败</el-tag>
            <el-tag v-else-if="row.result === 'SUCCESS'" type="success" size="small" effect="plain">成功</el-tag>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源" width="100" />
        <el-table-column prop="resourceId" label="资源 ID" width="150" show-overflow-tooltip />
        <el-table-column label="耗时" width="80" align="right">
          <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + ' ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="详情 / 错误" min-width="200">
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="err-text">{{ row.errorMessage }}</span>
            <el-popover v-else-if="row.detail" placement="top" :width="420" trigger="click">
              <template #reference>
                <span class="detail-text">{{ row.detail?.substring(0, 50) }}</span>
              </template>
              <pre class="detail-json">{{ row.detail }}</pre>
            </el-popover>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 0" :current-page="page" :page-size="pageSize" :page-sizes="PAGE_SIZES" :total="total"
        layout="total, sizes, prev, pager, next, jumper" @current-change="handlePageChange"
        @size-change="handleSizeChange" style="margin-top: 16px; justify-content: flex-end" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAuditLogs } from '@/api/audit'
import { PAGE_SIZES } from '@/composables/usePagination'
import { formatDateTime } from '@/utils/format'

const LEVELS = [
  { value: 'SECURITY', label: '安全', tag: 'danger' },
  { value: 'CRITICAL', label: '高危', tag: 'danger' },
  { value: 'OPERATION', label: '操作', tag: 'warning' },
  { value: 'CONFIG', label: '配置', tag: 'info' },
] as const

const logs = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const level = ref('')
const username = ref('')

function levelLabel(v: string) {
  return LEVELS.find((l) => l.value === v)?.label || v
}
function levelTag(v: string) {
  return LEVELS.find((l) => l.value === v)?.tag || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const result: any = await getAuditLogs({
      page: page.value, size: pageSize.value,
      level: level.value || undefined, username: username.value || undefined,
    })
    logs.value = result.content || []
    total.value = result.totalElements || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function reload() {
  page.value = 1
  loadData()
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; align-items: center; gap: 10px; }
.detail-text { cursor: pointer; color: var(--el-color-primary); }
.detail-json { max-height: 300px; overflow: auto; font-size: 12px; white-space: pre-wrap; }
.err-text { color: var(--el-color-danger); font-size: 12px; }
.dim { color: var(--el-text-color-secondary); }
</style>
