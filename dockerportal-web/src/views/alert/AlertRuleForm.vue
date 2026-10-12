<template>
  <div class="alert-rule-form">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑告警规则' : '新建告警规则' }}</span>
      </template>
      <el-form :model="form" label-width="100px" style="max-width: 600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="目标类型" required>
          <el-select v-model="form.targetType">
            <el-option label="容器" value="CONTAINER" />
            <el-option label="服务" value="SERVICE" />
            <el-option label="节点" value="NODE" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标" required>
          <el-input v-model="form.metric" placeholder="如 cpu_usage, memory_usage" />
        </el-form-item>
        <el-form-item label="运算符">
          <el-select v-model="form.operator">
            <el-option label="大于 (>)" value="GT" />
            <el-option label="小于 (<)" value="LT" />
            <el-option label="等于 (=)" value="EQ" />
            <el-option label="大于等于 (>=)" value="GTE" />
            <el-option label="小于等于 (<=)" value="LTE" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" required>
          <el-input-number v-model="form.threshold" :min="0" :step="0.1" />
        </el-form-item>
        <el-form-item label="持续时间(秒)">
          <el-input-number v-model="form.duration" :min="0" />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.severity">
            <el-option label="信息" value="INFO" />
            <el-option label="警告" value="WARNING" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="Webhook">
          <el-input v-model="form.webhookUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">{{ isEdit ? '更新' : '创建' }}</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAlertRule, createAlertRule, updateAlertRule } from '@/api/alert'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.query.id)

const form = ref({
  name: '',
  targetType: 'CONTAINER',
  metric: 'cpu_usage',
  operator: 'GT',
  threshold: 80,
  duration: 60,
  severity: 'WARNING',
  enabled: true,
  webhookUrl: '',
  environmentId: null as number | null,
})

async function loadRule() {
  if (!route.query.id) return
  try {
    const rule = await getAlertRule(Number(route.query.id))
    form.value = { ...rule }
  } catch (e) { ElMessage.error('加载规则失败') }
}

async function submit() {
  if (!form.value.name || !form.value.metric) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    if (isEdit.value) {
      await updateAlertRule(Number(route.query.id), form.value)
      ElMessage.success('规则已更新')
    } else {
      await createAlertRule(form.value)
      ElMessage.success('规则已创建')
    }
    router.push('/alerts/rules')
  } catch (e) { ElMessage.error('操作失败') }
}

onMounted(loadRule)
</script>
