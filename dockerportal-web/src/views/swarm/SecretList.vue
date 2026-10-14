<template>
  <div class="secret-list">
    <SwarmGuide v-if="guideCode" :code="guideCode" @done="loadData" @retry="loadData" />
    <el-card v-else shadow="never">
      <template #header>
        <div class="card-header">
          <span>Secrets</span>
          <div>
            <el-button @click="loadData" :icon="Refresh">刷新</el-button>
            <el-button type="primary" @click="openCreate" v-if="canOperate">新建 Secret</el-button>
          </div>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="Secret 以加密方式存入 Raft 集群并注入服务任务。内容创建后不可再读取（与 docker CLI 一致），轮换 = 新建同名版本 + 更新服务引用。" />
      <el-table :data="pageData" stripe v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="id" label="ID" width="220">
          <template #default="{ row }"><code>{{ row.id?.slice(0, 12) }}</code></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" v-if="canOperate">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
      <el-empty v-if="!loading && !items.length" description="暂无 Secret" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建 Secret" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 db_password" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.data" type="textarea" :rows="5" placeholder="敏感文本，保存后无法再查看" />
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
import { listSecrets, createSecret, deleteSecret } from '@/api/swarmResource'
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
const createVisible = ref(false)
const saving = ref(false)
const guideCode = ref(0)
const form = ref({ name: '', data: '' })

async function loadData() {
  const envId = envStore.currentEnvId
  if (!envId) return
  loading.value = true
  try {
    items.value = await listSecrets(envId)
    guideCode.value = 0
  } catch (e: any) {
    // Worker 节点 / 未初始化集群：不弹红错，展示引导组件
    items.value = []
    if (e.swarmGuide) guideCode.value = e.swarmGuide
  } finally { loading.value = false }
}

function openCreate() {
  form.value = { name: '', data: '' }
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
    await createSecret(envId, { name: form.value.name.trim(), data: form.value.data })
    ElMessage.success('Secret 已创建')
    createVisible.value = false
    loadData()
  } catch { /* 拦截器已提示 */ } finally { saving.value = false }
}

async function doDelete(row: any) {
  const envId = envStore.currentEnvId
  if (!envId) return
  try {
    await ElMessageBox.confirm(
      `删除 Secret “${row.name}” 后，引用它的服务任务将在下次调度时报错。确定删除？`,
      '删除 Secret', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    await deleteSecret(envId, row.id)
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
</style>
