<template>
  <div class="service-detail" v-loading="loading">
    <div class="page-header">
      <div class="title-box">
        <el-button text :icon="ArrowLeft" @click="router.push('/swarm/services')">返回</el-button>
        <h2>{{ service?.Spec?.Name || route.params.id }}</h2>
        <el-tag v-if="service?.Spec?.Mode?.Global" type="warning" size="small" effect="plain">Global</el-tag>
        <el-tag v-else size="small" effect="plain">{{ service?.Spec?.Mode?.Replicated?.Replicas ?? 0 }} 副本</el-tag>
        <el-tag v-if="runningTasks !== null" :type="runningTasks > 0 ? 'success' : 'info'" size="small">
          运行中任务 {{ runningTasks }}
        </el-tag>
      </div>
      <div class="header-actions" v-if="canOperate">
        <el-button @click="loadAll" :icon="Refresh">刷新</el-button>
        <el-button @click="openScale">扩缩容</el-button>
        <el-button type="primary" @click="router.push(`/swarm/services/${route.params.id}/edit`)">编辑</el-button>
        <el-button type="danger" @click="doRemove">删除</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ==================== 概览 ==================== -->
        <el-tab-pane label="概览" name="overview">
          <template v-if="service">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="服务 ID" :span="2"><code>{{ service.ID }}</code></el-descriptions-item>
              <el-descriptions-item label="镜像" :span="2">
                <code>{{ service.Spec?.TaskTemplate?.ContainerSpec?.Image || '-' }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(service.CreatedAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(service.UpdatedAt) }}</el-descriptions-item>
              <el-descriptions-item label="部署模式">
                {{ service.Spec?.Mode?.Global ? 'Global（每节点一个）' : `Replicated（${service.Spec?.Mode?.Replicated?.Replicas ?? 0} 副本）` }}
              </el-descriptions-item>
              <el-descriptions-item label="发布模式">{{ service.Spec?.EndpointSpec?.Mode || 'vip' }}</el-descriptions-item>
              <el-descriptions-item label="发布端口" :span="2">
                <template v-if="service.Endpoint?.Ports?.length || service.Spec?.EndpointSpec?.Ports?.length">
                  <el-tag v-for="p in (service.Spec?.EndpointSpec?.Ports || service.Endpoint?.Ports)" :key="p.PublishedPort"
                    size="small" style="margin-right:6px">
                    {{ p.PublishedPort }}→{{ p.TargetPort }}/{{ (p.Protocol || 'tcp').toLowerCase() }}
                  </el-tag>
                </template>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="网络" :span="2">
                <template v-if="service.Spec?.Networks?.length">
                  <el-tag v-for="n in service.Spec.Networks" :key="n.Target" size="small" style="margin-right:6px">{{ n.Target }}</el-tag>
                </template>
                <span v-else>default</span>
              </el-descriptions-item>
              <el-descriptions-item label="资源限制" :span="2">
                <span v-if="resourcesText">{{ resourcesText }}</span>
                <span v-else>未设置</span>
              </el-descriptions-item>
              <el-descriptions-item label="健康检查" :span="2">
                <template v-if="healthcheck">
                  <code>{{ healthcheck.Test.join(' ') }}</code>
                  <span class="hc-meta">{{ healthcheckMeta }}</span>
                </template>
                <span v-else>未启用</span>
              </el-descriptions-item>
              <el-descriptions-item label="重启策略" :span="2">
                {{ service.Spec?.TaskTemplate?.RestartPolicy?.Condition || '默认' }}
                <span v-if="service.Spec?.TaskTemplate?.RestartPolicy?.Delay">
                  （延迟 {{ Math.round(service.Spec.TaskTemplate.RestartPolicy.Delay / 1e9) }}s）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="滚动更新" :span="2">
                并行 {{ service.Spec?.UpdateConfig?.Parallelism ?? '-' }}，延迟
                {{ service.Spec?.UpdateConfig?.Delay ? Math.round(service.Spec.UpdateConfig.Delay / 1e9) + 's' : '0s' }}
                <span v-if="service.Spec?.UpdateConfig?.FailureAction">，失败 {{ service.Spec.UpdateConfig.FailureAction }}</span>
                <span v-if="service.Spec?.UpdateConfig?.Order">，顺序 {{ service.Spec.UpdateConfig.Order }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="调度约束" :span="2">
                <div v-if="service.Spec?.TaskTemplate?.Placement?.Constraints?.length">
                  <code v-for="c in service.Spec.TaskTemplate.Placement.Constraints" :key="c" style="display:block">{{ c }}</code>
                </div>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>

            <h4 class="sec-title">容器配置</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="Entrypoint">
                <code>{{ joinArr(service.Spec?.TaskTemplate?.ContainerSpec?.Command) || '镜像默认' }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="Command">
                <code>{{ joinArr(service.Spec?.TaskTemplate?.ContainerSpec?.Args) || '镜像默认' }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="工作目录 / 用户">
                {{ service.Spec?.TaskTemplate?.ContainerSpec?.Dir || '-' }} /
                {{ service.Spec?.TaskTemplate?.ContainerSpec?.User || 'root' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="envList.length" label="环境变量">
                <div v-for="e in envList" :key="e.k"><code>{{ e.k }}={{ e.v }}</code></div>
              </el-descriptions-item>
              <el-descriptions-item v-if="mountList.length" label="挂载">
                <div v-for="m in mountList" :key="m.target">
                  <code>{{ m.type }}: {{ m.source || '(匿名)' }} → {{ m.target }}{{ m.readOnly ? ' (ro)' : '' }}</code>
                </div>
              </el-descriptions-item>
              <el-descriptions-item v-if="secretList.length" label="Secrets">
                <div v-for="s in secretList" :key="s.id"><code>{{ s.name || s.id }} → {{ s.target }}</code></div>
              </el-descriptions-item>
              <el-descriptions-item v-if="configList.length" label="Configs">
                <div v-for="c in configList" :key="c.id"><code>{{ c.name || c.id }} → {{ c.target }}</code></div>
              </el-descriptions-item>
              <el-descriptions-item v-if="labelList.length" label="标签">
                <div v-for="l in labelList" :key="l.k"><code>{{ l.k }} = {{ l.v }}</code></div>
              </el-descriptions-item>
            </el-descriptions>

            <el-collapse style="margin-top:12px">
              <el-collapse-item title="原始 JSON（完整 inspect 数据）" name="raw">
                <pre class="raw-json">{{ JSON.stringify(service, null, 2) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </template>
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
            <span v-if="taskStateFilter" class="bar-hint">
              匹配 {{ filteredTasks.length }} / {{ tasks.length }}
              <el-link type="primary" :underline="false" style="margin-left: 6px; font-size: 12px"
                @click="taskStateFilter = ''">清除过滤</el-link>
            </span>
          </div>
          <el-table :data="filteredTasks" stripe size="small" v-loading="tasksLoading">
            <el-table-column label="ID" width="130">
              <template #default="{ row }"><code>{{ row.ID?.substring(0, 12) }}</code></template>
            </el-table-column>
            <el-table-column label="节点" width="140">
              <template #default="{ row }">{{ row.NodeID?.substring(0, 12) || '-' }}</template>
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
            <el-table-column label="错误" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.Status?.Err || row.Status?.Message || '-' }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.UpdatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary"
                  :disabled="!row.Status?.ContainerStatus?.ContainerID"
                  @click="viewTaskLogs(row)">查看日志</el-button>
                <el-button size="small" text type="primary" :loading="terminalLoading === row.ID"
                  :disabled="row.Status?.State !== 'running' || !row.Status?.ContainerStatus?.ContainerID"
                  @click="openTaskTerminal(row)">进入终端</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ==================== 日志 ==================== -->
        <el-tab-pane label="日志" name="logs">
          <div ref="logsPanelRef" class="panel" :class="{ 'is-fs': logsFullscreen }">
            <div class="logs-bar">
              <span class="lbl">副本</span>
              <el-select v-model="selectedTask" style="width:300px" @change="loadLogs">
                <el-option label="全部副本（聚合）" value="" />
                <el-option v-for="t in taskLogOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <span class="lbl">尾部行数</span>
              <el-input-number v-model="logTail" :min="10" :max="5000" :step="100" style="width:130px" />
              <el-button :loading="logsLoading" @click="loadLogs">刷新日志</el-button>
              <el-button class="fs-btn" :icon="FullScreen" @click="toggleLogsFullscreen">
                {{ logsFullscreen ? '退出全屏' : '全屏' }}
              </el-button>
            </div>
            <el-alert v-if="!selectedTask" type="info" :closable="false" show-icon style="margin-bottom:8px"
              title="当前为全部副本聚合日志（docker service logs），行首带 task 标识；如需只看单个副本请在上方选择。" />
            <pre class="logs-pre" v-loading="logsLoading">{{ logsText || '（暂无日志）' }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 扩缩容对话框 -->
    <el-dialog v-model="scaleVisible" title="扩缩容" width="400px">
      <el-form label-width="80px">
        <el-form-item label="服务">{{ service?.Spec?.Name }}</el-form-item>
        <el-form-item label="副本数">
          <el-input-number v-model="scaleReplicas" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scaleVisible = false">取消</el-button>
        <el-button type="primary" :loading="scaling" @click="doScale">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh, ArrowLeft, FullScreen } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getService, getServiceTasks, getServiceLogs, getTaskLogs, getTaskExecTarget, removeService as apiRemove, scaleService as apiScale } from '@/api/service'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { useFullscreen } from '@/composables/useFullscreen'
import { requireTypedConfirm } from '@/utils/danger'
import { formatDateTime } from '@/utils/format'
import { TASK_STATES } from '@/utils/constants'
import StatusTag from '@/components/common/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const envStore = useEnvironmentStore()
const { canOperate, isAdmin } = usePermission()

const service = ref<any>(null)
const tasks = ref<any[]>([])
const loading = ref(false)
const tasksLoading = ref(false)
const activeTab = ref('overview')

const logsText = ref('')
const logsLoading = ref(false)
const logTail = ref(200)
const selectedTask = ref('')
const logsPanelRef = ref<HTMLElement>()
const { isFullscreen: logsFullscreen, toggle: toggleLogsFullscreen } = useFullscreen(logsPanelRef)

// 任务状态过滤：'' 为全部。状态取值见 TASK_STATES，实际出现过的才列入（按任务数降序）
const taskStateFilter = ref('')

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

// 有容器 ID 的 task 才能单独取日志；运行中副本排前面。选中值为 task ID（daemon 按 task 定位节点取日志）
const taskLogOptions = computed(() =>
  tasks.value
    .filter((t) => t.Status?.ContainerStatus?.ContainerID)
    .map((t) => ({
      value: t.ID as string,
      label: `副本 #${t.Slot ?? '-'} · ${(t.ID || '').substring(0, 12)} · ${t.Status?.State || ''}`,
      running: t.Status?.State === 'running' ? 0 : 1,
    }))
    .sort((a, b) => a.running - b.running))

const scaleVisible = ref(false)
const scaling = ref(false)
const scaleReplicas = ref(1)
const terminalLoading = ref('')

const runningTasks = computed(() => {
  if (!tasks.value.length) return tasks.value.length ? 0 : null
  return tasks.value.filter((t) => t.Status?.State === 'running' && t.DesiredState === 'running').length
})

const envList = computed(() => {
  const env = service.value?.Spec?.TaskTemplate?.ContainerSpec?.Env || []
  return env.map((e: string) => {
    const i = e.indexOf('='); return i < 0 ? { k: e, v: '' } : { k: e.slice(0, i), v: e.slice(i + 1) }
  })
})
const mountList = computed(() => service.value?.Spec?.TaskTemplate?.ContainerSpec?.Mounts || [])
const secretList = computed(() =>
  (service.value?.Spec?.TaskTemplate?.ContainerSpec?.Secrets || []).map((s: any) => ({
    id: s.SecretID || s.Secret?.ID || '', name: s.SecretName || '', target: s.File?.Name || s.FileName || '-',
  })))
const configList = computed(() =>
  (service.value?.Spec?.TaskTemplate?.ContainerSpec?.Configs || []).map((c: any) => ({
    id: c.ConfigID || c.Config?.ID || '', name: c.ConfigName || '', target: c.File?.Name || c.FileName || '-',
  })))
const labelList = computed(() =>
  Object.entries(service.value?.Spec?.TaskTemplate?.ContainerSpec?.Labels || {}).map(([k, v]) => ({ k, v: String(v) })))
const resourcesText = computed(() => {
  const rr = service.value?.Spec?.TaskTemplate?.Resources
  if (!rr) return ''
  const parts: string[] = []
  if (rr.Limits?.NanoCPUs) parts.push(`CPU 上限 ${(rr.Limits.NanoCPUs / 1e9).toFixed(2)} 核`)
  if (rr.Limits?.MemoryBytes) parts.push(`内存上限 ${Math.round(rr.Limits.MemoryBytes / 1024 / 1024)}MB`)
  if (rr.Reservations?.NanoCPUs) parts.push(`CPU 保留 ${(rr.Reservations.NanoCPUs / 1e9).toFixed(2)} 核`)
  if (rr.Reservations?.MemoryBytes) parts.push(`内存保留 ${Math.round(rr.Reservations.MemoryBytes / 1024 / 1024)}MB`)
  return parts.join('，')
})

function joinArr(a?: string[]) { return a && a.length ? a.join(' ') : '' }

/** docker-java 的 ContainerSpec 字段名是 Healthcheck（小写 c），不是 HealthCheck */
const healthcheck = computed(() => {
  const hc = service.value?.Spec?.TaskTemplate?.ContainerSpec?.Healthcheck
  if (!hc || !Array.isArray(hc.Test) || !hc.Test.length || hc.Test[0] === 'NONE') return null
  return hc
})

const healthcheckMeta = computed(() => {
  const hc = healthcheck.value
  if (!hc) return ''
  const sec = (ns?: number) => (ns ? Math.round(ns / 1e9) : 0)
  const parts: string[] = []
  if (sec(hc.Interval)) parts.push(`间隔 ${sec(hc.Interval)}s`)
  if (sec(hc.Timeout)) parts.push(`超时 ${sec(hc.Timeout)}s`)
  if (sec(hc.StartPeriod)) parts.push(`启动宽限 ${sec(hc.StartPeriod)}s`)
  if (hc.Retries) parts.push(`重试 ${hc.Retries} 次`)
  return parts.length ? `（${parts.join('，')}）` : ''
})

async function loadService() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    service.value = await getService(envId, route.params.id as string)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载服务失败')
  } finally { loading.value = false }
}

async function loadTasks() {
  const envId = envStore.currentEnvId
  if (!envId) return
  tasksLoading.value = true
  try {
    tasks.value = (await getServiceTasks(envId, route.params.id as string)) || []
  } catch (e: any) {
    if (!e?.swarmGuide) console.error(e)
    tasks.value = []
  } finally {
    // 刷新后原过滤状态可能已不存在（如失败副本被 Swarm 重建），避免留下一张空表
    if (taskStateFilter.value
      && !tasks.value.some((t) => (t.Status?.State || 'unknown') === taskStateFilter.value)) {
      taskStateFilter.value = ''
    }
    tasksLoading.value = false
  }
}

async function loadLogs() {
  const envId = envStore.currentEnvId
  if (!envId) return
  logsLoading.value = true
  try {
    const r: any = selectedTask.value
      ? await getTaskLogs(envId, selectedTask.value, logTail.value)
      : await getServiceLogs(envId, route.params.id as string, logTail.value)
    logsText.value = typeof r === 'string' ? r : Array.isArray(r) ? r.join('\n') : (r?.logs ?? JSON.stringify(r))
  } catch (e: any) {
    logsText.value = ''
    ElMessage.error(e?.message || '读取日志失败')
  } finally { logsLoading.value = false }
}

// 从任务表点「查看日志」：选中该副本（task）并切到日志 tab
function viewTaskLogs(row: any) {
  if (!row?.Status?.ContainerStatus?.ContainerID) { ElMessage.warning('该副本没有可用的容器日志'); return }
  if (!tasks.value.length) loadTasks()
  selectedTask.value = row.ID
  activeTab.value = 'logs'
  loadLogs()
}

/**
 * 进入副本容器终端。docker exec 只能在 daemon 本地执行，所以要先探测：
 * 容器在当前连接的节点 → 走原生容器终端；否则只能 SSH 到副本所在节点执行 docker exec
 */
async function openTaskTerminal(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  const cid = row?.Status?.ContainerStatus?.ContainerID
  if (!cid) { ElMessage.warning('该副本没有可用容器，无法进入终端'); return }
  // 探测是异步的，而浏览器只允许同步用户手势内 window.open，先占一个空标签页再导航
  const draft = window.open('', '_blank')
  terminalLoading.value = row.ID
  try {
    const t: any = await getTaskExecTarget(envId, row.ID)
    if (t.local) {
      navigate(draft, router.resolve(`/containers/${t.containerId || cid}/console`).href)
      return
    }
    // SSH 终端后端限制为 ADMIN，提前拦住，避免开一个直接被拒的标签页
    if (!isAdmin.value) {
      closeDraft(draft)
      ElMessage.warning('该副本不在当前连接的管理节点上，需经节点 SSH 进入容器，而 SSH 终端仅管理员可用')
      return
    }
    if (t.sshConfigured) {
      navigate(draft, router.resolve({ path: '/ssh', query: { node: t.nodeId, exec: cid } }).href)
      return
    }
    closeDraft(draft)
    const addr = t.nodeAddress ? `（${t.nodeAddress}）` : ''
    try {
      await ElMessageBox.confirm(
        `该副本跑在其他节点${addr}，当前连接的管理节点上没有这个容器，无法直接 docker exec。` +
        '需先为该节点配置 SSH 连接，之后才能进入容器终端。现在去配置？',
        '需要节点 SSH 凭据',
        { confirmButtonText: '去配置', cancelButtonText: '取消', type: 'warning' },
      )
    } catch { return }
    // 配 SSH 属于要来回切页面的操作，同页跳转更稳
    router.push({ path: '/ssh', query: { node: t.nodeId } })
  } catch (e: any) {
    closeDraft(draft)
    ElMessage.error(e?.message || '获取副本接入信息失败')
  } finally { terminalLoading.value = '' }
}

function closeDraft(draft: Window | null) {
  if (draft && !draft.closed) draft.close()
}

/** 弹窗被拦截时退化为同页跳转，不能静默失败 */
function navigate(draft: Window | null, href: string) {
  if (draft && !draft.closed) {
    draft.location.href = href
    return
  }
  router.push(href)
}

function onTabChange(name: string | number) {
  if (name === 'tasks' && !tasks.value.length) loadTasks()
  if (name === 'logs') {
    if (!tasks.value.length) loadTasks()
    if (!logsText.value) loadLogs()
  }
}

function loadAll() {
  loadService()
  loadTasks()
  if (activeTab.value === 'logs') loadLogs()
}

function openScale() {
  scaleReplicas.value = service.value?.Spec?.Mode?.Replicated?.Replicas || 1
  scaleVisible.value = true
}

async function doScale() {
  const envId = envStore.currentEnvId
  if (!envId) return
  scaling.value = true
  try {
    await apiScale(envId, route.params.id as string, scaleReplicas.value)
    ElMessage.success('扩缩容成功')
    scaleVisible.value = false
    loadAll()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') } finally { scaling.value = false }
}

async function doRemove() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const name = service.value?.Spec?.Name || (route.params.id as string)
  try {
    await requireTypedConfirm({
      title: '删除服务',
      message: `删除服务 "${name}" 将停止并移除其所有任务，不可恢复。请输入服务名以确认。`,
      requireText: name,
    })
  } catch { return }
  try {
    await apiRemove(envId, route.params.id as string)
    ElMessage.success('服务已删除')
    router.push('/swarm/services')
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(loadAll)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-box { display: flex; align-items: center; gap: 10px; }
.title-box h2 { font-size: 18px; font-weight: 600; margin: 0; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.sec-title { margin: 18px 0 10px; font-size: 14px; font-weight: 600; color: #303133; }
.raw-json { background: #f5f7fa; padding: 12px; border-radius: 4px; max-height: 420px; overflow: auto; font-size: 12px; }
.hc-meta { margin-left: 8px; font-size: 12px; color: #909399; }
.logs-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tasks-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.bar-hint { font-size: 12px; color: #909399; display: flex; align-items: center; }
.lbl { font-size: 13px; color: #606266; }
.logs-pre { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 4px; height: 480px; overflow: auto; font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }
/* 日志面板全屏：整块撑满视口，控件条留在顶部，日志区 flex 拉伸 */
.panel { display: flex; flex-direction: column; min-height: 0; }
.panel.is-fs { height: 100vh; box-sizing: border-box; padding: 12px 16px 16px; background: #f5f7fa; }
.panel.is-fs .logs-pre { flex: 1; height: auto; min-height: 0; }
.fs-btn { margin-left: auto; }
</style>
