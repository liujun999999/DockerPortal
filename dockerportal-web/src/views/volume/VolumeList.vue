<template>
  <div>
    <div class="page-header">
      <h2>数据卷</h2>
      <div class="header-actions">
        <el-button @click="doPrune" v-if="isAdmin">清理未使用数据卷</el-button>
        <el-button type="primary" @click="showCreate = true" v-if="canOperate">创建数据卷</el-button>
      </div>
    </div>
    <el-table :data="volumes" v-loading="loading" stripe>
      <el-table-column label="名称" prop="Name" min-width="200" />
      <el-table-column label="驱动" prop="Driver" width="120" />
      <el-table-column label="挂载点" prop="Mountpoint" min-width="300" />
      <el-table-column label="操作" width="120" v-if="isAdmin">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="doRemove(row.Name)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="showCreate" title="创建数据卷" width="400px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="驱动"><el-input v-model="createForm.driver" placeholder="local" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { getVolumes, createVolume, removeVolume } from '@/api/volume'
import { pruneVolumes } from '@/api/system'
import { runPrune } from '@/utils/prune'
import { requireTypedConfirm } from '@/utils/danger'
import { ElMessage } from 'element-plus'

const envStore = useEnvironmentStore()
const { canOperate, isAdmin } = usePermission()
const volumes = ref<any[]>([])
const loading = ref(false)
const showCreate = ref(false)
const createForm = reactive({ name: '', driver: '' })

async function loadData() {
  if (!envStore.currentEnvId) return
  loading.value = true
  try {
    const res = await getVolumes(envStore.currentEnvId)
    volumes.value = res.Volumes || []
  } finally { loading.value = false }
}
async function doCreate() {
  await createVolume(envStore.currentEnvId, createForm)
  ElMessage.success('创建成功'); showCreate.value = false; loadData()
}
async function doRemove(name: string) {
  // 卷删除不可恢复，属 L2 危险操作：必须输入卷名确认
  try {
    await requireTypedConfirm({
      title: '删除数据卷',
      message: `删除数据卷 “${name}” 将永久移除卷内数据且不可恢复。请输入卷名以确认。`,
      requireText: name,
    })
  } catch { return }
  await removeVolume(envStore.currentEnvId, name, true)
  ElMessage.success('删除成功'); loadData()
}
onMounted(loadData)
watch(() => envStore.currentEnvId, loadData)
function doPrune() {
  runPrune('清理数据卷（docker volume prune）',
    '将删除所有未被任何容器使用的匿名数据卷（命名卷不受影响），卷内数据不可恢复。此操作高危，请输入 PRUNE 确认。',
    () => pruneVolumes(envStore.currentEnvId!), loadData, 'PRUNE')
}
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
</style>
