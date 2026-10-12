<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>容器详情 - {{ displayName }}</h2>
      <div class="header-right">
        <el-tag v-if="info" :type="info.State?.Running ? 'success' : 'info'" style="margin-right: 12px">
          {{ info.State?.Status }}
        </el-tag>
        <el-button v-if="info" style="margin-right: 12px" @click="jsonVisible = true">Inspect JSON</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== 详细信息 ==================== -->
      <el-tab-pane label="详细信息" name="detail">
        <template v-if="info">
          <el-descriptions title="常规" :column="2" border size="small" class="section">
            <el-descriptions-item label="ID">{{ info.Id }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ displayName }}</el-descriptions-item>
            <el-descriptions-item label="镜像">{{ info.Config?.Image }}</el-descriptions-item>
            <el-descriptions-item label="镜像 ID">{{ (info.ImageID || '').replace('sha256:', '').slice(0, 19) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ info.Created }}</el-descriptions-item>
            <el-descriptions-item label="平台">{{ info.Platform }}</el-descriptions-item>
            <el-descriptions-item label="驱动">{{ info.Driver }}</el-descriptions-item>
            <el-descriptions-item label="重启次数">{{ info.RestartCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions title="运行状态 (State)" :column="2" border size="small" class="section">
            <el-descriptions-item label="状态">{{ info.State?.Status }}</el-descriptions-item>
            <el-descriptions-item label="运行中">
              <el-tag :type="info.State?.Running ? 'success' : 'danger'" size="small">{{ info.State?.Running }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已暂停">{{ info.State?.Paused }}</el-descriptions-item>
            <el-descriptions-item label="OOM 被杀">
              <el-tag :type="info.State?.OOMKilled ? 'danger' : 'success'" size="small">{{ info.State?.OOMKilled }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退出码">{{ info.State?.ExitCode }}</el-descriptions-item>
            <el-descriptions-item label="进程 PID">{{ info.State?.Pid }}</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ info.State?.StartedAt }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ info.State?.FinishedAt }}</el-descriptions-item>
            <el-descriptions-item label="错误" :span="2">{{ info.State?.Error || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions title="配置 (Config)" :column="2" border size="small" class="section">
            <el-descriptions-item label="主机名">{{ info.Config?.Hostname }}</el-descriptions-item>
            <el-descriptions-item label="用户">{{ info.Config?.User || '(默认)' }}</el-descriptions-item>
            <el-descriptions-item label="工作目录">{{ info.Config?.WorkingDir || '-' }}</el-descriptions-item>
            <el-descriptions-item label="TTY">{{ info.Config?.Tty ? 'true' : 'false' }}</el-descriptions-item>
            <el-descriptions-item label="入口点 (Entrypoint)" :span="2">
              <code>{{ joinList(info.Config?.Entrypoint) || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="命令 (Cmd)" :span="2">
              <code>{{ joinList(info.Config?.Cmd) || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="暴露端口" :span="2">
              <el-tag v-for="p in exposedPorts" :key="p" size="small" style="margin-right: 6px">{{ p }}</el-tag>
              <span v-if="!exposedPorts.length">-</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section" v-if="envList.length">
            <h4>环境变量 ({{ envList.length }})</h4>
            <el-table :data="envList" size="small" max-height="240" border>
              <el-table-column prop="key" label="变量" width="280" show-overflow-tooltip />
              <el-table-column prop="value" label="值" show-overflow-tooltip />
            </el-table>
          </div>

          <div class="section" v-if="labelList.length">
            <h4>标签 (Labels)</h4>
            <el-table :data="labelList" size="small" max-height="240" border>
              <el-table-column prop="key" label="Key" width="320" show-overflow-tooltip />
              <el-table-column prop="value" label="Value" show-overflow-tooltip />
            </el-table>
          </div>

          <div class="section" v-if="info.Mounts?.length">
            <h4>挂载 (Mounts)</h4>
            <el-table :data="info.Mounts" size="small" border>
              <el-table-column prop="Type" label="类型" width="100" />
              <el-table-column prop="Source" label="源路径" show-overflow-tooltip />
              <el-table-column prop="Destination" label="容器内路径" show-overflow-tooltip />
              <el-table-column prop="Mode" label="Mode" width="90" />
              <el-table-column label="读写" width="80">
                <template #default="{ row }">{{ row.RW ? '读写' : '只读' }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="section" v-if="networkList.length">
            <h4>网络 (Networks)</h4>
            <el-table :data="networkList" size="small" border>
              <el-table-column prop="name" label="网络" width="160" />
              <el-table-column prop="IPAddress" label="IP 地址" width="160" />
              <el-table-column prop="Gateway" label="网关" width="160" />
              <el-table-column prop="MacAddress" label="MAC" width="200" />
              <el-table-column label="别名" show-overflow-tooltip>
                <template #default="{ row }">{{ (row.Aliases || []).join(', ') || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="section" v-if="portList.length">
            <h4>端口映射</h4>
            <el-table :data="portList" size="small" border>
              <el-table-column prop="container" label="容器端口" width="140" />
              <el-table-column prop="hostIp" label="主机 IP" width="140" />
              <el-table-column prop="hostPort" label="主机端口" width="120" />
              <el-table-column prop="proto" label="协议" width="90" />
            </el-table>
          </div>

          <el-descriptions title="主机配置 (HostConfig)" :column="2" border size="small" class="section">
            <el-descriptions-item label="重启策略">{{ info.HostConfig?.RestartPolicy?.Name || 'no' }}{{ retrySuffix }}</el-descriptions-item>
            <el-descriptions-item label="网络模式">{{ info.HostConfig?.NetworkMode }}</el-descriptions-item>
            <el-descriptions-item label="内存限制">{{ fmtBytes(info.HostConfig?.Memory) }}</el-descriptions-item>
            <el-descriptions-item label="CPU 配额">{{ info.HostConfig?.NanoCpus ? (info.HostConfig.NanoCpus / 1e9) + ' 核' : (info.HostConfig?.CpuQuota || '不限') }}</el-descriptions-item>
            <el-descriptions-item label="特权模式">{{ info.HostConfig?.Privileged ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="只读根文件系统">{{ info.HostConfig?.ReadonlyRootfs ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="DNS">{{ (info.HostConfig?.Dns || []).join(', ') || '-' }}</el-descriptions-item>
            <el-descriptions-item label="IPC/PID 模式">{{ info.HostConfig?.IpcMode || '-' }} / {{ info.HostConfig?.PidMode || '默认' }}</el-descriptions-item>
            <el-descriptions-item label="Binds" :span="2">
              <code>{{ (info.HostConfig?.Binds || []).join('\n') || '-' }}</code>
            </el-descriptions-item>
          </el-descriptions>

          <el-collapse class="section">
            <el-collapse-item title="原始 JSON（完整 inspect 数据）" name="raw">
              <pre class="raw-json">{{ JSON.stringify(info, null, 2) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
      </el-tab-pane>

      <!-- ==================== 监控 ==================== -->
      <el-tab-pane label="监控" name="monitor" lazy>
        <div class="toolbar">
          <el-tag :type="monRunning ? 'success' : 'info'" size="small">
            {{ monRunning ? '实时采集中（每 3 秒）' : '容器未运行，暂停采集' }}
          </el-tag>
          <span v-if="monError" class="mon-error">采集异常：{{ monError }}</span>
          <el-button size="small" @click="clearMonitor">清空曲线</el-button>
        </div>

        <div class="mon-cards">
          <div class="mon-card">
            <div class="mon-label">CPU 使用率</div>
            <div class="mon-value">{{ monNow.cpu }}<span class="mon-unit">%</span></div>
          </div>
          <div class="mon-card">
            <div class="mon-label">内存</div>
            <div class="mon-value">{{ monNow.mem }}<span class="mon-unit">MB</span></div>
            <div class="mon-sub">/ {{ monNow.memLimit }} MB（{{ monNow.memPct }}%）</div>
          </div>
          <div class="mon-card">
            <div class="mon-label">网络 RX / TX</div>
            <div class="mon-value small">{{ monNow.netRx }} / {{ monNow.netTx }}</div>
            <div class="mon-sub">每秒</div>
          </div>
          <div class="mon-card">
            <div class="mon-label">磁盘读 / 写</div>
            <div class="mon-value small">{{ monNow.blkR }} / {{ monNow.blkW }}</div>
            <div class="mon-sub">每秒</div>
          </div>
        </div>

        <div class="mon-charts">
          <el-card shadow="never">
            <TrendChart title="CPU 使用率（%）" unit="%" :labels="monLabels" :height="190"
                        :series="[{ name: 'CPU', color: '#409EFF', data: monCpu }]" />
          </el-card>
          <el-card shadow="never">
            <TrendChart title="内存使用（MB）" unit="MB" :labels="monLabels" :height="190"
                        :series="[{ name: '内存', color: '#67C23A', data: monMem }]" />
          </el-card>
          <el-card shadow="never">
            <TrendChart title="网络吞吐（B/s）" unit="B/s" :labels="monLabels" :height="190"
                        :series="monNetSeries" />
          </el-card>
          <el-card shadow="never">
            <TrendChart title="磁盘 IO（B/s）" unit="B/s" :labels="monLabels" :height="190"
                        :series="monBlkSeries" />
          </el-card>
        </div>
      </el-tab-pane>

      <!-- ==================== 日志 ==================== -->
      <el-tab-pane label="日志" name="logs" lazy>
        <div ref="logPanelRef" class="panel" :class="{ 'is-fs': logFullscreen }">
          <div class="toolbar">
            <el-select v-model="logTail" style="width: 120px" :disabled="logFollow">
              <el-option :value="50" label="50 行" />
              <el-option :value="100" label="100 行" />
              <el-option :value="500" label="500 行" />
              <el-option :value="1000" label="1000 行" />
            </el-select>
            <el-button @click="loadLogs" :disabled="logFollow" :loading="logLoading">刷新</el-button>
            <el-switch v-model="logFollow" active-text="实时跟踪" @change="toggleFollow" />
            <el-button link @click="clearLogs">清屏</el-button>
            <el-button class="fs-btn" :icon="FullScreen" @click="toggleLogFullscreen">
              {{ logFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
          <div ref="logBoxRef" class="log-box" v-loading="logLoading && !logs">
            <pre>{{ logs || '（暂无日志）' }}</pre>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 终端 ==================== -->
      <el-tab-pane label="终端" name="terminal" lazy v-if="canOperate">
        <div ref="termPanelRef" class="panel" :class="{ 'is-fs': termFullscreen }">
          <div class="toolbar">
            <el-tag :type="termConnected ? 'success' : 'danger'" size="small">
              {{ termConnected ? '已连接（bash/sh）' : '未连接' }}
            </el-tag>
            <el-button v-if="!termConnected" type="primary" size="small" @click="reconnect">重新连接</el-button>
            <span class="tip">可直接执行命令，如 <code>ls -l /</code>、<code>ps aux</code>，输入 <code>exit</code> 退出</span>
            <el-button class="fs-btn" :icon="FullScreen" @click="toggleTermFullscreen">
              {{ termFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
          <div class="term-wrap">
            <XTerminal v-if="activeTab === 'terminal'" :key="termKey" :ws-url="consoleWsUrl"
                       @open="termConnected = true" @close="termConnected = false" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 文件 ==================== -->
      <el-tab-pane label="文件" name="files" lazy>
        <el-alert v-if="info && !info.State?.Running" type="info" :closable="false" show-icon
                  title="容器未运行，无法浏览或编辑容器内文件；启动容器后可用。" />
        <div v-else class="file-browser">
          <div class="file-tree">
            <div class="tree-head">
              <span>容器目录</span>
              <el-button link size="small" :loading="treeLoading" @click="refreshTree">刷新</el-button>
            </div>
            <el-tree
                ref="treeRef"
                :key="treeKey"
                :load="loadNode"
                :props="{ isLeaf: 'isLeaf' }"
                lazy
                node-key="path"
                highlight-current
                :expand-on-click-node="false"
                @node-click="onNodeClick">
              <template #default="{ data }">
                <span class="tree-node">
                  <span class="tree-ico" :class="data.dir ? 'ico-dir' : 'ico-file'">{{ data.dir ? '▸' : '·' }}</span>
                  <span class="tree-name" :title="data.path">{{ data.name }}</span>
                  <span v-if="!data.dir && data.size != null" class="tree-size">{{ fmtBytes(data.size) }}</span>
                </span>
              </template>
            </el-tree>
          </div>

          <div class="file-editor">
            <template v-if="curFile.path">
              <div class="editor-head">
                <span class="file-path" :title="curFile.path">{{ curFile.path }}</span>
                <el-tag v-if="curFile.dirty" type="warning" size="small">未保存</el-tag>
                <el-button size="small" :loading="fileLoading" @click="reloadFile">重新加载</el-button>
                <el-button size="small" type="primary" :disabled="!curFile.dirty || curFile.binary || curFile.truncated"
                           :loading="saving" @click="saveFile" v-if="canOperate">保存</el-button>
              </div>
              <el-alert v-if="curFile.binary" type="info" :closable="false" show-icon
                        title="这是二进制文件，无法以文本方式查看或编辑。" />
              <el-alert v-else-if="curFile.truncated" type="warning" :closable="false" show-icon
                        title="文件超过 1MB，仅显示前 1MB。为避免损坏，已禁止保存。" />
              <el-input v-else v-model="curFile.content" type="textarea" class="editor-textarea"
                        resize="none" spellcheck="false"
                        @input="curFile.dirty = true" />
            </template>
            <el-empty v-else description="从左侧选择一个文本文件进行查看与编辑" :image-size="80" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Inspect JSON 全文弹窗 -->
    <el-dialog v-model="jsonVisible" :title="`Inspect JSON - ${displayName}`" width="72%" top="5vh">
      <div class="json-dialog-toolbar">
        <span class="json-size">{{ jsonText.length.toLocaleString() }} 字符</span>
        <el-button size="small" @click="copyJson">复制</el-button>
        <el-button size="small" @click="downloadJson">下载 .json</el-button>
      </div>
      <pre ref="jsonPreRef" class="raw-json json-dialog-pre">{{ jsonText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useEnvironmentStore } from '@/stores/environment'
import { useAuthStore } from '@/stores/auth'
import { usePermission } from '@/composables/usePermission'
import { useFullscreen } from '@/composables/useFullscreen'
import { FullScreen } from '@element-plus/icons-vue'
import { inspectContainer, getContainerLogs, listContainerFiles, readContainerFile, writeContainerFile } from '@/api/container'
import { getContainerStat } from '@/api/monitor'
import TrendChart from '@/components/charts/TrendChart.vue'
import XTerminal from '@/components/terminal/XTerminal.vue'

const route = useRoute()
const envStore = useEnvironmentStore()
const authStore = useAuthStore()
const { canOperate } = usePermission()
const containerId = route.params.id as string

const activeTab = ref('detail')
const info = ref<any>(null)
const loading = ref(false)

const displayName = computed(() => (info.value?.Name || '').replace(/^\//, '') || containerId.slice(0, 12))

/* ---------------- Inspect JSON 全文 ---------------- */
const jsonVisible = ref(false)
const jsonText = computed(() => (info.value ? JSON.stringify(info.value, null, 2) : ''))

async function copyJson() {
  try {
    await navigator.clipboard.writeText(jsonText.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 非 https 下 clipboard API 不可用，回退到临时 textarea
    const ta = document.createElement('textarea')
    ta.value = jsonText.value
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制到剪贴板')
  }
}

function downloadJson() {
  const blob = new Blob([jsonText.value], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${displayName.value || containerId.slice(0, 12)}-inspect.json`
  a.click()
  URL.revokeObjectURL(url)
}

function joinList(arr?: string[]) {
  return (arr || []).join(' ')
}

const exposedPorts = computed(() => Object.keys(info.value?.Config?.ExposedPorts || {}))

const envList = computed(() =>
  (info.value?.Config?.Env || []).map((e: string) => {
    const i = e.indexOf('=')
    return i >= 0 ? { key: e.slice(0, i), value: e.slice(i + 1) } : { key: e, value: '' }
  })
)

const labelList = computed(() =>
  Object.entries(info.value?.Config?.Labels || {}).map(([key, value]) => ({ key, value }))
)

const networkList = computed(() =>
  Object.entries(info.value?.NetworkSettings?.Networks || {}).map(([name, n]: [string, any]) => ({
    name, ...n,
  }))
)

const portList = computed(() => {
  const ports = info.value?.NetworkSettings?.Ports || {}
  const rows: any[] = []
  for (const [containerPort, binds] of Object.entries(ports)) {
    const [port, proto] = containerPort.split('/')
    const list = (binds as any[]) || []
    if (list.length === 0) {
      rows.push({ container: port, proto: proto?.toUpperCase(), hostIp: '-', hostPort: '未发布' })
    } else {
      for (const b of list) {
        rows.push({ container: port, proto: proto?.toUpperCase(), hostIp: b.HostIp || '0.0.0.0', hostPort: b.HostPort })
      }
    }
  }
  return rows
})

const retrySuffix = computed(() => {
  const rp = info.value?.HostConfig?.RestartPolicy
  return rp?.Name === 'on-failure' && rp.MaximumRetryCount ? ` (${rp.MaximumRetryCount} 次)` : ''
})

function fmtBytes(n?: number) {
  if (!n) return '不限'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(v % 1 === 0 ? 0 : 1)} ${units[i]}`
}

async function loadDetail() {
  loading.value = true
  try {
    info.value = await inspectContainer(envStore.currentEnvId!, containerId)
  } finally {
    loading.value = false
  }
}

/* ---------------- 日志 ---------------- */
const logs = ref('')
const logLoading = ref(false)
const logTail = ref(100)
const logFollow = ref(false)
const logBoxRef = ref<HTMLElement | null>(null)
const logPanelRef = ref<HTMLElement>()
const { isFullscreen: logFullscreen, toggle: toggleLogFullscreen } = useFullscreen(logPanelRef)
let logWs: WebSocket | null = null

async function loadLogs() {
  logLoading.value = true
  try {
    logs.value = await getContainerLogs(envStore.currentEnvId!, containerId, logTail.value, true)
  } catch (e) {
    logs.value = ''
    console.error(e)
  } finally {
    logLoading.value = false
  }
}

function toggleFollow(on: any) {
  if (on) {
    startLogStream()
  } else {
    stopLogStream()
  }
}

function startLogStream() {
  stopLogStream()
  const envId = envStore.currentEnvId
  if (!envId) return
  logs.value = ''
  const token = authStore.token
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  logWs = new WebSocket(`${protocol}//${window.location.host}/ws/env/${envId}/containers/${containerId}/logs${token ? `?token=${token}` : ''}`)
  logWs.onmessage = (ev) => {
    logs.value += typeof ev.data === 'string' ? ev.data : new TextDecoder().decode(ev.data)
    nextTick(() => {
      if (logBoxRef.value) logBoxRef.value.scrollTop = logBoxRef.value.scrollHeight
    })
  }
  logWs.onclose = () => { logFollow.value = false }
}

function stopLogStream() {
  if (logWs) {
    logWs.onclose = null
    logWs.close()
    logWs = null
  }
}

function clearLogs() {
  logs.value = ''
}

/* ---------------- 终端 ---------------- */
const termConnected = ref(false)
const termKey = ref(0)
const termPanelRef = ref<HTMLElement>()
const { isFullscreen: termFullscreen, toggle: toggleTermFullscreen } = useFullscreen(termPanelRef)

const consoleWsUrl = computed(() => {
  const envId = envStore.currentEnvId
  const token = authStore.token
  if (!envId) return ''
  return `/ws/env/${envId}/containers/${containerId}/console${token ? `?token=${token}` : ''}`
})

function reconnect() {
  termConnected.value = false
  termKey.value++
}

/* ---------------- 文件浏览与编辑 ---------------- */
const treeRef = ref<any>(null)
const treeKey = ref(0)
const treeLoading = ref(false)
const fileLoading = ref(false)
const saving = ref(false)
const curFile = ref<any>({ path: '', content: '', size: 0, binary: false, truncated: false, dirty: false })

// 懒加载：根节点直接展开为 / 的内容，目录节点展开时加载其子项
async function loadNode(node: any, resolve: (data: any[]) => void) {
  const envId = envStore.currentEnvId
  if (!envId || !info.value?.State?.Running) { resolve([]); return }
  try {
    const path = node.level === 0 ? '/' : node.data.path
    const list = await listContainerFiles(envId, containerId, path)
    resolve((list || []).map((f: any) => ({
      name: f.name, path: f.path, dir: f.dir, size: f.size, isLeaf: !f.dir,
    })))
  } catch (e) {
    console.error(e)
    resolve([])
  }
}

async function onNodeClick(data: any) {
  if (data.dir) return
  const envId = envStore.currentEnvId
  if (!envId) return
  if (curFile.value.dirty && curFile.value.path !== data.path) {
    try {
      await ElMessageBox.confirm('当前文件有未保存的修改，切换将丢失，确定继续？', '提示', { type: 'warning' })
    } catch { return }
  }
  fileLoading.value = true
  try {
    const res = await readContainerFile(envId, containerId, data.path)
    curFile.value = {
      path: data.path, content: res.content ?? '', size: res.size,
      binary: !!res.binary, truncated: !!res.truncated, dirty: false,
    }
  } catch (e: any) {
    ElMessage.error(e.message || '读取文件失败')
  } finally {
    fileLoading.value = false
  }
}

async function saveFile() {
  const envId = envStore.currentEnvId
  if (!envId || !curFile.value.path) return
  saving.value = true
  try {
    await writeContainerFile(envId, containerId, curFile.value.path, curFile.value.content)
    curFile.value.dirty = false
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function reloadFile() {
  if (!curFile.value.path) return
  const p = curFile.value.path
  curFile.value = { path: '', content: '', size: 0, binary: false, truncated: false, dirty: false }
  onNodeClick({ dir: false, path: p })
}

function refreshTree() {
  // 重建树会重置懒加载，重新拉取最新目录结构
  curFile.value = { path: '', content: '', size: 0, binary: false, truncated: false, dirty: false }
  treeKey.value++
}

/* ---------------- 监控 ---------------- */
const MON_MAX_POINTS = 60            // 保留最近 60 个点（约 3 分钟）
const MON_INTERVAL_MS = 3000
const monLabels = ref<string[]>([])
const monCpu = ref<number[]>([])
const monMem = ref<number[]>([])
const monNetRx = ref<number[]>([])
const monNetTx = ref<number[]>([])
const monBlkR = ref<number[]>([])
const monBlkW = ref<number[]>([])
const monNow = ref({ cpu: '0.0', mem: '0', memLimit: '0', memPct: '0.0', netRx: '-', netTx: '-', blkR: '-', blkW: '-' })
const monError = ref('')
let monTimer: ReturnType<typeof setInterval> | null = null
let monPrev: { t: number; rx: number; tx: number; br: number; bw: number } | null = null

const monRunning = computed(() => !!info.value?.State?.Running)
const monNetSeries = computed(() => [
  { name: '接收', color: '#E6A23C', data: monNetRx.value },
  { name: '发送', color: '#F56C6C', data: monNetTx.value },
])
const monBlkSeries = computed(() => [
  { name: '读取', color: '#409EFF', data: monBlkR.value },
  { name: '写入', color: '#9B59B6', data: monBlkW.value },
])

function fmtRate(n: number) {
  const units = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v >= 100 || i === 0 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`
}

function pushPoint(list: number[], v: number) {
  list.push(v)
  if (list.length > MON_MAX_POINTS) list.shift()
}

async function sampleMonitor() {
  const envId = envStore.currentEnvId
  if (!envId || !monRunning.value) return
  try {
    const s: any = await getContainerStat(envId, containerId)
    if (s.error) {
      monError.value = String(s.error)
      return
    }
    monError.value = ''
    const now = Date.now()
    const rx = Number(s.networkRxBytes) || 0
    const tx = Number(s.networkTxBytes) || 0
    const br = Number(s.blockReadBytes) || 0
    const bw = Number(s.blockWriteBytes) || 0
    // 累计计数器 → 每秒速率；容器重启后计数器回退则视为 0
    const dt = monPrev ? Math.max((now - monPrev.t) / 1000, 0.5) : 0
    const rate = (cur: number, prev: number | undefined) =>
      monPrev && prev != null && cur >= prev ? (cur - prev) / dt : 0
    const rxRate = rate(rx, monPrev?.rx)
    const txRate = rate(tx, monPrev?.tx)
    const brRate = rate(br, monPrev?.br)
    const bwRate = rate(bw, monPrev?.bw)
    monPrev = { t: now, rx, tx, br, bw }

    const d = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    monLabels.value.push(`${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`)
    if (monLabels.value.length > MON_MAX_POINTS) monLabels.value.shift()
    pushPoint(monCpu.value, Number(s.cpuPercent) || 0)
    pushPoint(monMem.value, Math.round(((Number(s.memoryUsage) || 0) / 1048576) * 10) / 10)
    pushPoint(monNetRx.value, Math.round(rxRate))
    pushPoint(monNetTx.value, Math.round(txRate))
    pushPoint(monBlkR.value, Math.round(brRate))
    pushPoint(monBlkW.value, Math.round(bwRate))

    const memLimit = Number(s.memoryLimit) || 0
    monNow.value = {
      cpu: (Number(s.cpuPercent) || 0).toFixed(1),
      mem: ((Number(s.memoryUsage) || 0) / 1048576).toFixed(1),
      memLimit: (memLimit / 1048576).toFixed(0),
      memPct: (Number(s.memoryPercent) || 0).toFixed(1),
      netRx: fmtRate(rxRate),
      netTx: fmtRate(txRate),
      blkR: fmtRate(brRate),
      blkW: fmtRate(bwRate),
    }
  } catch (e: any) {
    monError.value = e.message || String(e)
  }
}

function startMonitor() {
  stopMonitor()
  monPrev = null
  if (!monRunning.value) return
  sampleMonitor()
  monTimer = setInterval(sampleMonitor, MON_INTERVAL_MS)
}

function stopMonitor() {
  if (monTimer) {
    clearInterval(monTimer)
    monTimer = null
  }
}

function clearMonitor() {
  monLabels.value = []
  monCpu.value = []
  monMem.value = []
  monNetRx.value = []
  monNetTx.value = []
  monBlkR.value = []
  monBlkW.value = []
  monPrev = null
}

watch(activeTab, (tab) => {
  if (tab === 'logs' && !logs.value && !logFollow.value) loadLogs()
  if (tab === 'monitor') startMonitor()
  else stopMonitor()
})

onMounted(loadDetail)
onBeforeUnmount(() => {
  stopLogStream()
  stopMonitor()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h2 { font-size: 20px; margin: 0; }
  .header-right { display: flex; align-items: center; }
}

.section {
  margin-bottom: 20px;

  h4 { margin: 0 0 10px; font-size: 14px; color: #303133; }
  code { white-space: pre-wrap; word-break: break-all; }
}

.raw-json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 480px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.json-dialog-pre {
  max-height: 66vh;
  white-space: pre;
  word-break: normal;
}

.json-dialog-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 8px;

  .json-size { margin-right: auto; font-size: 12px; color: #909399; }
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  .tip { font-size: 12px; color: #909399; }
  .fs-btn { margin-left: auto; }
}

.log-box {
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 16px;
  height: 520px;
  overflow: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;

  pre { margin: 0; white-space: pre-wrap; word-break: break-all; }
}

.term-wrap {
  height: 520px;
  border-radius: 6px;
  overflow: hidden;
  background: #1e1e1e;
}

/* 日志 / 终端面板：全屏时整块撑满视口，内容区跟着 flex 拉伸，
   控件条留在顶部，否则全屏里没法退出也没法刷日志 */
.panel { display: flex; flex-direction: column; min-height: 0; }
.panel.is-fs {
  height: 100vh;
  box-sizing: border-box;
  padding: 12px 16px 16px;
  background: #f5f7fa;
}
.panel.is-fs .log-box,
.panel.is-fs .term-wrap { flex: 1; height: auto; min-height: 0; }

.file-browser {
  display: flex;
  gap: 12px;
  height: 540px;
}

.mon-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;

  .mon-card {
    border: 1px solid #ebeef5;
    border-radius: 6px;
    padding: 12px 16px;

    .mon-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
    .mon-value { font-size: 22px; font-weight: 600; color: #303133; line-height: 1.2; }
    .mon-value.small { font-size: 15px; }
    .mon-unit { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
    .mon-sub { font-size: 12px; color: #c0c4cc; margin-top: 2px; }
  }
}

.mon-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 12px;

  :deep(.el-card__body) { padding: 10px 12px; }
}

.mon-error { font-size: 12px; color: #f56c6c; }

.file-tree {
  flex: 0 0 300px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;

  .tree-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    border-bottom: 1px solid #ebeef5;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }

  :deep(.el-tree) {
    flex: 1;
    overflow: auto;
    padding: 6px;
  }
}

.tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  padding-right: 8px;
  font-size: 13px;

  .tree-ico { width: 14px; margin-right: 4px; color: #909399; }
  .ico-dir { color: #e6a23c; }
  .tree-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .tree-size { margin-left: 8px; color: #c0c4cc; font-size: 12px; flex-shrink: 0; }
}

.file-editor {
  flex: 1;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  padding: 10px 12px;

  .editor-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;

    .file-path {
      flex: 1;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      color: #303133;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .editor-textarea {
    flex: 1;
    :deep(.el-textarea__inner) {
      height: 100%;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 13px;
      line-height: 1.6;
    }
  }
}
</style>
