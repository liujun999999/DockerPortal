<template>
  <div class="monitor-dashboard">
    <!-- 节点信息 -->
    <el-row :gutter="20" class="mb-20">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="CPU 核心" :value="nodeInfo.cpus || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总内存" :value="formatBytes(nodeInfo.memory || 0)" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="运行容器" :value="nodeInfo.containersRunning || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="Docker 版本">
            <template #default>{{ nodeInfo.dockerVersion || '-' }}</template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 容器资源统计 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>容器资源使用</span>
          <el-button @click="loadStats" :icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="containerStats" stripe v-loading="loading">
        <el-table-column prop="name" label="容器" min-width="150" />
        <el-table-column prop="image" label="镜像" min-width="150" />
        <el-table-column label="CPU %" width="100">
          <template #default="{ row }">{{ row.cpuPercent?.toFixed(1) || '0.0' }}%</template>
        </el-table-column>
        <el-table-column label="内存使用" width="130">
          <template #default="{ row }">{{ formatBytes(row.memoryUsage || 0) }}</template>
        </el-table-column>
        <el-table-column label="内存 %" width="100">
          <template #default="{ row }">{{ row.memoryPercent?.toFixed(1) || '0.0' }}%</template>
        </el-table-column>
        <el-table-column label="网络接收" width="120">
          <template #default="{ row }">{{ formatBytes(row.networkRxBytes || 0) }}</template>
        </el-table-column>
        <el-table-column label="网络发送" width="120">
          <template #default="{ row }">{{ formatBytes(row.networkTxBytes || 0) }}</template>
        </el-table-column>
        <el-table-column label="块读取" width="120">
          <template #default="{ row }">{{ formatBytes(row.blockReadBytes || 0) }}</template>
        </el-table-column>
        <el-table-column label="块写入" width="120">
          <template #default="{ row }">{{ formatBytes(row.blockWriteBytes || 0) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getNodeInfo, getAllContainerStats } from '@/api/monitor'
import { useEnvironmentStore } from '@/stores/environment'
import { formatBytes } from '@/utils/format'

const envStore = useEnvironmentStore()
const nodeInfo = ref<any>({})
const containerStats = ref<any[]>([])
const loading = ref(false)

async function loadStats() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    const [info, stats] = await Promise.all([
      getNodeInfo(envId),
      getAllContainerStats(envId),
    ])
    nodeInfo.value = info
    containerStats.value = stats
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

onMounted(loadStats)
</script>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
