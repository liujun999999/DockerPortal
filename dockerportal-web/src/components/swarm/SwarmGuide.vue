<template>
  <el-card shadow="never" class="swarm-guide">
    <!-- 场景一：尚未初始化 Swarm 集群 -->
    <el-result v-if="code === 40901" icon="info" title="尚未创建 Swarm 集群">
      <template #sub-title>
        <p class="desc">
          当前 Docker 环境（{{ envName }}）还没有初始化 Swarm 集群。
          初始化后，本节点将成为集群的 Manager（管理节点），即可进行节点、服务、Stack 与任务的集群管理。
        </p>
        <p class="hint">相当于命令行执行：<code>docker swarm init</code></p>
      </template>
      <template #extra>
        <el-button type="primary" :loading="initializing" @click="handleInit" v-if="isAdmin">初始化 Swarm 集群</el-button>
        <el-button @click="emit('retry')">重新检查</el-button>
      </template>
    </el-result>

    <!-- 场景二：已加入集群但本节点是 Worker -->
    <el-result v-else-if="code === 40902" icon="warning" title="当前节点是 Swarm Worker">
      <template #sub-title>
        <p class="desc">
          当前 Docker 环境（{{ envName }}）已加入 Swarm 集群，但本节点是 Worker 工作节点，
          没有集群管理权限，无法查看或修改集群状态。
        </p>
        <p class="hint">您可以：</p>
        <ul class="hint">
          <li>切换到集群中 Manager 节点所在的 Docker 环境进行管理；</li>
          <li>或在本节点执行 <code>docker swarm leave --force</code> 退出集群后，重新 <code>docker swarm init</code> 创建新集群。</li>
        </ul>
      </template>
      <template #extra>
        <el-button @click="emit('retry')">重新检查</el-button>
        <el-button type="danger" plain :loading="leaving" @click="handleLeave" v-if="isAdmin">退出本地集群</el-button>
      </template>
    </el-result>

    <!-- 场景三：集群已初始化但 Manager 被锁定 -->
    <el-result v-else icon="warning" title="Swarm Manager 已锁定">
      <template #sub-title>
        <p class="desc">
          当前环境（{{ envName }}）的 Swarm 集群开启了 Manager 锁定，需要解锁后才能管理集群。
        </p>
        <p class="hint">请在服务器命令行执行：<code>docker swarm unlock</code>，并按提示输入解锁密钥。</p>
        <p class="hint">解锁密钥可在未锁定时的 <code>docker swarm unlock-key</code> 命令中查看。</p>
      </template>
      <template #extra>
        <el-button type="primary" @click="emit('retry')">解锁后重新检查</el-button>
      </template>
    </el-result>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { initSwarm, leaveSwarm } from '@/api/swarm'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'

const props = defineProps<{
  /** 40901=未初始化集群 40902=Worker 节点 40903=Manager 锁定 */
  code: number
}>()

const emit = defineEmits<{
  (e: 'done'): void
  (e: 'retry'): void
}>()

const envStore = useEnvironmentStore()
const { isAdmin } = usePermission()
const envName = computed(() => envStore.currentEnv?.name || `环境 ${envStore.currentEnvId}`)

const initializing = ref(false)
const leaving = ref(false)

async function handleInit() {
  const envId = envStore.currentEnvId
  if (!envId) return
  let advertiseAddr: string | undefined
  try {
    const r = await ElMessageBox.prompt(
      '可选：指定对外广播地址（IP 或网卡名，如 192.168.1.10、en0）。留空则由 Docker 自动探测。',
      '初始化 Swarm 集群',
      { confirmButtonText: '开始初始化', cancelButtonText: '取消', inputPlaceholder: 'advertise 地址（可选）', inputValue: '' },
    )
    advertiseAddr = (r.value || '').trim() || undefined
  } catch {
    return // 用户取消
  }
  initializing.value = true
  try {
    await initSwarm(envId, advertiseAddr)
    ElMessage.success('Swarm 集群初始化成功，当前节点已成为 Manager')
    emit('done')
  } catch {
    // 具体错误已由 axios 拦截器提示
  } finally {
    initializing.value = false
  }
}

async function handleLeave() {
  const envId = envStore.currentEnvId
  if (!envId) return
  try {
    await ElMessageBox.confirm(
      '将让当前节点退出 Swarm 集群（docker swarm leave --force）。若本节点是唯一 Manager，集群将被解散。确定继续？',
      '退出集群',
      { type: 'warning', confirmButtonText: '退出集群', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  leaving.value = true
  try {
    await leaveSwarm(envId, true)
    ElMessage.success('已退出 Swarm 集群，现在可以初始化新集群')
    emit('done')
  } catch {
    // 拦截器已提示
  } finally {
    leaving.value = false
  }
}
</script>

<style scoped lang="scss">
.swarm-guide {
  .desc {
    color: #606266;
    font-size: 14px;
    line-height: 1.7;
    max-width: 640px;
    margin: 0 auto;
  }

  .hint {
    color: #909399;
    font-size: 13px;
    margin: 8px auto 0;
    text-align: center;

    code {
      background: #f5f7fa;
      padding: 2px 6px;
      border-radius: 4px;
      color: #476582;
    }
  }

  ul.hint {
    list-style: none;
    padding: 0;
  }
}
</style>
