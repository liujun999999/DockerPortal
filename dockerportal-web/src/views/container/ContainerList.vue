<template>
  <div>
    <div class="page-header">
      <h2>容器</h2>
      <div class="header-actions" v-if="canOperate">
        <el-button @click="doPrune">清理已停止容器</el-button>
        <el-button type="primary" @click="router.push('/containers/create')">创建容器</el-button>
      </div>
    </div>
    <el-alert v-if="stateFilter" :title="`当前按状态过滤：${stateFilter === 'running' ? '运行中' : '已停止'}（共 ${containers.length} 个容器）`" type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #default>
        <el-link type="primary" style="font-size: 12px" @click="clearFilter">查看全部</el-link>
      </template>
    </el-alert>
    <el-table :data="pageData" v-loading="loading" stripe @selection-change="sel = $event">
      <el-table-column v-if="canOperate" type="selection" width="45" :selectable="() => true" />
      <el-table-column label="名称" prop="Names" min-width="180">
        <template #default="{ row }">
          <router-link :to="`/containers/${row.Id}`" class="link">{{ (row.Names || [])[0]?.replace(/^\//, '') || row.Id.slice(0, 12) }}</router-link>
        </template>
      </el-table-column>
      <el-table-column label="镜像" prop="Image" min-width="200" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.State === 'running' ? 'success' : 'info'" size="small">{{ row.State }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="端口" min-width="200">
        <template #default="{ row }">
          <span v-if="row.Ports?.length">{{ row.Ports.map((p: any) => p.PublicPort ? `${p.PublicPort}->${p.PrivatePort}/${p.Type}` : `${p.PrivatePort}/${p.Type}`).join(', ') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ new Date(row.Created * 1000).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right" v-if="canOperate">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="doAction(row.Id, 'start')" v-if="row.State !== 'running'">启动</el-button>
          <el-button size="small" type="warning" @click="doAction(row.Id, 'stop')" v-if="row.State === 'running'">停止</el-button>
          <el-button size="small" @click="doAction(row.Id, 'restart')">重启</el-button>
          <el-button size="small" @click="router.push(`/containers/create?clone=${row.Id}`)">克隆</el-button>
          <el-button size="small" @click="openResize(row)">调资源</el-button>
          <el-popconfirm title="确认删除?" @confirm="doRemove(row.Id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />

    <!-- 批量操作工具栏 -->
    <div v-if="canOperate && sel.length" class="batch-bar">
      <span>已选 {{ sel.length }} 个容器</span>
      <el-button size="small" type="success" :loading="batching" @click="doBatch('start')">批量启动</el-button>
      <el-button size="small" type="warning" :loading="batching" @click="doBatch('stop')">批量停止</el-button>
      <el-button size="small" :loading="batching" @click="doBatch('restart')">批量重启</el-button>
      <el-button size="small" type="danger" :loading="batching" @click="doBatchRemove">批量删除</el-button>
      <el-button size="small" text @click="sel = []">取消选择</el-button>
    </div>

    <!-- docker update：热调资源限制 -->
    <el-dialog v-model="resizeVisible" :title="`调整资源限制：${resizeName}`" width="480px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="对应 docker update：留空（0）的项不修改，运行中容器即时生效。" />
      <el-form :model="resizeForm" label-width="110px">
        <el-form-item label="内存上限">
          <el-input-number v-model="resizeForm.memoryMb" :min="0" :step="64" :controls="false" style="width:140px" />
          <span class="unit">MB</span>
        </el-form-item>
        <el-form-item label="内存+swap">
          <el-input-number v-model="resizeForm.memorySwapMb" :min="0" :step="64" :controls="false" style="width:140px" />
          <span class="unit">MB</span>
        </el-form-item>
        <el-form-item label="内存预留">
          <el-input-number v-model="resizeForm.memoryReservationMb" :min="0" :step="64" :controls="false" style="width:140px" />
          <span class="unit">MB</span>
        </el-form-item>
        <el-form-item label="CPU 核数">
          <el-input-number v-model="resizeForm.cpuCores" :min="0" :step="0.1" :precision="2" :controls="false" style="width:140px" />
          <span class="unit">核，支持小数</span>
        </el-form-item>
        <el-form-item label="CPU 份额">
          <el-input-number v-model="resizeForm.cpuShares" :min="0" :step="128" :controls="false" style="width:140px" />
          <span class="unit">默认 1024</span>
        </el-form-item>
        <el-form-item label="绑定核心">
          <el-input v-model="resizeForm.cpusetCpus" placeholder="如 0-2 或 0,1（留空不改）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resizeVisible = false">取消</el-button>
        <el-button type="primary" :loading="resizing" @click="doResize">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { getContainers, containerAction, removeContainer, updateContainerResources } from '@/api/container'
import { pruneContainers } from '@/api/system'
import { runPrune } from '@/utils/prune'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const containers = ref<any[]>([])
const loading = ref(false)
const sel = ref<any[]>([])
const batching = ref(false)

async function doBatch(action: string) {
  const envId = envStore.currentEnvId!
  batching.value = true
  const results = await Promise.allSettled(sel.value.map((c) => containerAction(envId, c.Id, action)))
  const ok = results.filter((r) => r.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage[fail ? 'warning' : 'success'](`${action} 成功 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
  batching.value = false
  sel.value = []
  loadData()
}

async function doBatchRemove() {
  const envId = envStore.currentEnvId!
  try {
    await ElMessageBox.confirm(
      `将强制删除选中的 ${sel.value.length} 个容器（含运行中的），不可恢复。确定继续？`,
      '批量删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch { return }
  batching.value = true
  const results = await Promise.allSettled(sel.value.map((c) => removeContainer(envId, c.Id, true)))
  const ok = results.filter((r) => r.status === 'fulfilled').length
  const fail = results.length - ok
  ElMessage[fail ? 'warning' : 'success'](`删除成功 ${ok} 个${fail ? `，失败 ${fail} 个` : ''}`)
  batching.value = false
  sel.value = []
  loadData()
}

// docker update 资源调整
const resizeVisible = ref(false)
const resizing = ref(false)
const resizeName = ref('')
const resizeId = ref('')
const resizeForm = ref({ memoryMb: 0, memorySwapMb: 0, memoryReservationMb: 0, cpuCores: 0, cpuShares: 0, cpusetCpus: '' })

function openResize(row: any) {
  resizeId.value = row.Id
  resizeName.value = (row.Names || [])[0]?.replace(/^\//, '') || row.Id.slice(0, 12)
  resizeForm.value = { memoryMb: 0, memorySwapMb: 0, memoryReservationMb: 0, cpuCores: 0, cpuShares: 0, cpusetCpus: '' }
  resizeVisible.value = true
}

async function doResize() {
  const f = resizeForm.value
  if (!f.memoryMb && !f.memorySwapMb && !f.memoryReservationMb && !f.cpuCores && !f.cpuShares && !f.cpusetCpus.trim()) {
    ElMessage.warning('请至少填写一项')
    return
  }
  resizing.value = true
  try {
    await updateContainerResources(envStore.currentEnvId!, resizeId.value, {
      memoryMb: f.memoryMb || undefined,
      memorySwapMb: f.memorySwapMb || undefined,
      memoryReservationMb: f.memoryReservationMb || undefined,
      cpuCores: f.cpuCores || undefined,
      cpuShares: f.cpuShares || undefined,
      cpusetCpus: f.cpusetCpus.trim() || undefined,
    })
    ElMessage.success('资源限制已更新')
    resizeVisible.value = false
  } catch { /* 拦截器已提示 */ } finally { resizing.value = false }
}

const stateFilter = computed(() => {
  const s = route.query.state
  return s === 'running' || s === 'exited' ? s : ''
})

const filteredContainers = computed(() => {
  if (!stateFilter.value) return containers.value
  return containers.value.filter((c) =>
    stateFilter.value === 'running' ? c.State === 'running' : c.State !== 'running'
  )
})

const { page, pageSize, total, pageData } = usePagination(filteredContainers)

function clearFilter() {
  router.replace({ path: '/containers' })
}

async function loadData() {
  if (!envStore.currentEnvId) return
  loading.value = true
  try {
    containers.value = await getContainers(envStore.currentEnvId)
  } finally {
    loading.value = false
  }
}

async function doAction(id: string, action: string) {
  await containerAction(envStore.currentEnvId, id, action)
  ElMessage.success(`操作成功: ${action}`)
  loadData()
}

async function doRemove(id: string) {
  await removeContainer(envStore.currentEnvId, id, true)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
watch(() => envStore.currentEnvId, loadData)
function doPrune() {
  runPrune('清理容器（docker container prune）',
    '将删除所有已停止的容器及其可写层数据，此操作不可恢复。是否继续？',
    () => pruneContainers(envStore.currentEnvId!), loadData)
}
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
.link { color: #409eff; text-decoration: none; }
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  font-size: 13px;
  color: #409eff;
}
.unit { font-size: 12px; color: #909399; margin-left: 6px; }
</style>
