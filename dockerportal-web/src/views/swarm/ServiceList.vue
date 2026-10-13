<template>
  <div class="service-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Swarm 服务</span>
          <div>
            <el-button @click="loadData" :icon="Refresh">刷新</el-button>
            <el-button type="primary" @click="router.push('/swarm/services/create')" v-if="canOperate">创建服务</el-button>
          </div>
        </div>
      </template>
      <el-table :data="pageData" stripe v-loading="loading">
        <el-table-column label="名称" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/swarm/services/${row.ID}`)">{{ row.Spec?.Name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="镜像" min-width="200">
          <template #default="{ row }">{{ row.Spec?.TaskTemplate?.ContainerSpec?.Image || '-' }}</template>
        </el-table-column>
        <el-table-column label="副本（实际/期望）" width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.Spec?.Mode?.Global" content="Global 模式：每个可用节点各跑一个副本" placement="top">
              <el-tag size="small" type="warning" effect="plain">Global · 运行 {{ runningOf(row.ID) }}</el-tag>
            </el-tooltip>
            <el-tooltip v-else :content="replicaHint(row)" placement="top">
              <span class="replica">
                <span class="actual" :class="replicaClass(row)">{{ runningOf(row.ID) }}</span>
                <span class="sep">/</span>
                <span class="desired">{{ desiredOf(row) }}</span>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="端口" width="180">
          <template #default="{ row }">
            <span v-if="row.Endpoint?.Ports">
              <span v-for="p in row.Endpoint.Ports" :key="p.PublishedPort">
                {{ p.PublishedPort }}:{{ p.TargetPort }}/{{ p.Protocol }}
              </span>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.CreatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="router.push(`/swarm/services/${row.ID}`)">详情</el-button>
            <template v-if="canOperate">
              <el-button size="small" text type="primary" @click="router.push(`/swarm/services/${row.ID}/edit`)">编辑</el-button>
              <el-button size="small" text type="primary" @click="scaleService(row)">扩缩容</el-button>
              <el-button size="small" text type="danger" @click="removeService(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
    </el-card>

    <!-- 扩缩容对话框 -->
    <el-dialog v-model="scaleVisible" title="扩缩容" width="400px">
      <el-form label-width="80px">
        <el-form-item label="服务">{{ scalingService?.Spec?.Name }}</el-form-item>
        <el-form-item label="副本数">
          <el-input-number v-model="scaleReplicas" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scaleVisible = false">取消</el-button>
        <el-button type="primary" @click="doScale">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getServices, removeService as apiRemove, scaleService as apiScale } from '@/api/service'
import { getSwarmTasks } from '@/api/swarm'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { requireTypedConfirm } from '@/utils/danger'
import { formatDateTime } from '@/utils/format'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const router = useRouter()
const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const services = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(services)
const tasks = ref<any[]>([])
const loading = ref(false)
const guideCode = ref(0)

const scaleVisible = ref(false)
const scalingService = ref<any>(null)
const scaleReplicas = ref(1)

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    services.value = await getServices(envId)
    guideCode.value = 0
    // 一次拉取集群全部任务，用于统计各服务实际运行副本数；失败不阻断列表
    tasks.value = await getSwarmTasks(envId).catch(() => [])
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
      services.value = []
      tasks.value = []
    } else {
      console.error(e)
    }
  }
  finally { loading.value = false }
}

/** 实际运行副本数：与详情页口径一致（desired=running 且 status=running） */
function runningOf(serviceId: string) {
  return tasks.value.filter(
    (t) => t.ServiceID === serviceId && t.DesiredState === 'running' && t.Status?.State === 'running',
  ).length
}

function desiredOf(row: any) {
  return row.Spec?.Mode?.Replicated?.Replicas ?? 0
}

function replicaClass(row: any) {
  const desired = desiredOf(row)
  const running = runningOf(row.ID)
  if (desired === 0) return 'scaled-to-zero'
  if (running >= desired) return 'converged'
  return 'not-converged'
}

function replicaHint(row: any) {
  const desired = desiredOf(row)
  const running = runningOf(row.ID)
  if (desired === 0) return '期望副本数为 0（已缩容到零）'
  if (running >= desired) return `${running} 个副本全部运行中`
  return `期望 ${desired} 个，当前仅 ${running} 个运行中，可能有副本调度失败或正在启动`
}

function scaleService(row: any) {
  scalingService.value = row
  scaleReplicas.value = row.Spec?.Mode?.Replicated?.Replicas || 1
  scaleVisible.value = true
}

async function doScale() {
  const envId = envStore.currentEnvId
  if (!envId || !scalingService.value) return
  try {
    await apiScale(envId, scalingService.value.ID, scaleReplicas.value)
    ElMessage.success('扩缩容成功')
    scaleVisible.value = false
    loadData()
  } catch (e) { ElMessage.error('操作失败') }
}

async function removeService(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  const name = row?.Spec?.Name || row?.ID || ''
  // 删除服务会销毁其下所有任务，属 L2 危险操作：输入服务名确认
  try {
    await requireTypedConfirm({
      title: '删除服务',
      message: `删除服务 "${name}" 将停止并移除其所有任务，不可恢复。请输入服务名以确认。`,
      requireText: name,
    })
  } catch { return }
  try {
    await apiRemove(envId, row.ID)
    ElMessage.success('服务已删除')
    loadData()
  } catch (e) { ElMessage.error('删除失败') }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.replica { font-variant-numeric: tabular-nums; cursor: default; }
.replica .actual { font-weight: 600; }
.replica .actual.converged { color: #67c23a; }
.replica .actual.not-converged { color: #e6a23c; }
.replica .actual.scaled-to-zero { color: #909399; }
.replica .sep, .replica .desired { color: #909399; }
</style>
