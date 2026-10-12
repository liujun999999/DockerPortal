<template>
  <div class="swarm-overview">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />

    <template v-else-if="swarmInfo.swarmMode">
      <!-- 集群信息 -->
      <el-row :gutter="20" class="mb-20">
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="节点数" :value="swarmInfo.nodes || 0">
              <template #prefix><el-icon><Grid /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="Manager 数" :value="swarmInfo.managers || 0">
              <template #prefix><el-icon><Setting /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="服务数" :value="serviceCount">
              <template #prefix><el-icon><List /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="任务数" :value="taskCount">
              <template #prefix><el-icon><Tickets /></el-icon></template>
            </el-statistic>
          </el-card>
        </el-col>
      </el-row>

      <!-- 集群详情 -->
      <el-card shadow="never" class="mb-20">
        <template #header>
          <span>集群信息</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="节点 ID">{{ swarmInfo.nodeId }}</el-descriptions-item>
          <el-descriptions-item label="节点地址">{{ swarmInfo.nodeAddr }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag type="success">{{ swarmInfo.localNodeState }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="控制可用">
            <el-tag :type="swarmInfo.controlAvailable ? 'success' : 'warning'">
              {{ swarmInfo.controlAvailable ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 加入集群 -->
      <el-card v-if="joinInfo" shadow="never" class="mb-20">
        <template #header>
          <div class="card-header">
            <span>加入集群</span>
            <span class="tip">在需要加入集群的机器上执行以下命令（将地址替换为 Manager 节点 IP）</span>
          </div>
        </template>
        <div v-for="cmd in joinCommands" :key="cmd.label" class="join-cmd">
          <div class="join-cmd-info">
            <el-tag :type="cmd.tagType" size="small">{{ cmd.label }}</el-tag>
            <span class="join-desc">{{ cmd.desc }}</span>
          </div>
          <div class="join-cmd-code">
            <code>{{ cmd.text }}</code>
            <el-button size="small" type="primary" plain :icon="DocumentCopy" @click="copyCmd(cmd.text)">复制</el-button>
          </div>
        </div>
      </el-card>

      <!-- 节点列表 -->
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>节点列表</span>
            <el-button type="primary" text @click="$router.push('/swarm/nodes')">查看全部</el-button>
          </div>
        </template>
        <el-table :data="nodes" stripe>
          <el-table-column prop="ID" label="ID" width="150">
            <template #default="{ row }">{{ row.ID?.substring(0, 12) }}</template>
          </el-table-column>
          <el-table-column label="名称">
            <template #default="{ row }">{{ row.Description?.Hostname || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.Spec?.Role" :status-map="NODE_ROLES" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.Status?.State" :status-map="NODE_STATUS" />
            </template>
          </el-table-column>
          <el-table-column label="可用性" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.Spec?.Availability" :status-map="NODE_AVAILABILITY" />
            </template>
          </el-table-column>
          <el-table-column label="引擎版本">
            <template #default="{ row }">{{ row.Description?.Engine?.EngineVersion || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSwarmInfo, getSwarmJoinInfo } from '@/api/swarm'
import { getNodes } from '@/api/node'
import { getServices } from '@/api/service'
import { getSwarmTasks } from '@/api/swarm'
import { useEnvironmentStore } from '@/stores/environment'
import { NODE_ROLES, NODE_STATUS, NODE_AVAILABILITY } from '@/utils/constants'
import StatusTag from '@/components/common/StatusTag.vue'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const envStore = useEnvironmentStore()
const swarmInfo = ref<any>({})
const nodes = ref<any[]>([])
const serviceCount = ref(0)
const taskCount = ref(0)
const guideCode = ref(0)
const joinInfo = ref<any>(null)

const joinCommands = computed(() => [
  {
    label: 'Worker 节点',
    tagType: 'info' as const,
    desc: '作为工作节点加入，只运行任务、不参与调度决策',
    text: `docker swarm join --token ${joinInfo.value?.workerToken} ${joinInfo.value?.joinAddr}`,
  },
  {
    label: 'Manager 节点',
    tagType: 'warning' as const,
    desc: '作为管理节点加入，参与 Raft 集群管理（建议奇数个）',
    text: `docker swarm join --token ${joinInfo.value?.managerToken} ${joinInfo.value?.joinAddr}`,
  },
])

async function copyCmd(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('命令已复制')
  } catch {
    // 非安全上下文降级方案
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('命令已复制')
  }
}

async function loadJoinInfo(envId: number) {
  try {
    joinInfo.value = await getSwarmJoinInfo(envId)
  } catch {
    // Worker 节点无权限获取令牌，隐藏卡片即可
    joinInfo.value = null
  }
}

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return

  try {
    const [info, nodeList, services, tasks] = await Promise.all([
      getSwarmInfo(envId),
      getNodes(envId),
      getServices(envId),
      getSwarmTasks(envId),
    ])
    swarmInfo.value = info
    nodes.value = nodeList
    serviceCount.value = services?.length || 0
    taskCount.value = tasks?.length || 0
    // info 接口不报错但集群未初始化时（如 pending/locked 状态）也展示引导
    guideCode.value = info.swarmMode ? 0 : 40901
    if (info.swarmMode && info.controlAvailable !== false) {
      loadJoinInfo(envId)
    } else {
      joinInfo.value = null
    }
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
    } else {
      console.error('Failed to load swarm data', e)
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.mb-20 { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header .tip { font-size: 12px; color: #909399; }
.join-cmd { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 10px 0; }
.join-cmd + .join-cmd { border-top: 1px dashed #ebeef5; }
.join-cmd-info { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.join-desc { font-size: 12px; color: #909399; }
.join-cmd-code { display: flex; align-items: center; gap: 12px; min-width: 0; }
.join-cmd-code code {
  background: #f5f7fa; padding: 6px 10px; border-radius: 4px;
  font-size: 12px; word-break: break-all; color: #476582;
}
</style>
