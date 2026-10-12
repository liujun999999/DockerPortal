<template>
  <div class="stack-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Stack 列表</span>
          <div>
            <el-button @click="loadData" :icon="Refresh">刷新</el-button>
            <el-button type="primary" @click="openDeploy" v-if="canOperate">部署 Stack</el-button>
          </div>
        </div>
      </template>
      <el-table :data="stacks" stripe v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="serviceCount" label="服务数" width="100" />
        <el-table-column label="包含服务" min-width="300">
          <template #default="{ row }">
            <el-tag v-for="s in row.services" :key="s" size="small" style="margin: 2px">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" v-if="canOperate">
          <template #default="{ row }">
            <el-tooltip :disabled="row.hasCompose" content="该 Stack 不是由本平台部署的，没有 compose 原文记录"
              placement="top">
              <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            </el-tooltip>
            <el-button size="small" text type="danger" @click="removeStack(row.name)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 部署 / 编辑共用：编辑就是把已保存的 compose 改一遍再同名重新部署 -->
    <el-dialog v-model="deployVisible" :title="editing ? '编辑 Stack' : '部署 Stack'" width="720px"
      :close-on-click-modal="false">
      <el-form :model="deployForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="deployForm.name" :disabled="editing"
            placeholder="小写字母开头，可含数字/连字符/下划线，如 webapp" />
          <span v-if="editing" class="name-hint">Stack 不支持改名，需换名请用新名字部署后再删除旧的</span>
        </el-form-item>
        <el-form-item label="Compose">
          <div class="compose-field">
            <div class="compose-tools">
              <el-button size="small" :icon="Upload" @click="pickFile">上传 YAML</el-button>
              <span v-if="composeMeta?.stored" class="field-hint">已保存的原文，上次部署于 {{ composeMeta.updatedAt }}</span>
              <input ref="fileInput" type="file" accept=".yml,.yaml,.txt,text/yaml,text/plain" class="hidden-file"
                @change="onFilePick" />
            </div>
            <CodeEditor v-model="deployForm.composeContent" language="yaml" height="380px"
              placeholder="粘贴 docker-compose.yml 内容，或点「上传 YAML」选择本地文件" />
          </div>
        </el-form-item>
        <!-- 只在确实读到 stored=false 时断言“没有记录”，读取失败时不报错误的结论 -->
        <el-alert v-if="editing && !loadingCompose && composeMeta && !composeMeta.stored" type="warning"
          :closable="false" show-icon
          title="没有该 Stack 的 compose 原文记录"
          description="它不是由本平台部署的，Docker 只保存翻译后的服务定义。请粘贴或上传完整 compose 后重新部署；compose 里未声明的服务会被一并删除。" />
        <el-alert type="info" :closable="false" show-icon
          title="支持：image/网络/命名卷/端口/环境变量/mount/healthcheck/replicas/资源限制/update-config/secrets/configs；不支持 build、相对路径挂载与变量插值。重复部署同名 Stack 会增量更新。" />
        <template v-if="deploySummary">
          <el-form-item label="结果">
            <el-alert v-if="!deploySummary.warnings?.length" type="success" :closable="false" show-icon
              :title="`新建 ${deploySummary.created?.length || 0} 个服务，更新 ${deploySummary.updated?.length || 0} 个，移除 ${deploySummary.removed?.length || 0} 个`" />
            <ul v-if="deploySummary.warnings?.length" class="summary-warn">
              <li v-for="(w, i) in deploySummary.warnings" :key="i">{{ w }}</li>
            </ul>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="deployVisible = false">取消</el-button>
        <el-button type="primary" :loading="deploying" @click="doDeploy">{{ editing ? '重新部署' : '部署' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getStacks, removeStack as apiRemove, deployStack as apiDeploy, getStackCompose } from '@/api/stack'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { requireTypedConfirm } from '@/utils/danger'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'
import CodeEditor from '@/components/common/CodeEditor.vue'

const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const stacks = ref<any[]>([])
const loading = ref(false)
const guideCode = ref(0)
const deployVisible = ref(false)
const deploying = ref(false)
const deploySummary = ref<any>(null)
const deployForm = ref({ name: '', composeContent: '' })
const editing = ref(false)
const loadingCompose = ref(false)
const composeMeta = ref<{ stored: boolean; updatedAt?: string } | null>(null)
const fileInput = ref<HTMLInputElement>()

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    stacks.value = await getStacks(envId)
    guideCode.value = 0
  } catch (e: any) {
    if (e.swarmGuide) {
      guideCode.value = e.swarmGuide
      stacks.value = []
    } else {
      console.error(e)
    }
  }
  finally { loading.value = false }
}

async function removeStack(name: string) {
  const envId = envStore.currentEnvId
  if (!envId) return
  // Stack 删除会级联删除其下所有服务，属 L2 危险操作：输入 Stack 名确认
  try {
    await requireTypedConfirm({
      title: '删除 Stack',
      message: `删除 Stack “${name}” 将同时删除其下所有服务，不可恢复。请输入 Stack 名以确认。`,
      requireText: name,
    })
  } catch { return }
  try {
    await apiRemove(envId, name)
    ElMessage.success('Stack 已删除')
    loadData()
  } catch (e) { ElMessage.error('删除失败') }
}

function openDeploy() {
  editing.value = false
  deploySummary.value = null
  composeMeta.value = null
  // 与编辑模式共用一个对话框，不重置会把上一个 Stack 的内容带进新建部署
  deployForm.value = { name: '', composeContent: '' }
  deployVisible.value = true
}

/** 编辑 = 回填已保存的 compose，改完同名重新部署 */
async function openEdit(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  editing.value = true
  deploySummary.value = null
  composeMeta.value = null
  deployForm.value = { name: row.name, composeContent: '' }
  deployVisible.value = true
  loadingCompose.value = true
  try {
    const res: any = await getStackCompose(envId, row.name)
    composeMeta.value = { stored: !!res?.stored, updatedAt: res?.updatedAt }
    if (res?.content) deployForm.value.composeContent = res.content
  } catch {
    // 错误提示由拦截器统一弹；这里保持编辑器为空，用户仍可手动粘贴或上传
  } finally {
    loadingCompose.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

/** 纯前端读文本：compose 就是个 YAML 文件，不值得为它开一个 multipart 接口 */
async function onFilePick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 不清空的话，持续选同一个文件不会再触发 change
  if (!file) return
  if (file.size > 1024 * 1024) {
    ElMessage.error('文件超过 1MB，请确认没选错文件')
    return
  }
  const text = await file.text()
  if (deployForm.value.composeContent.trim()) {
    try {
      await ElMessageBox.confirm(`用 ${file.name} 覆盖编辑器里的当前内容？`, '覆盖确认', { type: 'warning' })
    } catch { return }
  }
  deployForm.value.composeContent = text
  if (!editing.value && !deployForm.value.name) {
    deployForm.value.name = guessStackName(file.name)
  }
  if (/^\s*services\s*:/m.test(text)) {
    ElMessage.success(`已载入 ${file.name}`)
  } else {
    ElMessage.warning(`已载入 ${file.name}，但内容里没有 services 段，部署会被后端拒绝`)
  }
}

/** 拿文件名当默认 Stack 名，需符合后端的 ^[a-z0-9][a-z0-9_-]*$；
    docker-compose.yml / compose.yml 这类通用文件名不配做 Stack 名，宁可让用户自己填 */
function guessStackName(fileName: string): string {
  const base = fileName.replace(/\.(ya?ml|txt)$/i, '').toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-').slice(0, 63).replace(/^[-_]+|[-_]+$/g, '')
  if (!/^[a-z0-9][a-z0-9_-]*$/.test(base)) return ''
  return ['docker-compose', 'compose', 'stack'].includes(base) ? '' : base
}

async function doDeploy() {
  const envId = envStore.currentEnvId
  if (!envId || !deployForm.value.name) {
    ElMessage.warning('请填写 Stack 名称')
    return
  }
  if (!deployForm.value.composeContent?.trim()) {
    ElMessage.warning('请粘贴 compose 内容')
    return
  }
  deploying.value = true
  deploySummary.value = null
  try {
    const summary: any = await apiDeploy(envId, {
      name: deployForm.value.name,
      composeContent: deployForm.value.composeContent,
    })
    deploySummary.value = summary || {}
    // 部署成功直接关闭对话框，摘要用全局消息提示；失败时保留对话框展示错误详情
    const s: any = deploySummary.value
    const detail = `新建 ${s.created?.length || 0} 个，更新 ${s.updated?.length || 0} 个，移除 ${s.removed?.length || 0} 个`
    if (s.warnings?.length) {
      ElNotification({
        type: 'warning',
        title: `部署完成（${detail}）`,
        message: s.warnings.join('；'),
        duration: 10000,
      })
    } else {
      ElMessage.success(`部署完成：${detail}`)
    }
    deploySummary.value = null
    deployVisible.value = false
    loadData()
  } catch (e: any) {
    deploySummary.value = { warnings: [(e as any)?.message || '部署失败'].filter(Boolean) }
  } finally { deploying.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.summary-warn { margin: 8px 0 0; padding-left: 20px; color: #e6a23c; font-size: 13px; }
.compose-field { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.compose-tools { display: flex; align-items: center; gap: 10px; }
.hidden-file { display: none; }
.field-hint { font-size: 12px; color: #909399; }
.name-hint { width: 100%; font-size: 12px; color: #909399; line-height: 18px; }
</style>
