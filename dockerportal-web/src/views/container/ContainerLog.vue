<template>
  <div class="container-log">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>容器日志 - {{ containerId?.substring(0, 12) }}</span>
          <div class="header-actions">
            <el-select v-model="tail" style="width:120px" @change="loadLogs()">
              <el-option :value="50" label="50 行" />
              <el-option :value="100" label="100 行" />
              <el-option :value="500" label="500 行" />
              <el-option :value="1000" label="1000 行" />
            </el-select>
            <el-select v-model="interval" :disabled="!autoRefresh" style="width:96px">
              <el-option :value="2000" label="2 秒" />
              <el-option :value="5000" label="5 秒" />
              <el-option :value="10000" label="10 秒" />
              <el-option :value="30000" label="30 秒" />
            </el-select>
            <el-switch v-model="autoRefresh" active-text="自动刷新" />
            <el-button @click="loadLogs()" :icon="Refresh">刷新</el-button>
          </div>
        </div>
      </template>
      <div ref="logBoxRef" class="log-content" v-loading="loading">
        <pre>{{ logs }}</pre>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getContainerLogs } from '@/api/container'
import { useEnvironmentStore } from '@/stores/environment'
import { useAutoRefresh } from '@/composables/useAutoRefresh'

const route = useRoute()
const envStore = useEnvironmentStore()
const containerId = route.params.id as string
const logs = ref('')
const loading = ref(false)
const tail = ref(100)
const logBoxRef = ref<HTMLElement | null>(null)

/**
 * 刷新后是否跟到底部：手动 / 首次加载强制到底；自动刷新只在用户本来就贴在底部时跟随，
 * 否则用户往上翻历史时会被每几秒拽回去，根本没法看。
 */
function scrollToBottom(force: boolean) {
  nextTick(() => {
    const el = logBoxRef.value
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40
    if (force || nearBottom) el.scrollTop = el.scrollHeight
  })
}

// silent：自动刷新走静默，不弹整块 loading 遮罩，避免每几秒闪一下
async function loadLogs(silent = false) {
  const envId = envStore.currentEnvId
  if (!envId) return
  if (!silent) loading.value = true
  try {
    logs.value = await getContainerLogs(envId, containerId, tail.value, true)
    scrollToBottom(!silent)
  } catch (e) { console.error(e) }
  finally { if (!silent) loading.value = false }
}

const { enabled: autoRefresh, interval } = useAutoRefresh(() => loadLogs(true))

onMounted(loadLogs)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.log-content {
  background: #1e1e1e; color: #d4d4d4; border-radius: 6px;
  padding: 16px; max-height: 600px; overflow: auto;
  font-family: 'Consolas', monospace; font-size: 13px; line-height: 1.5;
  white-space: pre-wrap; word-break: break-all;
}
</style>
