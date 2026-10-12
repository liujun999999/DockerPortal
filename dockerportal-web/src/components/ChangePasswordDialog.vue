<template>
  <el-dialog
    :model-value="modelValue"
    :title="forceMode ? '首次登录请修改初始密码' : '修改密码'"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="!forceMode"
    :show-close="!forceMode"
    append-to-body
  >
    <el-alert
      v-if="forceMode"
      type="warning"
      :closable="false"
      show-icon
      title="为保障环境安全，请设置一个强密码后再使用其他功能"
      style="margin-bottom: 16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" @submit.prevent>
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="off" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="off" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="off" />
      </el-form-item>
    </el-form>
    <div class="hint">密码需至少 8 位，并同时包含字母和数字。</div>
    <template #footer>
      <el-button v-if="!forceMode" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ modelValue: boolean; forceMode?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '需同时包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('两次输入不一致'))),
      trigger: 'blur',
    },
  ],
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      form.oldPassword = ''
      form.newPassword = ''
      form.confirmPassword = ''
    }
  }
)

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res: any = await changePassword(form.oldPassword, form.newPassword)
      // 后端换签新 token（旧 token 带 must_change claim 会被后续 API 拦截）
      if (res?.accessToken) authStore.setToken(res.accessToken)
      ElMessage.success('密码修改成功')
      if (props.forceMode) authStore.clearMustChangePassword()
      emit('update:modelValue', false)
      emit('success')
    } catch (e) {
      // 拦截器已提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
</style>
