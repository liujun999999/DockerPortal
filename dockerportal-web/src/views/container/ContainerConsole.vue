<template>
  <div class="container-console">
    <el-card ref="cardRef" shadow="never" class="console-card" :class="{ 'is-fs': isFullscreen }">
      <template #header>
        <div class="card-header">
          <div class="title-box">
            <span>容器终端 - {{ containerId?.substring(0, 12) }}</span>
            <el-tag :type="connected ? 'success' : 'danger'" size="small">
              {{ connected ? '已连接' : '未连接' }}
            </el-tag>
            <el-button v-if="!connected" size="small" type="primary" @click="reconnect">重新连接</el-button>
          </div>
          <div class="header-actions">
            <span v-if="isFullscreen" class="head-tip">按 ESC 可退出全屏</span>
            <el-button size="small" :icon="FullScreen" @click="toggle">
              {{ isFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
        </div>
      </template>
      <XTerminal :key="termKey" :ws-url="wsUrl" @open="connected = true" @close="connected = false" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { FullScreen } from '@element-plus/icons-vue'
import { useEnvironmentStore } from '@/stores/environment'
import { useAuthStore } from '@/stores/auth'
import { useFullscreen } from '@/composables/useFullscreen'
import XTerminal from '@/components/terminal/XTerminal.vue'

const route = useRoute()
const envStore = useEnvironmentStore()
const authStore = useAuthStore()
const containerId = route.params.id as string
const connected = ref(false)
const termKey = ref(0)
const cardRef = ref<HTMLElement>()
const { isFullscreen, toggle } = useFullscreen(cardRef)

// 重建终端组件即可重连：exec 会话不能复用，必须重新走一遍握手
function reconnect() {
  termKey.value++
}

const wsUrl = computed(() => {
  const envId = envStore.currentEnvId
  const token = authStore.token
  if (!envId) return ''
  return `/ws/env/${envId}/containers/${containerId}/console${token ? `?token=${token}` : ''}`
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title-box { display: flex; align-items: center; gap: 10px; }
.header-actions { display: flex; align-items: center; gap: 10px; }
.head-tip { font-size: 12px; color: #909399; }
/* 全屏：卡片撑满视口，终端区跟 flex 拉伸（XTerminal 内部有 ResizeObserver，尺寸变会自动 fit） */
.console-card.is-fs {
  height: 100vh;
  box-sizing: border-box;
  border-radius: 0;
  display: flex;
  flex-direction: column;
}
.console-card.is-fs :deep(.el-card__header) { flex: none; }
.console-card.is-fs :deep(.el-card__body) { flex: 1; min-height: 0; }
</style>
