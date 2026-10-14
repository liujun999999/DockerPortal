<template>
  <div class="config-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Configs</span>
          <div>
            <el-button @click="loadData" :icon="Refresh">刷新</el-button>
            <el-button type="primary" @click="openCreate" v-if="canOperate">新建 Config</el-button>
          </div>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="Config 用于把配置文件（nginx.conf、脚本等）注入服务任务。内容可查看；Docker 不支持原地修改，更新 = 删除后同名重建，服务需重新引用新 ID。" />
      <el-table :data="pageData" stripe v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="id" label="ID" width="200">
          <template #default="{ row }"><code>{{ row.id?.slice(0, 12) }}</code></template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ row.size }} B</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ fmtTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewContent(row)">查看</el-button>
            <el-button size="small" text type="danger" @click="doDelete(row)" v-if="canOperate">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
      <el-empty v-if="!loading && !items.length" description="暂无 Config" />
    </el-card>

    <!-- 内容查看 -->
    <el-dialog v-model="viewVisible" :title="`内容：${viewing?.name || ''}`" width="640px">
      <pre class="config-content">{{ viewing?.data ?? '(无法读取内容)' }}</pre>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
        <el-button type="primary" v-if="canOperate" @click="recreate">以此内容新建版本</el-button>
      </template>
    </el-dialog>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建 Config" width="640px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 nginx_conf" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.data" type="textarea" :rows="12" class="content-editor"
            placeholder="配置文件文本内容" />
        </el-form-item>
        <el-form-item label="Templatable">
          <el-switch v-model="form.templatable" />
          <span class="hint">开启后内容可用 &#123;&#123; .ServiceName &#125;&#125; 等模板变量</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listConfigs, getConfig, createConfig, deleteConfig } from '@/api/swarmResource'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SwarmGuide from '@/components/swarm/SwarmGuide.vue'

const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const items = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(items)
const loading = ref(false)
const viewVisible = ref(false)
const viewing = ref<any>(null)
const createVisible = ref(false)
const saving = ref(false)
const guideCode = ref(0)
const form = ref({ name: '', data: '', templatable: false })

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    items.value = await listConfigs(envId)
    guideCode.value = 0
  } catch (e: any) {
    // Worker 节点 / 未初始化集群：不弹红错，展示引导组件
    items.value = []
    if (e.swarmGuide) guideCode.value = e.swarmGuide
  } finally { loading.value = false }
}

async function viewContent(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  viewing.value = row
  viewVisible.value = true
  try {
    viewing.value = await getConfig(envId, row.id)
  } catch { /* 列表数据已有 data 时兜底 */ }
}

function recreate() {
  form.value = { name: viewing.value?.name || '', data: viewing.value?.data || '', templatable: false }
  viewVisible.value = false
  createVisible.value = true
}

function openCreate() {
  form.value = { name: '', data: '', templatable: false }
  createVisible.value = true
}

async function doCreate() {
  const envId = envStore.currentEnvId
  if (!envId) return
  if (!form.value.name || !form.value.data) {
    ElMessage.warning('请填写名称和内容')
    return
  }
  saving.value = true
  try {
    await createConfig(envId, {
      name: form.value.name.trim(),
      data: form.value.data,
      templatable: form.value.templatable || undefined,
    })
    ElMessage.success('Config 已创建')
    createVisible.value = false
    loadData()
  } catch { /* 拦截器已提示 */ } finally { saving.value = false }
}

async function doDelete(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  try {
    await ElMessageBox.confirm(
      `删除 Config “${row.name}” 后，引用它的服务任务将在下次调度时报错。确定删除？`,
      '删除 Config', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    await deleteConfig(envId, row.id)
    ElMessage.success('已删除')
    loadData()
  } catch { /* 拦截器已提示 */ }
}

function fmtTime(t?: string) {
  if (!t) return '-'
  try { return new Date(t).toLocaleString() } catch { return t }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.config-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.content-editor :deep(textarea) { font-family: Menlo, Monaco, monospace; font-size: 13px; }
.hint { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
