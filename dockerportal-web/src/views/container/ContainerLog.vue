<template>
  <div class="container-log">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>容器日志 - {{ containerId?.substring(0, 12) }}</span>
          <div>
            <el-select v-model="tail" style="width:120px" @change="loadLogs">
              <el-option :value="50" label="50 行" />
              <el-option :value="100" label="100 行" />
              <el-option :value="500" label="500 行" />
              <el-option :value="1000" label="1000 行" />
            </el-select>
            <el-button @click="loadLogs" :icon="Refresh">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="log-content" v-loading="loading">
        <pre>{{ logs }}</pre>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getContainerLogs } from '@/api/container'
import { useEnvironmentStore } from '@/stores/environment'

const route = useRoute()
const envStore = useEnvironmentStore()
const containerId = route.params.id as string
const logs = ref('')
const loading = ref(false)
const tail = ref(100)

async function loadLogs() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    logs.value = await getContainerLogs(envId, containerId, tail.value, true)
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

onMounted(loadLogs)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.log-content {
  background: #1e1e1e; color: #d4d4d4; border-radius: 6px;
  padding: 16px; max-height: 600px; overflow: auto;
  font-family: 'Consolas', monospace; font-size: 13px; line-height: 1.5;
  white-space: pre-wrap; word-break: break-all;
}
</style>
