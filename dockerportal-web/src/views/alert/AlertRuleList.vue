<template>
  <div class="alert-rule-list">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>告警规则</span>
          <el-button type="primary" @click="$router.push('/alerts/rules/create')">新建规则</el-button>
        </div>
      </template>
      <el-table :data="pageData" stripe v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="目标类型" width="100">
          <template #default="{ row }">{{ row.targetType }}</template>
        </el-table-column>
        <el-table-column label="指标" width="120">
          <template #default="{ row }">{{ row.metric }}</template>
        </el-table-column>
        <el-table-column label="条件" width="150">
          <template #default="{ row }">{{ row.operator }} {{ row.threshold }}</template>
        </el-table-column>
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <StatusTag :status="row.severity" :status-map="ALERT_SEVERITY" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggleRule(row.id)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="editRule(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="deleteRule(row.id)">
              <template #reference>
                <el-button size="small" text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAlertRules, deleteAlertRule, toggleAlertRule } from '@/api/alert'
import { ALERT_SEVERITY } from '@/utils/constants'
import { usePagination } from '@/composables/usePagination'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const router = useRouter()
const rules = ref<any[]>([])
const { page, pageSize, total, pageData } = usePagination(rules)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try { rules.value = await getAlertRules() }
  catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function toggleRule(id: number) {
  try { await toggleAlertRule(id); ElMessage.success('已更新'); loadData() }
  catch (e) { ElMessage.error('操作失败') }
}

async function deleteRule(id: number) {
  try { await deleteAlertRule(id); ElMessage.success('已删除'); loadData() }
  catch (e) { ElMessage.error('删除失败') }
}

function editRule(row: any) {
  router.push({ path: '/alerts/rules/create', query: { id: row.id } })
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
