<template>
  <div class="audit-log-list">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <el-button @click="loadData" :icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="action" label="操作" width="150" />
        <el-table-column prop="resourceType" label="资源类型" width="100" />
        <el-table-column prop="resourceId" label="资源 ID" width="150" />
        <el-table-column prop="environmentId" label="环境" width="80" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="详情" min-width="200">
          <template #default="{ row }">
            <el-popover v-if="row.detail" placement="top" :width="400" trigger="hover">
              <template #reference>
                <span class="detail-text">{{ row.detail?.substring(0, 50) }}</span>
              </template>
              <pre class="detail-json">{{ row.detail }}</pre>
            </el-popover>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 0" :current-page="page" :page-size="pageSize" :total="total"
        layout="total, prev, pager, next" @current-change="handlePageChange" style="margin-top: 16px" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAuditLogs } from '@/api/audit'
import { formatDateTime } from '@/utils/format'

const logs = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const result = await getAuditLogs(page.value, pageSize.value)
    logs.value = result.content || []
    total.value = result.totalElements || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.detail-text { cursor: pointer; color: var(--el-color-primary); }
.detail-json { max-height: 300px; overflow: auto; font-size: 12px; white-space: pre-wrap; }
</style>
