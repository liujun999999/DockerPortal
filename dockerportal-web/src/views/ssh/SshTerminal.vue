<template>
  <div class="ssh-terminal-page">
    <el-card shadow="never" class="mb-10">
      <div class="toolbar">
        <el-select v-model="selectedTarget" placeholder="选择节点或自定义主机" style="width: 320px">
          <el-option-group v-if="nodes.length" label="Swarm 节点">
            <el-option v-for="n in nodes" :key="n.ID" :label="nodeLabel(n)" :value="'node:' + n.ID">
              <span>{{ nodeLabel(n) }}</span>
              <span style="float: right; color: #909399; font-size: 12px; margin-left: 16px">{{ nodeIp(n.ID) || '无地址' }}</span>
              <el-tag :type="hasConfig(n.ID) ? 'success' : 'info'" size="small" style="margin-left: 8px">
                {{ hasConfig(n.ID) ? '已配置' : '未配置' }}
              </el-tag>
            </el-option>
          </el-option-group>
          <el-option-group v-if="customHosts.length" label="自定义主机">
            <el-option v-for="c in customHosts" :key="c.id" :label="c.name || c.host" :value="'cfg:' + c.id">
              <span>{{ c.name || c.host }}</span>
              <span style="float: right; color: #909399; font-size: 12px">{{ c.username }}@{{ c.host }}:{{ c.port }}</span>
            </el-option>
          </el-option-group>
        </el-select>
        <el-button type="primary" :disabled="!selectedTarget" @click="connectTarget(selectedTarget)">连接</el-button>
        <el-button :disabled="!isNodeTarget" @click="openConfigDialog(selectedTarget.slice(5))">配置连接</el-button>
        <el-button @click="openCustomHostDialog">添加主机</el-button>
        <el-button @click="addTab">新标签</el-button>
      </div>
    </el-card>

    <!-- 多标签终端 -->
    <el-card ref="termCardRef" shadow="never" class="terminal-card" :class="{ 'is-fs': termFullscreen }">
      <template #header>
        <div class="term-head">
          <span>终端会话</span>
          <div class="term-head-actions">
            <span class="head-tip">{{ termFullscreen ? '按 ESC 也可退出全屏' : '全屏后可继续切换标签' }}</span>
            <el-button size="small" :icon="FullScreen" :disabled="!tabs.length" @click="toggleTermFullscreen">
              {{ termFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-tabs v-model="activeTab" type="card" closable @tab-remove="removeTab">
        <el-tab-pane v-for="tab in tabs" :key="tab.id" :label="tab.label" :name="tab.id">
          <div :ref="(el) => setTerminalRef(tab.id, el as HTMLElement)" class="terminal-container"></div>
        </el-tab-pane>
      </el-tabs>
      <el-empty v-if="tabs.length === 0" description="请选择节点或主机并点击连接，也可点击「添加主机」接入集群外的机器" />
    </el-card>

    <!-- 已有 SSH 配置 -->
    <el-card v-if="configs.length" shadow="never" class="mb-10">
      <template #header><span>已配置的 SSH 连接</span></template>
      <el-table :data="pageData" size="small" stripe>
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <span>{{ row.nodeId ? nodeLabelById(row.nodeId) : (row.name || row.host) }}</span>
            <el-tag v-if="!row.nodeId" size="small" style="margin-left: 8px">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="160">
          <template #default="{ row }"><code>{{ row.host }}:{{ row.port }}</code></template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="认证方式" width="100">
          <template #default="{ row }">{{ row.authType === 'KEY' ? '私钥' : '密码' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="editConfig(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="doDeleteConfig(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
    </el-card>

    <!-- SSH 配置对话框 -->
    <el-dialog v-model="configVisible" :title="dialogTitle" width="520px">
      <el-form :model="configForm" label-width="90px">
        <el-form-item v-if="configForm.nodeId" label="节点">
          <span>{{ nodeLabelById(configForm.nodeId) }}</span>
        </el-form-item>
        <el-form-item v-else label="显示名称">
          <el-input v-model="configForm.name" placeholder="便于识别，如：数据库服务器（留空则用主机地址）" />
        </el-form-item>
        <el-form-item label="主机地址" required>
          <el-input v-model="configForm.host" placeholder="IP 或域名，如 192.168.1.10" />
          <div v-if="hostAutoFilled" class="field-hint">已从节点状态地址（Status.Addr）自动带出；若 SSH 监听地址不同（如内网/公网分离）请修改。</div>
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="configForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="configForm.username" placeholder="SSH 登录用户名，如 root" />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-radio-group v-model="configForm.authType">
            <el-radio value="PASSWORD">密码</el-radio>
            <el-radio value="KEY">私钥</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="configForm.authType === 'PASSWORD'" label="密码">
          <el-input v-model="configForm.password" type="password" show-password
                    :placeholder="configForm.id ? '留空表示不修改' : 'SSH 登录密码'" />
        </el-form-item>
        <el-form-item v-else label="私钥">
          <el-input v-model="configForm.privateKey" type="textarea" :rows="5"
                    :placeholder="configForm.id ? '留空表示不修改' : '粘贴 OpenSSH 格式私钥（-----BEGIN ... PRIVATE KEY-----）'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display:flex; justify-content:flex-end; gap:8px">
          <el-button @click="configVisible = false">取消</el-button>
          <el-button :loading="testing" @click="doTestConfig">测试连接</el-button>
          <el-button type="primary" :loading="saving" @click="doSaveConfig">保存并连接</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen } from '@element-plus/icons-vue'
import { useFullscreen } from '@/composables/useFullscreen'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { getNodes } from '@/api/node'
import { getSshConfigs, createSshConfig, updateSshConfig, deleteSshConfig, testSshConfig } from '@/api/ssh'
import { useEnvironmentStore } from '@/stores/environment'
import { useAuthStore } from '@/stores/auth'

const envStore = useEnvironmentStore()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const nodes = ref<any[]>([])
const configs = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(configs)
const selectedTarget = ref('')
const activeTab = ref('')
const configVisible = ref(false)
const saving = ref(false)
const testing = ref(false)
const hostAutoFilled = ref(false)
const configForm = ref<any>({ id: null, nodeId: '', name: '', host: '', port: 22, username: '', authType: 'PASSWORD', password: '', privateKey: '' })
let tabCounter = 0
let connectAfterSave = false
// 配 SSH 中途去保存配置的场景：记住待进入的容器，保存后自动续上
let pendingExec = ''

// nodeId 为空的是不依附 Swarm 集群的自定义主机
const customHosts = computed(() => configs.value.filter(c => !c.nodeId))
const isNodeTarget = computed(() => selectedTarget.value.startsWith('node:'))
const dialogTitle = computed(() => {
  if (configForm.value.id) return '编辑 SSH 连接'
  return configForm.value.nodeId ? '配置节点 SSH 连接' : '添加自定义主机'
})

interface Tab { id: string; label: string; exec: string; ws: WebSocket | null; terminal: Terminal | null; fitAddon: FitAddon | null }
const tabs = ref<Tab[]>([])
const terminalRefs = new Map<string, HTMLElement>()
const encoder = new TextEncoder()

// docker-java 返回 PascalCase 字段
function nodeLabel(n: any) {
  return n?.Description?.Hostname || n?.Spec?.Name || n?.ID?.substring(0, 12) || '未知节点'
}

function nodeLabelById(nodeId: string) {
  const node = nodes.value.find(n => n.ID === nodeId)
  return node ? nodeLabel(node) : (nodeId ? nodeId.substring(0, 12) : '-')
}

function hasConfig(nodeId: string) {
  return configs.value.some(c => c.nodeId === nodeId)
}

/** 节点 IP 取自 Status.Addr（docker node ls 的 ADDRESS 列同源，如 10.0.0.5:2377 / [fd0::1]:2377），剥掉端口 */
function nodeIp(nodeId: string): string {
  const addr = nodes.value.find(n => n.ID === nodeId)?.Status?.Addr || ''
  if (!addr) return ''
  if (addr.startsWith('[')) {
    const cb = addr.indexOf(']')
    return cb > 0 ? addr.slice(1, cb) : addr
  }
  const idx = addr.lastIndexOf(':')
  return idx > 0 ? addr.slice(0, idx) : addr
}

function setTerminalRef(tabId: string, el: HTMLElement | null) {
  if (el) {
    terminalRefs.set(tabId, el)
    nextTick(() => {
      const tab = tabs.value.find(t => t.id === tabId)
      if (tab && !tab.terminal) initTerminal(tab, el)
    })
  }
}

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  try {
    const [nodeList, configList] = await Promise.all([getNodes(envId), getSshConfigs(envId)])
    nodes.value = nodeList || []
    configs.value = configList || []
  } catch (e) {
    console.error(e)
  }
}

function addTab() {
  if (!selectedTarget.value) return
  connectTarget(selectedTarget.value)
}

function openConfigDialog(nodeId: string) {
  const existing = configs.value.find(c => c.nodeId === nodeId)
  if (existing) {
    configForm.value = { ...existing, password: '', privateKey: '' }
    hostAutoFilled.value = false
  } else {
    // 新建节点配置：自动带出节点 IP，省去手抄
    const ip = nodeIp(nodeId)
    configForm.value = { id: null, nodeId, name: '', host: ip, port: 22, username: '', authType: 'PASSWORD', password: '', privateKey: '' }
    hostAutoFilled.value = !!ip
  }
  connectAfterSave = true
  configVisible.value = true
}

function openCustomHostDialog() {
  configForm.value = { id: null, nodeId: '', name: '', host: '', port: 22, username: '', authType: 'PASSWORD', password: '', privateKey: '' }
  hostAutoFilled.value = false
  connectAfterSave = true
  configVisible.value = true
}

function editConfig(row: any) {
  configForm.value = { ...row, password: '', privateKey: '' }
  hostAutoFilled.value = false
  connectAfterSave = false
  configVisible.value = true
}

async function doSaveConfig() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const f = configForm.value
  if (!f.host?.trim() || !f.username?.trim()) { ElMessage.warning('主机地址和用户名不能为空'); return }
  saving.value = true
  try {
    const payload = buildConfigPayload(f)
    let savedId: number | null = f.id ?? null
    if (f.id) {
      await updateSshConfig(envId, f.id, payload)
    } else {
      const created: any = await createSshConfig(envId, payload)
      savedId = created?.id ?? null
    }
    ElMessage.success('SSH 配置已保存')
    configVisible.value = false
    await loadData()
    if (connectAfterSave && savedId != null) {
      selectedTarget.value = f.nodeId ? `node:${f.nodeId}` : `cfg:${savedId}`
      connectTarget(selectedTarget.value, pendingExec)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function buildConfigPayload(f: any) {
  return {
    id: f.id ?? undefined,
    nodeId: f.nodeId || null, name: (f.name || '').trim() || null,
    host: f.host.trim(), port: f.port,
    username: f.username.trim(), authType: f.authType,
    password: f.password || null, privateKey: f.privateKey || null,
  }
}

async function doTestConfig() {
  const envId = envStore.currentEnvId
  if (!envId) return
  const f = configForm.value
  if (!f.host?.trim() || !f.username?.trim()) { ElMessage.warning('请先填写主机地址和用户名'); return }
  testing.value = true
  try {
    const msg: any = await testSshConfig(envId, buildConfigPayload(f))
    ElMessage.success(typeof msg === 'string' ? msg : '连接成功')
  } catch (e: any) {
    // 具体原因（无法连接/超时/认证失败）已由响应拦截器提示
  } finally {
    testing.value = false
  }
}

async function doDeleteConfig(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  const label = row.nodeId ? nodeLabelById(row.nodeId) : (row.name || row.host)
  try {
    await ElMessageBox.confirm(`确定删除 ${label} 的 SSH 配置？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteSshConfig(envId, row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

/**
 * @param exec 不为空时，登录后直接 docker exec 进入该容器（服务副本场景），而不是停在宿主 shell
 */
function connectTarget(target: string, exec = '') {
  const envId = envStore.currentEnvId
  if (!envId || !target) return
  pendingExec = exec
  let cfg: any
  let label = ''
  // Swarm 节点配置继续用节点 ID 寻址（与旧版后端兼容），自定义主机用配置 ID（纯数字）
  let urlTarget: string
  if (target.startsWith('cfg:')) {
    cfg = configs.value.find(c => c.id === Number(target.slice(4)))
    if (!cfg) { ElMessage.warning('配置已不存在'); loadData(); return }
    label = cfg.name || cfg.host
    urlTarget = String(cfg.id)
  } else {
    const nodeId = target.slice(5)
    cfg = configs.value.find(c => c.nodeId === nodeId)
    if (!cfg) {
      ElMessage.warning('该节点尚未配置 SSH 连接信息，请先完成配置')
      openConfigDialog(nodeId)
      return
    }
    label = nodeLabelById(nodeId)
    urlTarget = nodeId
  }
  if (exec) label = `${label} · 容器 ${exec.slice(0, 12)}`

  tabCounter++
  const tabId = `tab-${tabCounter}`
  const tab: Tab = { id: tabId, label, exec, ws: null, terminal: null, fitAddon: null }
  tabs.value.push(tab)
  activeTab.value = tabId

  nextTick(() => {
    const el = terminalRefs.get(tabId)
    if (el && !tab.terminal) initTerminal(tab, el)
    // 等布局稳定拿到真实行列数后再建连，避免 PTY 尺寸与前端不一致
    scheduleFit(tab, 0)
    setTimeout(() => connectWebSocket(tab, envId, urlTarget), 200)
  })
}

/**
 * 挂载瞬间字体/布局未稳定，fit() 会静默失败，需多次重试
 */
function scheduleFit(tab: Tab, attempt: number) {
  requestAnimationFrame(() => {
    const term = tab.terminal
    const fit = tab.fitAddon
    if (!term || !fit) return
    if (term.rows < 3 || term.cols < 20) {
      try { fit.fit() } catch { /* ignore */ }
    }
    if ((term.rows < 3 || term.cols < 20) && attempt < 3) {
      setTimeout(() => scheduleFit(tab, attempt + 1), 120 * (attempt + 1))
    }
  })
}

function initTerminal(tab: Tab, el: HTMLElement) {
  const term = new Terminal({
    fontSize: 14,
    fontFamily: "'Cascadia Code', 'Fira Code', 'Consolas', monospace",
    theme: { background: '#1e1e1e', foreground: '#d4d4d4', cursor: '#d4d4d4' },
    cursorBlink: true,
    scrollback: 10000,
  })
  const fit = new FitAddon()
  term.loadAddon(fit)
  term.open(el)
  fit.fit()

  // 键盘输入走二进制帧，文本帧保留给控制指令（resize）
  term.onData((data) => {
    if (tab.ws && tab.ws.readyState === WebSocket.OPEN) {
      tab.ws.send(encoder.encode(data).buffer)
    }
  })
  term.onResize(({ cols, rows }) => {
    if (tab.ws && tab.ws.readyState === WebSocket.OPEN) {
      tab.ws.send(JSON.stringify({ type: 'resize', cols, rows }))
    }
  })

  tab.terminal = term
  tab.fitAddon = fit
}

function connectWebSocket(tab: Tab, envId: number, target: string) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const token = authStore.token
  const cols = tab.terminal?.cols || 120
  const rows = tab.terminal?.rows || 30
  let url = `${protocol}//${host}/ws/env/${envId}/ssh/terminal/${target}`
    + `?token=${encodeURIComponent(token || '')}&cols=${cols}&rows=${rows}`
  if (tab.exec) url += `&exec=${encodeURIComponent(tab.exec)}`

  const ws = new WebSocket(url)
  ws.binaryType = 'arraybuffer'

  ws.onopen = () => {
    tab.terminal?.writeln('\x1b[32mSSH Connected.\x1b[0m\r\n')
  }
  ws.onmessage = (event) => {
    if (event.data instanceof ArrayBuffer) {
      tab.terminal?.write(new Uint8Array(event.data))
    } else {
      tab.terminal?.write(event.data)
    }
  }
  ws.onclose = (event) => {
    const reason = event.reason || 'Disconnected.'
    tab.terminal?.writeln(`\r\n\x1b[31m${reason}\x1b[0m\r\n`)
    if (event.code === 4001) {
      ElMessage.warning(reason)
      loadData()
    }
  }
  ws.onerror = () => {
    tab.terminal?.writeln('\r\n\x1b[31mConnection error.\x1b[0m\r\n')
  }
  tab.ws = ws
}

function handleWindowResize() {
  tabs.value.forEach(tab => {
    if (tab.id !== activeTab.value || !tab.terminal || !tab.fitAddon) return
    try {
      tab.fitAddon.fit()
      tab.terminal.scrollToBottom()
    } catch { /* ignore */ }
  })
}

/* 全屏切换不一定会触发 window resize（浏览器窗口尺寸可能没变），
   且 Chrome 全屏有动画，尺寸稳定前 fit() 会算错行列数，分两次补 fit */
const termCardRef = ref<HTMLElement>()
const { isFullscreen: termFullscreen, toggle: toggleTermFullscreen } = useFullscreen(termCardRef)
watch(termFullscreen, () => {
  nextTick(() => setTimeout(handleWindowResize, 80))
  setTimeout(handleWindowResize, 400)
})

// 切到某个标签后补一次 fit：隐藏面板里 fit() 量不到真实尺寸，
// 不补就会沿用切走之前的行列数；全屏下容器高度多了几百像素，底部会空出一大块
watch(activeTab, () => nextTick(() => setTimeout(handleWindowResize, 60)))

function removeTab(tabId: string) {
  const idx = tabs.value.findIndex(t => t.id === tabId)
  if (idx >= 0) {
    const tab = tabs.value[idx]
    tab.ws?.close()
    tab.terminal?.dispose()
    tabs.value.splice(idx, 1)
    terminalRefs.delete(tabId)
  }
  if (activeTab.value === tabId && tabs.value.length > 0) {
    activeTab.value = tabs.value[Math.max(0, idx - 1)].id
  }
}

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleWindowResize)
  // 从服务详情「进入终端」跳转过来：预选节点，带 exec 时直接打开该副本的容器终端
  const { node, exec } = route.query
  if (node) {
    selectedTarget.value = `node:${node}`
    if (exec) {
      connectTarget(`node:${node}`, String(exec))
      // 目标一次性消费，刷页不应重复拉起终端
      router.replace({ path: '/ssh' })
    }
  }
})
onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize)
  tabs.value.forEach(tab => {
    tab.ws?.close()
    tab.terminal?.dispose()
  })
})
</script>

<style scoped>
.mb-10 { margin-bottom: 10px; }
.toolbar { display: flex; gap: 10px; align-items: center; }
.term-head { display: flex; justify-content: space-between; align-items: center; }
.term-head-actions { display: flex; align-items: center; gap: 10px; }
.head-tip { font-size: 12px; color: #909399; }
.terminal-card { min-height: 500px; }
.terminal-container { width: 100%; height: 450px; }
/* 全屏：卡片撑满视口，标签页内容区 flex 拉伸，终端自适应高度而不是用 100vh 做减法 */
.terminal-card.is-fs {
  height: 100vh;
  min-height: 0;
  box-sizing: border-box;
  border-radius: 0;
  display: flex;
  flex-direction: column;
}
.terminal-card.is-fs :deep(.el-card__header) { flex: none; }
.terminal-card.is-fs :deep(.el-card__body) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.terminal-card.is-fs :deep(.el-tabs) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.terminal-card.is-fs :deep(.el-tabs__content) { flex: 1; min-height: 0; }
.terminal-card.is-fs :deep(.el-tab-pane) { height: 100%; }
.terminal-card.is-fs .terminal-container { height: 100%; }
.field-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4; }
</style>
