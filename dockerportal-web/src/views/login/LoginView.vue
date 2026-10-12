<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <el-icon size="40" color="#409eff"><Ship /></el-icon>
        <h1>DockerPortal</h1>
        <p>Docker 容器管理平台</p>
      </div>
      <el-alert v-if="setupStore.needsSetup" type="warning" :closable="false" show-icon class="setup-tip"
        title="实例尚未初始化">
        <div>
          当前还没有任何账号，登录表单不会生效。请先
          <el-link type="primary" :underline="false" @click="router.push('/setup')">进入安装向导</el-link>
          创建管理员账号。
        </div>
      </el-alert>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSetupStore } from '@/stores/setup'
import { ElMessage, type FormInstance } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const setupStore = useSetupStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    // 首登强制改密 → 进入安装向导
    router.push(authStore.mustChangePassword ? '/setup' : '/dashboard')
  } catch (e) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.setup-tip {
  margin-bottom: 18px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;

  h1 {
    margin: 12px 0 4px;
    font-size: 24px;
    color: #303133;
  }

  p {
    color: #909399;
    font-size: 14px;
  }
}
</style>
