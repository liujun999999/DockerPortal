<template>
  <div class="alert-history">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>告警历史</span>
          <el-button @click="loadData" :icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="events" stripe v-loading="loading">
        <el-table-column prop="ruleName" label="规则" min-width="150" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <StatusTag :status="row.severity" :status-map="ALERT_SEVERITY" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <StatusTag :status="row.status" :status-map="ALERT_STATUS" />
          </template>
        </el-table-column>
        <el-table-column prop="metric" label="指标" width="120" />
        <el-table-column label="当前值/阈值" width="130">
          <template #default="{ row }">{{ row.currentValue?.toFixed(1) }} / {{ row.threshold }}</template>
        </el-table-column>
        <el-table-column label="触发时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.firedAt) }}</template>
        </el-table-column>
        <el-table-column label="解决时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.resolvedAt) }}</template>
        </el-table-column>
        <el-table-column label="已通知" width="80">
          <template #default="{ row }">
            <el-tag :type="row.notified ? 'success' : 'info'" size="small">{{ row.notified ? '是' : '否' }}</el-tag>
          </template>
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
import { getAlertEvents } from '@/api/alert'
import { PAGE_SIZES } from '@/composables/usePagination'
import { ALERT_SEVERITY, ALERT_STATUS } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'

const events = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const result = await getAlertEvents({ page: page.value, size: pageSize.value })
    events.value = result.content || []
    total.value = result.totalElements || 0
  } catch (e) { console.error(e) }
  finally { loading.value = false }
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
</style>
