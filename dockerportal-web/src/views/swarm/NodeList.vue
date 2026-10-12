<template>
  <div class="node-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Swarm 节点</span>
          <el-button @click="loadData" :icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="nodes" stripe v-loading="loading">
        <el-table-column label="ID" width="140">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">
              {{ row.ID?.substring(0, 12) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="主机名">
          <template #default="{ row }">
            {{ row.Description?.Hostname || '-' }}
            <el-tag v-if="row.ManagerStatus?.Leader" type="danger" size="small" effect="dark" class="mini-tag">Leader</el-tag>
            <el-tag v-else-if="row.ManagerStatus" type="warning" size="small" effect="plain" class="mini-tag">Manager</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP">
          <template #default="{ row }">{{ row.Status?.Addr || '-' }}</template>
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
        <el-table-column label="引擎版本" width="120">
          <template #default="{ row }">{{ row.Description?.Engine?.EngineVersion || '-' }}</template>
        </el-table-column>
        <!-- 节点标签是调度约束的输入，列表里先给个概览，具体增删到详情页做 -->
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <template v-if="labelEntries(row).length">
              <el-tag v-for="l in labelEntries(row).slice(0, 2)" :key="l.k" size="small" effect="plain" class="label-tag">
                {{ l.v === '' ? l.k : `${l.k}=${l.v}` }}
              </el-tag>
              <span v-if="labelEntries(row).length > 2" class="label-more">+{{ labelEntries(row).length - 2 }}</span>
            </template>
            <span v-else class="label-none">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" v-if="isAdmin">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button size="small" text type="warning" @click="changeAvailability(row)">
              {{ row.Spec?.Availability === 'drain' ? '激活' : '排空' }}
            </el-button>
            <el-popconfirm title="确认将此节点移出集群？" @confirm="removeNode(row.ID)">
              <template #reference>
                <el-button size="small" text type="danger">移除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNodes, updateNode, removeNode as apiRemoveNode } from '@/api/node'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { NODE_ROLES, NODE_STATUS, NODE_AVAILABILITY } from '@/utils/constants'
import StatusTag from '@/components/common/StatusTag.vue'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const envStore = useEnvironmentStore()
const router = useRouter()
const { isAdmin } = usePermission()
const nodes = ref<any[]>([])
const loading = ref(false)
const guideCode = ref(0)

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    nodes.value = await getNodes(envId)
    guideCode.value = 0
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
      nodes.value = []
    } else {
      console.error(e)
    }
  } finally {
    loading.value = false
  }
}

/** 节点详情做成独立页面而不是弹 JSON 窗：内容多、还要带标签编辑和任务列表 */
function openDetail(row: any) {
  router.push(`/swarm/nodes/${row.ID}`)
}

function labelEntries(row: any) {
  return Object.entries(row.Spec?.Labels || {}).map(([k, v]) => ({ k, v: String(v) }))
}

async function changeAvailability(node: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  const newAvail = node.Spec?.Availability === 'drain' ? 'active' : 'drain'
  try {
    await updateNode(envId, node.ID, { availability: newAvail })
    ElMessage.success('节点可用性已更新')
    loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function removeNode(nodeId: string) {
  const envId = envStore.currentEnvId
  if (!envId) return
  try {
    await apiRemoveNode(envId, nodeId, true)
    ElMessage.success('节点已删除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.mini-tag { margin-left: 6px; }
.label-tag { margin-right: 6px; }
.label-more, .label-none { font-size: 12px; color: #909399; }
</style>
