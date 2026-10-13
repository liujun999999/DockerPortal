<template>
  <div>
    <div class="page-header">
      <h2>镜像仓库</h2>
      <el-button type="primary" @click="openForm()">添加仓库</el-button>
    </div>
    <el-alert type="info" :closable="false" class="mb-16"
      title="拉取镜像时，若镜像地址命中下方某个仓库，会自动携带对应账号凭据，可直连 Harbor / 阿里云 ACR / DockerHub 等私有仓库。内置 Docker Hub 为默认仓库，无需配置即可拉取公开镜像；如需拉取自己 Docker Hub 账号下的私有镜像，请额外新增一条仓库记录（地址留空即可）。" />
    <el-table :data="pageData" v-loading="loading" stripe>
      <el-table-column label="名称" prop="name" min-width="140" />
      <el-table-column label="仓库地址" min-width="240">
        <template #default="{ row }">
          <code>{{ row.url || 'Docker Hub（默认）' }}</code>
          <el-tag v-if="row.builtin" size="small" type="info" effect="plain" style="margin-left:8px">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" prop="username" width="160">
        <template #default="{ row }">{{ row.username || '匿名' }}</template>
      </el-table-column>
      <el-table-column label="命名空间" width="140">
        <template #default="{ row }">{{ row.namespacePrefix || '-' }}</template>
      </el-table-column>
      <el-table-column label="生效环境" width="120">
        <template #default="{ row }">
          <el-tag v-if="!row.environmentId" size="small">全部</el-tag>
          <el-tag v-else type="warning" size="small">{{ envName(row.environmentId) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ row.updatedAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <template v-if="!row.builtin">
            <el-button size="small" type="success" :loading="testingId === row.id" @click="doTest(row)">测试连接</el-button>
            <el-button size="small" @click="openForm(row)">编辑</el-button>
            <el-popconfirm title="确认删除该仓库配置?" @confirm="doDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
          <span v-else style="color:#999">内置默认，无需配置</span>
        </template>
      </el-table-column>
    </el-table>
    <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />

    <el-dialog v-model="showForm" :title="editingId ? '编辑仓库' : '添加仓库'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：公司 Harbor" />
        </el-form-item>
        <el-form-item label="仓库地址">
          <el-input v-model="form.url" placeholder="如 harbor.example.com（留空 = Docker Hub）" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="可留空＝匿名仓库（免登录公开镜像库）" />
        </el-form-item>
        <el-form-item label="密码/令牌">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="editingId ? '留空表示不修改' : '仓库密码或访问令牌，匿名仓库可留空'" />
        </el-form-item>
        <el-form-item label="命名空间">
          <el-input v-model="form.namespacePrefix" placeholder="可选，如 library、my-project" />
        </el-form-item>
        <el-form-item label="生效环境">
          <el-select v-model="form.environmentId" clearable placeholder="全部环境（默认）" style="width:100%">
            <el-option v-for="e in envStore.environments" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button :loading="testing" @click="doTestForm">测试连接</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRegistries, createRegistry, updateRegistry, deleteRegistry, testRegistryById, testRegistryWith } from '@/api/registry'
import { useEnvironmentStore } from '@/stores/environment'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'

const envStore = useEnvironmentStore()
const registries = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(registries)
const loading = ref(false)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const testing = ref(false)
const testingId = ref<number | null>(null)
const form = reactive({ name: '', url: '', username: '', password: '', namespacePrefix: '', environmentId: undefined as number | undefined })

function envName(id: number) {
  return envStore.environments.find((e) => e.id === id)?.name || `环境${id}`
}

async function loadData() {
  loading.value = true
  try { registries.value = await getRegistries() } finally { loading.value = false }
}

function openForm(row?: any) {
  editingId.value = row?.id ?? null
  form.name = row?.name || ''
  form.url = row?.url || ''
  form.username = row?.username || ''
  form.password = ''
  form.namespacePrefix = row?.namespacePrefix || ''
  form.environmentId = row?.environmentId ?? undefined
  showForm.value = true
}

async function doSave() {
  if (!form.name) {
    ElMessage.warning('请填写仓库名称')
    return
  }
  if ((form.username && !form.password && !editingId.value) || (!form.username && form.password)) {
    ElMessage.warning('用户名与密码需同时填写；匿名仓库请两者留空')
    return
  }
  const data = { ...form, environmentId: form.environmentId ?? null, namespacePrefix: form.namespacePrefix || null }
  if (editingId.value) {
    await updateRegistry(editingId.value, data)
  } else {
    await createRegistry(data)
  }
  ElMessage.success('保存成功')
  showForm.value = false
  loadData()
}

async function doDelete(id: number) {
  await deleteRegistry(id)
  ElMessage.success('删除成功')
  loadData()
}

function showTestResult(msg: string) {
  const tone = msg.startsWith('连接成功') ? 'success' : 'warning'
  ElMessage({ message: msg, type: tone, duration: 6000 })
}

async function doTest(row: any) {
  testingId.value = row.id
  try {
    const r = await testRegistryById(row.id)
    showTestResult(r.message)
  } finally { testingId.value = null }
}

async function doTestForm() {
  if (!form.url) {
    ElMessage.warning('请先填写仓库地址（地址留空为 Docker Hub，内置默认无需测试）')
    return
  }
  testing.value = true
  try {
    // 编辑时密码留空则用已保存的密码测试
    let password = form.password
    if (editingId.value && !password) {
      const old = registries.value.find((r) => r.id === editingId.value)
      // 列表接口不返回密码，留空时直接测试已保存配置
      if (old) {
        const r = await testRegistryById(old.id)
        showTestResult(r.message)
        return
      }
    }
    const r = await testRegistryWith({ url: form.url, username: form.username, password })
    showTestResult(r.message)
  } finally { testing.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.mb-16 { margin-bottom: 16px; }
</style>
