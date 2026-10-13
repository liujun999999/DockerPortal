<template>
  <div class="node-detail">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadAll" @retry="loadAll" />

    <template v-else>
      <div class="page-header">
        <div class="title-box">
          <el-button text :icon="ArrowLeft" @click="router.push('/swarm/nodes')">返回</el-button>
          <h2>{{ hostname }}</h2>
          <StatusTag v-if="node?.Spec?.Role" :status="node.Spec.Role" :status-map="NODE_ROLES" />
          <StatusTag v-if="node?.Status?.State" :status="node.Status.State" :status-map="NODE_STATUS" />
          <StatusTag v-if="node?.Spec?.Availability" :status="node.Spec.Availability" :status-map="NODE_AVAILABILITY" />
          <el-tag v-if="node?.ManagerStatus?.Leader" type="danger" size="small" effect="dark">Leader</el-tag>
        </div>
        <div class="header-actions" v-if="isAdmin">
          <el-button @click="loadAll" :icon="Refresh">刷新</el-button>
          <el-button @click="changeAvailability">
            {{ node?.Spec?.Availability === 'drain' ? '激活节点' : '排空节点' }}
          </el-button>
          <el-button type="primary" :icon="PriceTag" @click="openLabelEditor">编辑标签</el-button>
          <el-button type="danger" plain @click="doRemove">移除节点</el-button>
        </div>
      </div>

      <el-card shadow="never" v-loading="loading">
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <!-- ==================== 概览 ==================== -->
          <el-tab-pane label="概览" name="overview">
            <template v-if="node">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="节点 ID" :span="2"><code>{{ node.ID }}</code></el-descriptions-item>
                <el-descriptions-item label="主机名">{{ node.Description?.Hostname || '-' }}</el-descriptions-item>
                <el-descriptions-item label="节点名称">{{ node.Spec?.Name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="地址">
                  <code>{{ node.Status?.Addr || '-' }}</code>
                </el-descriptions-item>
                <el-descriptions-item label="角色">{{ node.Spec?.Role || '-' }}
                  <span v-if="node.ManagerStatus" class="dim">
                    （Manager 可达性：{{ node.ManagerStatus.Reachability || '未知' }}）
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="状态">{{ node.Status?.State || '-' }}</el-descriptions-item>
                <el-descriptions-item label="可用性">{{ node.Spec?.Availability || '-' }}</el-descriptions-item>
                <el-descriptions-item label="加入时间">{{ formatDateTime(node.CreatedAt) }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatDateTime(node.UpdatedAt) }}</el-descriptions-item>
              </el-descriptions>

              <h4 class="sec-title">硬件与引擎</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="操作系统">
                  {{ node.Description?.Platform?.OS || '-' }} / {{ node.Description?.Platform?.Architecture || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="CPU">{{ cpuText }}</el-descriptions-item>
                <el-descriptions-item label="内存">
                  {{ memText }}
                  <!-- Docker Desktop / 云主机上 daemon 报的是虚拟机可见内存，与物理内存不是同一个数 -->
                  <div class="dim">daemon 上报值；虚拟机 / Docker Desktop 下为 guest 可见内存，可能小于机器物理内存</div>
                </el-descriptions-item>
                <el-descriptions-item label="Docker 版本">{{ node.Description?.Engine?.EngineVersion || '-' }}</el-descriptions-item>
                <el-descriptions-item label="插件" :span="2">
                  <template v-if="node.Description?.Engine?.Plugins?.length">
                    <el-tag v-for="p in node.Description.Engine.Plugins" :key="`${p.Type}-${p.Name}`"
                      size="small" effect="plain" class="plugin-tag">
                      {{ p.Type }}: {{ p.Name }}
                    </el-tag>
                  </template>
                  <span v-else class="dim">-</span>
                </el-descriptions-item>
                <el-descriptions-item label="引擎标签" :span="2">
                  <div v-if="engineLabelList.length" class="engine-labels">
                    <code v-for="l in engineLabelList" :key="l.k" class="engine-label">{{ l.k }}={{ l.v }}</code>
                  </div>
                  <span v-else class="dim">无</span>
                  <!-- dockerd 自己的标签和 swarm 节点标签是两码事，容易看错，这里点明 -->
                  <div class="dim">引擎标签来自 dockerd 启动配置，只读；参与调度的是「标签」页签里的节点标签。</div>
                </el-descriptions-item>
              </el-descriptions>

              <h4 class="sec-title">节点标签</h4>
              <div v-if="labelList.length" class="label-tags">
                <el-tag v-for="l in labelList" :key="l.k" size="small" effect="plain" class="label-tag">
                  {{ l.v === '' ? l.k : `${l.k}=${l.v}` }}
                </el-tag>
              </div>
              <span v-else class="dim">无标签（可在「标签」页签里添加，用于服务调度约束）</span>
              <div v-if="isAdmin" style="margin-top:8px">
                <el-button size="small" @click="openLabelEditor">编辑标签</el-button>
              </div>

              <el-collapse style="margin-top:12px">
                <el-collapse-item title="原始 JSON（完整 inspect 数据）" name="raw">
                  <pre class="raw-json">{{ JSON.stringify(node, null, 2) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </template>
          </el-tab-pane>

          <!-- ==================== 标签 ==================== -->
          <el-tab-pane label="标签" name="labels">
            <div class="bar">
              <span class="dim">
                节点标签是 Swarm 调度器的输入，服务通过 <code>node.labels.&lt;key&gt;==&lt;value&gt;</code> 约束指定落点。
              </span>
              <el-button v-if="isAdmin" size="small" type="primary" :icon="Edit" @click="openLabelEditor">编辑标签</el-button>
            </div>
            <el-table :data="labelList" stripe size="small">
              <el-table-column label="Key" prop="k" min-width="220" />
              <el-table-column label="Value" prop="v" min-width="260">
                <template #default="{ row }">
                  <span v-if="row.v">{{ row.v }}</span>
                  <span v-else class="dim">（空值）</span>
                </template>
              </el-table-column>
              <el-table-column label="调度约束写法" min-width="300">
                <template #default="{ row }">
                  <code>{{ `node.labels.${row.k}${row.v ? '==' + row.v : ''}` }}</code>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!labelList.length" description="该节点没有标签" :image-size="80" />
          </el-tab-pane>

          <!-- ==================== 任务 ==================== -->
          <el-tab-pane label="任务" name="tasks">
            <div v-if="tasks.length" class="tasks-bar">
              <el-radio-group v-model="taskStateFilter" size="small">
                <el-radio-button value="">全部 {{ tasks.length }}</el-radio-button>
                <el-radio-button v-for="s in taskStateOptions" :key="s.value" :value="s.value">
                  {{ s.label }} {{ s.count }}
                </el-radio-button>
              </el-radio-group>
              <span v-if="taskStateFilter" class="dim">
                匹配 {{ filteredTasks.length }} / {{ tasks.length }}
                <el-link type="primary" :underline="false" style="margin-left: 6px; font-size: 12px"
                  @click="taskStateFilter = ''">清除过滤</el-link>
              </span>
              <span v-else class="dim">含已结束与被拒绝的历史任务，便于排查这个节点上的副本为什么没起来</span>
            </div>
            <el-table :data="filteredTasks" stripe size="small" v-loading="tasksLoading">
              <el-table-column label="ID" width="130">
                <template #default="{ row }"><code>{{ row.ID?.substring(0, 12) }}</code></template>
              </el-table-column>
              <el-table-column label="服务" width="180">
                <template #default="{ row }">
                  <el-link v-if="serviceName(row.ServiceID)" type="primary" :underline="false"
                    @click="router.push(`/swarm/services/${row.ServiceID}`)">
                    {{ serviceName(row.ServiceID) }}
                  </el-link>
                  <span v-else>{{ row.ServiceID?.substring(0, 12) || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="副本" width="70">
                <template #default="{ row }">#{{ row.Slot ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <StatusTag :status="row.Status?.State || 'unknown'" :status-map="TASK_STATES" />
                </template>
              </el-table-column>
              <el-table-column label="Desired" width="100">
                <template #default="{ row }">{{ row.DesiredState || '-' }}</template>
              </el-table-column>
              <el-table-column label="镜像" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.Spec?.ContainerSpec?.Image || '-' }}</template>
              </el-table-column>
              <el-table-column label="错误" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.Status?.Err || row.Status?.Message || '-' }}</template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.UpdatedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!tasksLoading && !tasks.length" description="该节点上没有任务记录" :image-size="80" />
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- 标签编辑对话框 -->
      <el-dialog v-model="labelVisible" title="编辑节点标签" width="700px">
        <!-- daemon 侧是整体替换语义，漏写即删除，必须提前讲清楚 -->
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
          title="保存时用这份列表整体替换节点现有的标签：没有列在这里的标签会被删除。" />
        <div v-for="(row, i) in labelDraft" :key="i" class="label-row">
          <el-input v-model="row.k" placeholder="key，例如 disk" />
          <span class="eq">=</span>
          <el-input v-model="row.v" placeholder="value，可留空" />
          <el-button text type="danger" :icon="Delete" @click="labelDraft.splice(i, 1)" />
        </div>
        <div v-if="!labelDraft.length" class="dim empty-tip">当前没有标签行。</div>
        <el-button text type="primary" :icon="Plus" @click="labelDraft.push({ k: '', v: '' })">添加一项</el-button>
        <template #footer>
          <el-button @click="labelVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveLabels">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh, PriceTag, Edit, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNode, getNodeTasks, updateNode, removeNode as apiRemoveNode } from '@/api/node'
import { getServices } from '@/api/service'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { requireTypedConfirm } from '@/utils/danger'
import { formatBytes, formatDateTime } from '@/utils/format'
import { NODE_AVAILABILITY, NODE_ROLES, NODE_STATUS, TASK_STATES } from '@/utils/constants'
import StatusTag from '@/components/common/StatusTag.vue'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const route = useRoute()
const router = useRouter()
const envStore = useEnvironmentStore()
const { isAdmin } = usePermission()

const nodeId = route.params.id as string

const node = ref<any>(null)
const loading = ref(false)
const guideCode = ref(0)
const activeTab = ref('overview')

const tasks = ref<any[]>([])
const tasksLoading = ref(false)
const taskStateFilter = ref('')
const services = ref<any[]>([])

const labelVisible = ref(false)
const saving = ref(false)
const labelDraft = ref<{ k: string; v: string }[]>([])

const hostname = computed(
  () => node.value?.Description?.Hostname || node.value?.Spec?.Name || nodeId.substring(0, 12))

const toList = (m?: Record<string, string>) =>
  Object.entries(m || {}).map(([k, v]) => ({ k, v: String(v) }))

const labelList = computed(() => toList(node.value?.Spec?.Labels))
const engineLabelList = computed(() => toList(node.value?.Description?.Engine?.Labels))

// NanoCPUs 是 1e-9 核；MemoryBytes 直接按字节换算
const cpuText = computed(() => {
  const n = node.value?.Description?.Resources?.NanoCPUs
  return n ? `${(n / 1e9).toFixed(n % 1e9 === 0 ? 0 : 2)} 核` : '-'
})
// 这里不能取整：8GB 虚拟机实际可用 7.748GiB，显示成“8 GB”会让人以为统计错机器内存
const memText = computed(() => {
  const m = node.value?.Description?.Resources?.MemoryBytes
  return m ? formatBytes(m, 2) : '-'
})

const taskStateOptions = computed(() => {
  const counts = new Map<string, number>()
  tasks.value.forEach((t) => {
    const state = t.Status?.State || 'unknown'
    counts.set(state, (counts.get(state) || 0) + 1)
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([value, count]) => ({
      value,
      count,
      label: (TASK_STATES as Record<string, { label: string }>)[value]?.label || value,
    }))
})

const filteredTasks = computed(() =>
  taskStateFilter.value
    ? tasks.value.filter((t) => (t.Status?.State || 'unknown') === taskStateFilter.value)
    : tasks.value)

function serviceName(id?: string) {
  if (!id) return ''
  return services.value.find((s) => s.ID === id)?.Spec?.Name || ''
}

async function loadNode() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    node.value = await getNode(envId, nodeId)
    guideCode.value = 0
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
      node.value = null
    } else {
      ElMessage.error(e?.message || '加载节点信息失败')
    }
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  const envId = envStore.currentEnvId
  if (!envId) return
  tasksLoading.value = true
  try {
    const [list, svcs] = await Promise.all([getNodeTasks(envId, nodeId), getServices(envId)])
    // 按更新时间倒序，最近的变化排在最前
    tasks.value = (list || []).sort((a: any, b: any) => String(b.UpdatedAt).localeCompare(String(a.UpdatedAt)))
    services.value = svcs || []
  } catch (e: any) {
    if (!e?.swarmGuide) console.error(e)
    tasks.value = []
  } finally {
    // 刷新后原过滤状态可能已不存在（失败副本被重建），别留下一张空表
    if (taskStateFilter.value
      && !tasks.value.some((t) => (t.Status?.State || 'unknown') === taskStateFilter.value)) {
      taskStateFilter.value = ''
    }
    tasksLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'tasks' && !tasks.value.length) loadTasks()
}

function loadAll() {
  loadNode()
  if (activeTab.value === 'tasks') loadTasks()
}

function openLabelEditor() {
  // 从当前值起步，避免漏带原有标签被顺手删掉
  labelDraft.value = labelList.value.map((l) => ({ k: l.k, v: l.v }))
  labelVisible.value = true
}

async function saveLabels() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const labels: Record<string, string> = {}
  for (const row of labelDraft.value) {
    const k = (row.k || '').trim()
    if (!k) {
      ElMessage.warning('标签 key 不能为空，空行请直接删除')
      return
    }
    if (labels[k] !== undefined) {
      ElMessage.warning(`标签 key 重复：${k}`)
      return
    }
    labels[k] = (row.v || '').trim()
  }
  saving.value = true
  try {
    await updateNode(envId, nodeId, { labels })
    ElMessage.success('节点标签已保存')
    labelVisible.value = false
    loadNode()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存标签失败')
  } finally {
    saving.value = false
  }
}

async function changeAvailability() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const draining = node.value?.Spec?.Availability !== 'drain'
  if (draining) {
    try {
      await ElMessageBox.confirm(
        `排空「${hostname.value}」会把它标记为不可调度，并已在上面的服务副本重新调度到其他节点。确认继续？`,
        '排空节点', { type: 'warning', confirmButtonText: '排空', cancelButtonText: '取消' },
      )
    } catch { return }
  }
  try {
    // 只传 availability：labels 不带就不参与本次更新，避免误覆盖标签
    await updateNode(envId, nodeId, { availability: draining ? 'drain' : 'active' })
    ElMessage.success(draining ? '节点已排空' : '节点已激活')
    loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function doRemove() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const isManager = node.value?.Spec?.Role === 'manager'
  try {
    await requireTypedConfirm({
      title: '移除节点',
      message: isManager
        ? `"${hostname.value}" 是 Manager 节点，移除会缩减集群控制面，若剩余 Manager 不足法定票数整个 Swarm 将无法写入。请输入主机名以确认。`
        : `移除后「${hostname.value}」将退出 Swarm 集群，其上的副本会被重新调度。请输入主机名以确认。`,
      requireText: hostname.value,
    })
  } catch { return }
  try {
    await apiRemoveNode(envId, nodeId, true)
    ElMessage.success('节点已移出集群')
    router.push('/swarm/nodes')
  } catch (e: any) {
    ElMessage.error(e?.message || '移除失败')
  }
}

onMounted(loadNode)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-box { display: flex; align-items: center; gap: 10px; }
.title-box h2 { font-size: 18px; font-weight: 600; margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.sec-title { margin: 18px 0 10px; font-size: 14px; font-weight: 600; color: #303133; }
.raw-json { background: #f5f7fa; padding: 12px; border-radius: 4px; max-height: 420px; overflow: auto; font-size: 12px; }
.dim { font-size: 12px; color: #909399; }
.bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; }
.tasks-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.label-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.label-tag { margin: 0; }
.plugin-tag { margin-right: 6px; }
.engine-labels { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 4px; }
.engine-label { background: #f5f7fa; padding: 1px 6px; border-radius: 3px; }
.label-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.label-row .eq { color: #909399; }
.empty-tip { margin-bottom: 8px; }
</style>
