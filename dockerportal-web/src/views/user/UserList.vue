<template>
  <div>
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="showCreate = true">创建用户</el-button>
    </div>
    <el-table :data="pageData" v-loading="loading" stripe>
      <el-table-column label="用户名" prop="username" min-width="150" />
      <el-table-column label="邮箱" prop="email" min-width="200" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : row.role === 'OPERATOR' ? 'warning' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="首次登录" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.mustChangePassword" type="warning" size="small">需改密码</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="doToggle(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
          <el-button size="small" type="warning" plain @click="doReset(row)">重置密码</el-button>
          <el-popconfirm title="确认删除?" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
    <el-dialog v-model="showCreate" title="创建用户" width="400px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="createForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="createForm.password" type="password" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="操作员" value="OPERATOR" />
            <el-option label="只读" value="VIEWER" />
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
import { ref, reactive, onMounted } from 'vue'
import request from '@/api/request'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(users)
const loading = ref(false)
const showCreate = ref(false)
const createForm = reactive({ username: '', password: '', email: '', role: 'VIEWER' })

async function loadData() {
  loading.value = true
  try { users.value = await request.get('/users') as any } finally { loading.value = false }
}
async function doCreate() {
  await request.post('/users', createForm)
  ElMessage.success('创建成功'); showCreate.value = false; loadData()
}
async function doDelete(id: number) {
  await request.delete(`/users/${id}`)
  ElMessage.success('删除成功'); loadData()
}
async function doToggle(row: any) {
  await request.post(`/users/${row.id}/toggle-enabled`)
  ElMessage.success(row.enabled ? '已禁用' : '已启用'); loadData()
}
async function doReset(row: any) {
  let newPassword: string
  try {
    const r = await ElMessageBox.prompt(
      `为用户 “${row.username}” 设置新密码（至少 8 位，含字母与数字）。重置后该用户首次登录将被强制再次修改密码。`,
      '重置密码',
      {
        inputType: 'password',
        inputPlaceholder: '输入新密码',
        inputValidator: (v: string) =>
          /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(v || '') ? true : '密码至少 8 位且同时包含字母和数字',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
      },
    )
    newPassword = r.value
  } catch {
    return
  }
  await request.post(`/users/${row.id}/reset-password`, { newPassword })
  ElMessage.success('密码已重置')
  loadData()
}
onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
