<template>
  <div>
    <div class="page-header">
      <h2>环境管理</h2>
      <el-button type="primary" @click="showForm = true">添加环境</el-button>
    </div>
    <el-table :data="environments" v-loading="loading" stripe>
      <el-table-column label="名称" prop="name" min-width="150" />
      <el-table-column label="类型" prop="type" width="100" />
      <el-table-column label="地址" prop="endpoint" min-width="250" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONNECTED' ? 'success' : row.status === 'ERROR' ? 'danger' : 'info'" size="small">
            {{ row.status === 'CONNECTED' ? '已连接' : row.status === 'ERROR' ? '错误' : '未连接' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Swarm" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.swarmMode" type="warning" size="small">Swarm</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Docker 版本" prop="dockerVersion" width="120" />
      <el-table-column label="操作" width="340">
        <template #default="{ row }">
          <el-button size="small" :loading="testingId === row.id" @click="doTest(row)">测试</el-button>
          <el-button size="small" type="success" @click="doConnect(row.id)" v-if="row.status !== 'CONNECTED'">连接</el-button>
          <el-button size="small" type="warning" @click="doDisconnect(row.id)" v-if="row.status === 'CONNECTED'">断开</el-button>
          <el-button size="small" @click="editEnv(row)">编辑</el-button>
          <el-popconfirm title="确认删除?" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showForm" :title="editingId ? '编辑环境' : '添加环境'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="本地 (Local)" value="LOCAL" />
            <el-option label="远程 (TCP)" value="TCP" />
            <el-option label="SSH" value="SSH" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.endpoint" :placeholder="endpointPlaceholder" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试结果 -->
    <el-dialog v-model="testVisible" :title="`测试连接：${testEnvName}`" width="460px">
      <el-alert v-if="testOk" type="success" :closable="false" show-icon
        :title="`连接成功：Docker ${testInfo?.dockerVersion}（API ${testInfo?.apiVersion}）`">
        <div style="font-size: 13px; line-height: 1.7">
          <div>系统：{{ testInfo?.os }}/{{ testInfo?.arch }}，内核 {{ testInfo?.kernelVersion }}</div>
          <div>资源：{{ testInfo?.ncpu }} 核 / {{ testInfo?.memGB }} GB，容器 {{ testInfo?.containerCount }} 个</div>
          <div>Swarm：{{ testInfo?.swarmState || 'unknown' }}</div>
        </div>
      </el-alert>
      <el-alert v-else type="error" :closable="false" show-icon :title="testInfo" />
      <template #footer><el-button @click="testVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getEnvironments, createEnvironment, updateEnvironment, deleteEnvironment, connectEnvironment, disconnectEnvironment, testEnvironment } from '@/api/environment'
import { useEnvironmentStore } from '@/stores/environment'
import { ElMessage } from 'element-plus'

const envStore = useEnvironmentStore()
const environments = ref<any[]>([])
const loading = ref(false)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', type: 'LOCAL', endpoint: '' })
const testingId = ref<number | null>(null)
const testVisible = ref(false)
const testOk = ref(false)
const testInfo = ref<any>(null)
const testEnvName = ref('')

const endpointPlaceholder = computed(() => {
  switch (form.type) {
    case 'LOCAL': return 'unix:///var/run/docker.sock'
    case 'TCP': return 'tcp://192.168.1.100:2375'
    case 'SSH': return 'user@192.168.1.100'
    default: return ''
  }
})

async function doTest(row: any) {
  testingId.value = row.id
  testEnvName.value = row.name
  try {
    testInfo.value = await testEnvironment(row.id)
    testOk.value = true
  } catch (e: any) {
    testInfo.value = e?.response?.data?.message || e?.message || '连接失败'
    testOk.value = false
  } finally {
    testingId.value = null
    testVisible.value = true
    loadData()
  }
}

async function loadData() {
  loading.value = true
  try { environments.value = await getEnvironments() } finally { loading.value = false }
}
function editEnv(env: any) {
  editingId.value = env.id
  form.name = env.name; form.type = env.type; form.endpoint = env.endpoint
  showForm.value = true
}
async function doSave() {
  if (editingId.value) {
    await updateEnvironment(editingId.value, form)
  } else {
    await createEnvironment(form)
  }
  ElMessage.success('保存成功'); showForm.value = false; editingId.value = null; loadData()
  envStore.loadEnvironments()
}
async function doDelete(id: number) {
  await deleteEnvironment(id); ElMessage.success('删除成功'); loadData(); envStore.loadEnvironments()
}
async function doConnect(id: number) {
  await connectEnvironment(id); ElMessage.success('连接成功'); loadData(); envStore.loadEnvironments()
}
async function doDisconnect(id: number) {
  await disconnectEnvironment(id); ElMessage.success('已断开'); loadData(); envStore.loadEnvironments()
}
onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
