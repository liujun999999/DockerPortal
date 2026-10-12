<template>
  <div class="task-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Swarm 任务</span>
          <el-button @click="loadData" :icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="tasks" stripe v-loading="loading">
        <el-table-column label="ID" width="130">
          <template #default="{ row }">{{ row.ID?.substring(0, 12) }}</template>
        </el-table-column>
        <el-table-column label="服务" width="150">
          <template #default="{ row }">{{ row.ServiceID?.substring(0, 12) || '-' }}</template>
        </el-table-column>
        <el-table-column label="节点" width="150">
          <template #default="{ row }">{{ row.NodeID?.substring(0, 12) || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.Status?.State || 'unknown'" :status-map="TASK_STATES" />
          </template>
        </el-table-column>
        <el-table-column label="Desired" width="100">
          <template #default="{ row }">{{ row.DesiredState || '-' }}</template>
        </el-table-column>
        <el-table-column label="错误" min-width="200">
          <template #default="{ row }">{{ row.Status?.Err || row.Status?.Message || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.UpdatedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getSwarmTasks } from '@/api/swarm'
import { useEnvironmentStore } from '@/stores/environment'
import { TASK_STATES } from '@/utils/constants'
import { formatDateTime } from '@/utils/format'
import StatusTag from '@/components/common/StatusTag.vue'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const envStore = useEnvironmentStore()
const tasks = ref<any[]>([])
const loading = ref(false)
const guideCode = ref(0)

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    tasks.value = await getSwarmTasks(envId)
    guideCode.value = 0
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
      tasks.value = []
    } else {
      console.error(e)
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
