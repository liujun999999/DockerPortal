<template>
  <div>
    <div class="page-header">
      <h2>网络</h2>
      <div class="header-actions">
        <el-button @click="doPrune" v-if="canOperate">清理未使用网络</el-button>
        <el-button type="primary" @click="showCreate = true" v-if="canOperate">创建网络</el-button>
      </div>
    </div>
    <el-table :data="pageData" v-loading="loading" stripe>
      <el-table-column label="名称" prop="Name" min-width="200" />
      <el-table-column label="ID" width="150">
        <template #default="{ row }">{{ row.Id?.slice(0, 12) }}</template>
      </el-table-column>
      <el-table-column label="驱动" prop="Driver" width="120" />
      <el-table-column label="作用域" prop="Scope" width="120" />
      <el-table-column label="操作" width="120" v-if="canOperate">
        <template #default="{ row }">
          <el-popconfirm title="确认删除?" @confirm="doRemove(row.Id)" v-if="!['bridge','host','none'].includes(row.Name)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
    <el-dialog v-model="showCreate" title="创建网络" width="400px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="驱动">
          <el-select v-model="createForm.driver">
            <el-option label="bridge" value="bridge" />
            <el-option label="overlay" value="overlay" />
            <el-option label="macvlan" value="macvlan" />
          </el-select>
        </el-form-item>
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
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { getNetworks, createNetwork, removeNetwork } from '@/api/network'
import { pruneNetworks } from '@/api/system'
import { runPrune } from '@/utils/prune'
import { ElMessage } from 'element-plus'

const envStore = useEnvironmentStore()
const { canOperate } = usePermission()
const networks = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(networks)
const loading = ref(false)
const showCreate = ref(false)
const createForm = reactive({ name: '', driver: 'bridge' })

async function loadData() {
  if (!envStore.currentEnvId) return
  loading.value = true
  try { networks.value = await getNetworks(envStore.currentEnvId) } finally { loading.value = false }
}
async function doCreate() {
  await createNetwork(envStore.currentEnvId, createForm)
  ElMessage.success('创建成功'); showCreate.value = false; loadData()
}
async function doRemove(id: string) {
  await removeNetwork(envStore.currentEnvId, id)
  ElMessage.success('删除成功'); loadData()
}
onMounted(loadData)
watch(() => envStore.currentEnvId, loadData)
function doPrune() {
  runPrune('清理网络（docker network prune）',
    '将删除所有没有被任何容器连接的网络（内置 bridge/host/null 等不受影响）。是否继续？',
    () => pruneNetworks(envStore.currentEnvId!), loadData)
}
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; align-items: center; }
</style>
