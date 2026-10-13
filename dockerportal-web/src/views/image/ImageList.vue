<template>
  <div>
    <div class="page-header">
      <h2>镜像</h2>
      <div class="header-actions">
        <el-checkbox v-model="onlyInUse" label="仅显示在用" style="margin-right: 12px" />
        <el-button @click="doPrune" style="margin-right: 12px" v-if="canOperate">清理镜像</el-button>
        <el-button @click="loadData" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="showPull = true" v-if="canOperate">拉取镜像</el-button>
      </div>
    </div>

    <el-table :data="pageData" v-loading="loading" stripe>
      <el-table-column label="仓库:标签" min-width="280">
        <template #default="{ row }">
          <template v-if="row.RepoTags && row.RepoTags.length">
            <div v-for="t in row.RepoTags.slice(0, 2)" :key="t" class="repo-tag">{{ t }}</div>
            <div v-if="row.RepoTags.length > 2" class="repo-tag more">…共 {{ row.RepoTags.length }} 个标签</div>
          </template>
          <span v-else class="repo-tag none">&lt;none&gt;</span>
        </template>
      </el-table-column>
      <el-table-column label="镜像ID" width="140">
        <template #default="{ row }">
          <code>{{ row.Id?.replace('sha256:', '').slice(0, 12) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="容器使用" width="170">
        <template #default="{ row }">
          <el-tag v-if="usageOf(row.Id).running > 0" type="success" size="small">
            运行中 {{ usageOf(row.Id).running }}
          </el-tag>
          <el-tag v-else-if="usageOf(row.Id).total > 0" type="warning" size="small">
            已停止 {{ usageOf(row.Id).total }}
          </el-tag>
          <el-tag v-else type="info" size="small" effect="plain">未使用</el-tag>
          <span v-if="usageOf(row.Id).running > 0 && usageOf(row.Id).total > usageOf(row.Id).running" class="usage-extra">
            / 共 {{ usageOf(row.Id).total }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ formatSize(row.Size) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ new Date(row.Created * 1000).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button size="small" @click="openHistory(row)">分层</el-button>
          <el-button size="small" type="primary" plain @click="openPush(row)" v-if="canOperate">推送</el-button>
          <!-- 使用中的镜像不给删也不给强删：按钮直接置灰，把原因和出路说清；
               禁用按钮不冒鼠标事件，得包一层 span 才能触发 tooltip -->
          <el-tooltip v-if="canOperate && usageOf(row.Id).total > 0" :content="inUseTip(row)" placement="top">
            <span class="btn-wrap">
              <el-button size="small" type="danger" disabled>删除</el-button>
            </span>
          </el-tooltip>
          <el-popconfirm v-else-if="canOperate" title="确认删除该镜像？" width="220" @confirm="doRemove(row.Id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />

    <!-- ==================== 镜像详情抽屉 ==================== -->
    <el-drawer v-model="detailVisible" :title="`镜像详情 - ${detailName}`" size="62%">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border class="detail-sec">
            <el-descriptions-item label="镜像 ID" :span="2">
              <code>{{ detail.Id }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatSize(detail.Size) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ new Date(detail.Created).toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="架构">{{ detail.Architecture || '-' }} / {{ detail.Os || '-' }}</el-descriptions-item>
            <el-descriptions-item label="镜像层数">{{ (detail.RootFS?.Layers || []).length }}</el-descriptions-item>
            <el-descriptions-item label="标签" :span="2">
              <template v-if="detail.RepoTags && detail.RepoTags.length">
                <el-tag v-for="t in detail.RepoTags" :key="t" size="small" style="margin-right: 6px">{{ t }}</el-tag>
              </template>
              <span v-else>&lt;none&gt;</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.RepoDigests && detail.RepoDigests.length" label="摘要" :span="2">
              <div v-for="d in detail.RepoDigests" :key="d"><code class="digest">{{ d }}</code></div>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.Comment" label="备注" :span="2">{{ detail.Comment }}</el-descriptions-item>
          </el-descriptions>

          <h4 class="detail-title">容器使用情况</h4>
          <el-table v-if="detailUsage.length" :data="detailUsage" size="small" border class="detail-sec">
            <el-table-column label="容器" min-width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="$router.push(`/containers/${row.id}`)">{{ row.name }}</el-link>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.state === 'running' ? 'success' : 'info'" size="small">{{ row.state }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启动命令" min-width="220">
              <template #default="{ row }"><code class="cmd">{{ row.command }}</code></template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="当前没有容器使用此镜像" :image-size="60" />

          <h4 class="detail-title">启动配置</h4>
          <el-descriptions :column="1" border class="detail-sec">
            <el-descriptions-item label="Entrypoint">
              <code>{{ joinArr(detail.Config?.Entrypoint) || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Cmd">
              <code>{{ joinArr(detail.Config?.Cmd) || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="工作目录 / 用户">
              {{ detail.Config?.WorkingDir || '-' }} / {{ detail.Config?.User || 'root' }}
            </el-descriptions-item>
            <el-descriptions-item label="暴露端口">
              <template v-if="Object.keys(detail.Config?.ExposedPorts || {}).length">
                <el-tag v-for="p in Object.keys(detail.Config.ExposedPorts)" :key="p" size="small" style="margin-right: 6px">{{ p }}</el-tag>
              </template>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="envList.length" label="环境变量">
              <div class="env-list">
                <div v-for="e in envList" :key="e.k" class="env-item">
                  <span class="env-k">{{ e.k }}</span>=<span class="env-v">{{ e.v }}</span>
                </div>
              </div>
            </el-descriptions-item>
            <el-descriptions-item v-if="Object.keys(detail.Config?.Labels || {}).length" label="Labels">
              <div v-for="(v, k) in detail.Config.Labels" :key="k"><code>{{ k }} = {{ v }}</code></div>
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="detail-title">镜像层（{{ (detail.RootFS?.Layers || []).length }}）</h4>
          <div class="layer-list">
            <div v-for="(l, i) in detail.RootFS?.Layers || []" :key="l" class="layer-item">
              <span class="layer-idx">#{{ i + 1 }}</span> <code>{{ l.replace('sha256:', '').slice(0, 24) }}</code>
            </div>
          </div>

          <el-collapse class="detail-sec">
            <el-collapse-item title="原始 JSON（完整 inspect 数据）" name="raw">
              <div class="raw-head">
                <el-button size="small" @click="copyDetailJson">复制</el-button>
              </div>
              <pre class="raw-json">{{ JSON.stringify(detail, null, 2) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </template>
      </div>
    </el-drawer>

    <!-- ==================== 拉取镜像 ==================== -->
    <el-dialog v-model="showPull" title="拉取镜像" width="420px">
      <el-form label-width="80px">
        <el-form-item label="镜像仓库">
          <el-select v-model="pullForm.registryId" style="width:100%">
            <el-option v-for="r in registries" :key="r.id" :label="registryLabel(r)" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="镜像名">
          <el-input v-model="pullForm.imageName" :placeholder="pullInputPlaceholder" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="pullForm.tag" placeholder="latest" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPull = false">取消</el-button>
        <el-button type="primary" :loading="pulling" @click="doPull">拉取</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 推送镜像 ==================== -->
    <el-dialog v-model="showPush" title="推送镜像" width="480px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="镜像">
          <span class="push-src">{{ pushSourceName }}</span>
        </el-form-item>
        <el-form-item label="目标仓库">
          <el-select v-model="pushForm.registryId" style="width:100%" @change="onPushRegistry">
            <el-option v-for="r in registries" :key="r.id" :label="registryLabel(r)" :value="r.id" />
          </el-select>
          <div class="push-hint">非 Docker Hub 仓库需先在「设置 → 镜像仓库」配好凭据；留默认时按地址前缀自动匹配。</div>
        </el-form-item>
        <el-form-item label="目标路径" required>
          <el-input v-model="pushForm.repo" placeholder="如 my-project/web 或 registry.example.com/team/app" />
        </el-form-item>
        <el-form-item label="标签" required>
          <el-input v-model="pushForm.tag" placeholder="latest" />
        </el-form-item>
        <el-alert v-if="pushError" type="error" :closable="true" show-icon :title="pushError" @close="pushError = ''" />
      </el-form>
      <template #footer>
        <el-button @click="showPush = false">取消</el-button>
        <el-button type="primary" :loading="pushing" @click="doPush">{{ pushing ? '推送中…' : '推送' }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 镜像分层历史 ==================== -->
    <el-dialog v-model="showHistory" :title="`镜像分层：${historyName}`" width="860px" top="8vh">
      <el-table :data="historyRows" v-loading="historyLoading" size="small" border max-height="480">
        <el-table-column label="#" type="index" width="50" />
        <el-table-column label="层 ID" width="130">
          <template #default="{ row }"><code>{{ (row.Id || '').replace('sha256:', '').slice(0, 12) }}</code></template>
        </el-table-column>
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ row.Created ? new Date(row.Created).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="95">
          <template #default="{ row }">{{ row.Size ? formatSize(row.Size) : '-' }}</template>
        </el-table-column>
        <el-table-column label="构建指令" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'hist-empty': row.CreatedBy === '<missing>' }">{{ row.CreatedBy || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="history-total" v-if="historyRows.length">
        共 {{ historyRows.length }} 层，累加大小 {{ formatSize(historyRows.reduce((s, r) => s + (r.Size || 0), 0)) }}（实际镜像大小因层共享小于此值）
      </div>
      <template #footer><el-button @click="showHistory = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { getImages, pullImage, removeImage, inspectImage, pushImage, getImageHistory } from '@/api/image'
import { pruneImages } from '@/api/system'
import { runPruneChoice } from '@/utils/prune'
import { getContainers } from '@/api/container'
import { getRegistries } from '@/api/registry'
import { ElMessage } from 'element-plus'

const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const images = ref<any[]>([])
const containers = ref<any[]>([])
const registries = ref<any[]>([])
const loading = ref(false)
const showPull = ref(false)
const pulling = ref(false)
const onlyInUse = ref(false)
const pullForm = reactive({ imageName: '', tag: 'latest', registryId: 0 as number })

/** 行展示文本：内置行直接用名称，已配置行额外带地址 */
function registryLabel(r: any) {
  if (r.builtin) return `${r.name}（内置默认）`
  return `${r.name}（${r.url || 'Docker Hub'}）`
}
const selectedRegistry = computed(() => registries.value.find((r) => r.id === pullForm.registryId))
const pullInputPlaceholder = computed(() => {
  const reg = selectedRegistry.value
  if (!reg || reg.builtin || !reg.url) return '如 nginx, ubuntu, redis 或 library/nginx'
  return `仓库内路径，如 my-project/my-app`
})

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

/* ---------------- 容器使用情况（按镜像 ID 匹配） ---------------- */
const usageMap = computed(() => {
  const map: Record<string, { running: number; total: number; list: any[] }> = {}
  for (const c of containers.value) {
    const id = c.ImageID
    if (!id) continue
    if (!map[id]) map[id] = { running: 0, total: 0, list: [] }
    map[id].total++
    if (c.State === 'running') map[id].running++
    map[id].list.push({
      id: c.Id,
      name: (c.Names && c.Names.length ? c.Names[0] : c.Id).replace(/^\//, ''),
      state: c.State,
      command: c.Command,
    })
  }
  return map
})

function usageOf(imageId: string) {
  return usageMap.value[imageId] || { running: 0, total: 0, list: [] }
}

const filteredImages = computed(() =>
  onlyInUse.value ? images.value.filter((i) => usageOf(i.Id).total > 0) : images.value
)

const { page, pageSize, total, pageData } = usePagination(filteredImages)

function inUseTip(row: any) {
  const u = usageOf(row.Id)
  const names = u.list.slice(0, 3).map((c: any) => `${c.name}（${c.state === 'running' ? '运行中' : '已停止'}）`).join('、')
  const more = u.list.length > 3 ? ` 等 ${u.list.length} 个` : ''
  return `正被 ${u.total} 个容器使用：${names}${more}。已停止的容器同样占用镜像，请先删除容器；`
    + `全部引用可在「详情」抽屉里查看。平台不提供强制删除。`
}

/* ---------------- 详情抽屉 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<any>(null)
const detailRow = ref<any>(null)
const detailName = computed(() => {
  const tags = detail.value?.RepoTags || detailRow.value?.RepoTags
  return (tags && tags.length ? tags[0] : (detail.value?.Id || '').replace('sha256:', '').slice(0, 12))
})
const detailUsage = computed(() => (detail.value ? usageOf(detail.value.Id).list : []))
const envList = computed(() =>
  (detail.value?.Config?.Env || []).map((e: string) => {
    const i = e.indexOf('=')
    return i >= 0 ? { k: e.slice(0, i), v: e.slice(i + 1) } : { k: e, v: '' }
  })
)

function joinArr(arr?: string[]) {
  return (arr || []).join(' ')
}

async function openDetail(row: any) {
  detailRow.value = row
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await inspectImage(envStore.currentEnvId!, row.Id)
  } catch (e: any) {
    ElMessage.error(e.message || '获取镜像详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function copyDetailJson() {
  try {
    await navigator.clipboard.writeText(JSON.stringify(detail.value, null, 2))
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

/* ---------------- 数据加载 ---------------- */
function doPrune() {
  runPruneChoice('清理镜像（docker image prune）',
    '仅悬空：删除没有标签、未被任何容器引用的悬空镜像（dangling）。\n全部未使用：额外删除所有未被任何容器使用的镜像，下次使用需重新拉取。',
    (all) => pruneImages(envStore.currentEnvId!, all), loadData,
    { left: '仅悬空', right: '全部未使用 (-a)' })
}

async function loadData() {
  if (!envStore.currentEnvId) return
  loading.value = true
  try {
    const [imgList, conList, regs] = await Promise.all([
      getImages(envStore.currentEnvId),
      getContainers(envStore.currentEnvId, true).catch(() => []),
      getRegistries(),
    ])
    images.value = imgList || []
    containers.value = conList || []
    registries.value = regs || []
  } finally { loading.value = false }
}

async function doPull() {
  pulling.value = true
  try {
    // 选中仓库时自动拼接仓库地址前缀，并将 registryId 一并上传供后端取对应凭据
    let name = pullForm.imageName.trim()
    const reg = registries.value.find((r) => r.id === pullForm.registryId)
    if (reg && reg.url && !name.startsWith(reg.url + '/')) {
      name = `${reg.url}/${name}`
    }
    await pullImage(envStore.currentEnvId, name, pullForm.tag, pullForm.registryId)
    ElMessage.success('镜像拉取成功')
    showPull.value = false
    loadData()
  } finally { pulling.value = false }
}

async function doRemove(id: string) {
  await removeImage(envStore.currentEnvId, id)
  ElMessage.success('删除成功')
  loadData()
}

/* ---------------- 推送镜像 ---------------- */
const showPush = ref(false)
const pushing = ref(false)
const pushError = ref('')
const pushRow = ref<any>(null)
const pushForm = reactive({ repo: '', tag: 'latest', registryId: 0 as number })
const pushSourceName = computed(() => {
  const tags = pushRow.value?.RepoTags || []
  return tags.length ? tags[0] : (pushRow.value?.Id || '').replace('sha256:', '').slice(0, 12)
})

function openPush(row: any) {
  pushRow.value = row
  pushError.value = ''
  const tags = row.RepoTags || []
  // 默认带入第一个 repo:tag，用户在此基础上改目标路径
  if (tags.length) {
    const [repo, tag] = splitTagRef(tags[0])
    pushForm.repo = repo
    pushForm.tag = tag
  } else {
    pushForm.repo = ''
    pushForm.tag = 'latest'
  }
  showPush.value = true
}

function splitTagRef(full: string): [string, string] {
  const lastSlash = full.lastIndexOf('/')
  const colon = full.indexOf(':', lastSlash + 1)
  return colon > 0 ? [full.slice(0, colon), full.slice(colon + 1)] : [full, 'latest']
}

function onPushRegistry() {
  const reg = registries.value.find((r) => r.id === pushForm.registryId)
  if (reg && !reg.builtin && reg.url && pushForm.repo && !pushForm.repo.startsWith(reg.url)) {
    pushForm.repo = `${reg.url}/${pushForm.repo.replace(/^https?:\/\//, '')}`
  }
}

async function doPush() {
  if (!pushForm.repo.trim() || !pushForm.tag.trim()) {
    ElMessage.warning('请填写目标路径与标签')
    return
  }
  pushing.value = true
  pushError.value = ''
  try {
    await pushImage(envStore.currentEnvId!, pushRow.value.Id, pushForm.repo.trim(), pushForm.tag.trim(),
      pushForm.registryId || undefined)
    ElMessage.success(`推送成功：${pushForm.repo}:${pushForm.tag}`)
    showPush.value = false
    loadData()
  } catch (e: any) {
    pushError.value = e?.message || '推送失败'
  } finally { pushing.value = false }
}

/* ---------------- 镜像分层历史 ---------------- */
const showHistory = ref(false)
const historyLoading = ref(false)
const historyRows = ref<any[]>([])
const historyName = ref('')

async function openHistory(row: any) {
  const tags = row.RepoTags || []
  historyName.value = tags.length ? tags[0] : (row.Id || '').replace('sha256:', '').slice(0, 12)
  showHistory.value = true
  historyLoading.value = true
  historyRows.value = []
  try {
    historyRows.value = (await getImageHistory(envStore.currentEnvId!, row.Id)) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '获取分层历史失败')
  } finally { historyLoading.value = false }
}

onMounted(loadData)
watch(() => envStore.currentEnvId, loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
.repo-tag { line-height: 1.6; font-size: 13px; }
.repo-tag.more { color: #909399; font-size: 12px; }
.repo-tag.none { color: #c0c4cc; }
.usage-extra { font-size: 12px; color: #909399; margin-left: 4px; }
/* 置灰按钮被 span 包了一层，相邻兄弟选择器 .el-button+.el-button 不再命中，把间距补回来 */
.btn-wrap { display: inline-block; margin-left: 12px; }
.detail-sec { margin-bottom: 18px; }
.detail-title { margin: 0 0 10px; font-size: 14px; color: #303133; }
.digest { word-break: break-all; font-size: 12px; }
.cmd { font-size: 12px; word-break: break-all; }
.env-list { max-height: 220px; overflow: auto; font-size: 12px; line-height: 1.8; }
.env-k { color: #409eff; font-weight: 600; }
.env-v { color: #606266; word-break: break-all; }
.layer-list { max-height: 200px; overflow: auto; margin-bottom: 18px; }
.layer-item { font-size: 12px; line-height: 1.9; }
.layer-idx { display: inline-block; width: 32px; color: #909399; }
.raw-head { display: flex; justify-content: flex-end; margin-bottom: 6px; }
.raw-json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.push-src { font-size: 13px; font-weight: 600; word-break: break-all; }
.push-hint { font-size: 12px; color: #909399; line-height: 1.5; margin-top: 2px; }
.hist-empty { color: #c0c4cc; }
.history-total { margin-top: 10px; font-size: 12px; color: #909399; }
</style>
