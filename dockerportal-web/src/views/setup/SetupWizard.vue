<template>
  <div class="setup-page">
    <div class="setup-card">
      <div class="setup-header">
        <el-icon size="32" color="#409eff"><Ship /></el-icon>
        <h1>欢迎使用 DockerPortal</h1>
        <p>完成以下设置即可开始管理 Docker 环境</p>
      </div>

      <el-steps :active="currentStep" finish-status="success" align-center style="margin: 24px 0">
        <el-step :title="createMode ? '创建管理员账号' : '修改初始密码'" />
        <el-step title="连接 Docker 环境" />
        <el-step title="初始化 Swarm（可选）" />
      </el-steps>

      <!-- 步骤一（未认证/全新安装）：直接建管理员账号，不再从后端日志里接随机密码 -->
      <div v-if="currentStep === 0 && createMode" class="step-body">
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px"
          title="该账号拥有平台全部权限，请用强密码并妥善保管。" />
        <el-alert v-if="setupStore.freshInstall" type="info" :closable="false" show-icon style="margin-bottom: 16px"
          title="本次安装已自动生成随机 JWT 签名密钥并写入数据目录下的 .jwt.key（权限 600），请与数据库文件一同备份；丢失该文件会使已登录令牌全部失效。" />
        <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="90px" @submit.prevent>
          <el-form-item label="管理员" prop="username">
            <el-input v-model="accountForm.username" placeholder="admin" autocomplete="off" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="accountForm.password" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="accountForm.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
        </el-form>
        <div class="hint">密码需至少 8 位，并同时包含字母和数字；用户名留空则为 admin。</div>
        <el-button type="primary" :loading="accountSaving" @click="submitAccount" style="width: 100%">
          创建账号，下一步
        </el-button>
      </div>

      <!-- 步骤一（已认证但带了初始密码）：改密 -->
      <div v-else-if="currentStep === 0" class="step-body">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
          title="首次登录或密码被重置后需先设置自己的强密码；当前密码仅本次会话可见，请妥善保管新密码。" />
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px" @submit.prevent>
          <el-form-item label="旧密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="off" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="off" />
          </el-form-item>
        </el-form>
        <div class="hint">密码需至少 8 位，并同时包含字母和数字。</div>
        <el-button type="primary" :loading="pwdSaving" @click="submitPassword" style="width: 100%">
          保存密码，下一步
        </el-button>
      </div>

      <!-- 步骤二：创建 Docker 环境 -->
      <div v-else-if="currentStep === 1" class="step-body">
        <el-form label-width="90px" @submit.prevent>
          <el-form-item label="名称" required>
            <el-input v-model="envForm.name" placeholder="如：本机 Docker" />
          </el-form-item>
          <el-form-item label="连接类型" required>
            <el-radio-group v-model="envForm.type">
              <el-radio value="LOCAL">本地 Socket</el-radio>
              <el-radio value="TCP">远程 TCP</el-radio>
              <el-radio value="SSH">SSH 隧道</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="端点" required>
            <el-input v-model="envForm.endpoint" :placeholder="endpointPlaceholder" />
          </el-form-item>
          <template v-if="envForm.type === 'TCP'">
            <el-form-item label="TLS">
              <el-switch v-model="envForm.tlsEnabled" />
            </el-form-item>
            <template v-if="envForm.tlsEnabled">
              <el-form-item label="CA 证书"><el-input v-model="envForm.tlsCaCert" type="textarea" :rows="2" /></el-form-item>
              <el-form-item label="客户端证书"><el-input v-model="envForm.tlsCert" type="textarea" :rows="2" /></el-form-item>
              <el-form-item label="客户端密钥"><el-input v-model="envForm.tlsKey" type="textarea" :rows="2" /></el-form-item>
            </template>
          </template>
          <template v-if="envForm.type === 'SSH'">
            <el-form-item label="SSH 用户"><el-input v-model="envForm.sshUser" placeholder="root" /></el-form-item>
            <el-form-item label="SSH 密钥"><el-input v-model="envForm.sshKey" type="textarea" :rows="3" placeholder="私钥内容" /></el-form-item>
          </template>
          <el-form-item>
            <el-button @click="testConn" :loading="testing">测试连接</el-button>
          </el-form-item>
          <el-form-item v-if="testResult || testError" label=" ">
            <el-alert v-if="testResult" type="success" :closable="false" show-icon
              :title="`连接成功：Docker ${testResult.dockerVersion}（API ${testResult.apiVersion}）`">
              <div class="test-detail">
                <div>系统：{{ testResult.os }}/{{ testResult.arch }}，{{ testResult.ncpu }} 核 / {{ testResult.memGB }} GB</div>
                <div>Swarm：{{ testResult.swarmState === 'active' ? '已初始化' : '未初始化' }}</div>
              </div>
            </el-alert>
            <el-alert v-else type="error" :closable="false" show-icon :title="testError" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="envSaving" @click="submitEnv" style="width: 100%">
          保存环境，下一步
        </el-button>
        <el-button text style="width: 100%; margin-top: 8px; margin-left: 0" @click="currentStep = 2">
          已有环境，跳到下一步
        </el-button>
      </div>

      <!-- 步骤三：初始化 Swarm（可选） -->
      <div v-else-if="currentStep === 2" class="step-body">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
          title="Swarm 模式支持多节点集群、服务编排与 Secrets/Configs。单节点也有价值，建议初始化；也可稍后在 Swarm 页面完成。" />
        <el-form label-width="90px">
          <el-form-item label="选择环境">
            <el-select v-model="swarmEnvId" style="width: 100%">
              <el-option v-for="e in envOptions" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="广播地址">
            <el-input v-model="advertiseAddr" placeholder="可选，如 192.168.1.10 或 en0，留空自动探测" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="swarmIniting" :disabled="!swarmEnvId" @click="doInitSwarm" style="width: 100%">
          初始化 Swarm 集群
        </el-button>
        <el-button text style="width: 100%; margin-top: 8px; margin-left: 0" @click="finish">
          跳过，稍后初始化
        </el-button>
      </div>

      <!-- 全部完成 -->
      <div v-else class="step-body" style="text-align: center">
        <el-result icon="success" title="安装设置完成">
          <template #extra>
            <el-button type="primary" @click="finish">进入控制台</el-button>
          </template>
        </el-result>
      </div>

      <div v-if="currentStep < 3" class="skip-bar">
        <el-button text @click="finish">退出向导，稍后设置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { changePassword } from '@/api/auth'
import { submitSetup } from '@/api/setup'
import { createEnvironment, getEnvironments, testEnvironmentDraft } from '@/api/environment'
import { initSwarm } from '@/api/swarm'
import { useAuthStore } from '@/stores/auth'
import { useEnvironmentStore } from '@/stores/environment'
import { useSetupStore } from '@/stores/setup'

const router = useRouter()
const authStore = useAuthStore()
const envStore = useEnvironmentStore()
const setupStore = useSetupStore()

const currentStep = ref(0)
/** 步骤一的双形态：未登录走建号，已登录（带初始密码）走改密 */
const createMode = ref(false)
const envOptions = ref<any[]>([])
const swarmEnvId = ref<number | undefined>(undefined)
const advertiseAddr = ref('')
const swarmIniting = ref(false)

// ---------- 步骤状态幂等判定 ----------
onMounted(async () => {
  // 未认证时不能调 /auth/me：拦截器碰到 401 会清登录态并把人踢回 /login，向导就进不去了
  createMode.value = !authStore.isAuthenticated
  if (createMode.value) return
  await authStore.refreshMe()
  currentStep.value = authStore.mustChangePassword ? 0 : 1
  if (currentStep.value >= 1) {
    try {
      envOptions.value = await getEnvironments()
    } catch { envOptions.value = [] }
    if (envOptions.value.length > 0) {
      currentStep.value = 2
      swarmEnvId.value = envStore.currentEnvId || envOptions.value[0]?.id
    }
  }
})

// ---------- 步骤一（全新安装）：建管理员账号 ----------
const accountFormRef = ref<FormInstance>()
const accountSaving = ref(false)
const accountForm = reactive({ username: '', password: '', confirmPassword: '' })
const accountRules: FormRules = {
  username: [
    { pattern: /^[a-zA-Z0-9_.-]{3,32}$/, message: '只能包含字母、数字、下划线、点、横线，长度 3-32', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '需同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: (_r, v, cb) => (v === accountForm.password ? cb() : cb(new Error('两次输入不一致'))), trigger: 'blur' },
  ],
}

async function submitAccount() {
  if (!accountFormRef.value) return
  const valid = await accountFormRef.value.validate().catch(() => false)
  if (!valid) return
  accountSaving.value = true
  try {
    const res: any = await submitSetup({ username: accountForm.username.trim(), password: accountForm.password })
    // 后端直接回签发好的令牌，不需要用户再回登录页输一遍密码
    authStore.setToken(res.accessToken)
    await authStore.refreshMe()
    await setupStore.reset()
    ElMessage.success(`管理员账号 ${res.username} 已创建`)
    currentStep.value = 1
  } catch {
    // 拦截器已提示
  } finally { accountSaving.value = false }
}

// ---------- 步骤一（已认证）：改密 ----------
const pwdFormRef = ref<FormInstance>()
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入启动日志中的初始密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '需同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (_r, v, cb) => (v === pwdForm.newPassword ? cb() : cb(new Error('两次输入不一致'))), trigger: 'blur' },
  ],
}

async function submitPassword() {
  if (!pwdFormRef.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSaving.value = true
  try {
    const res: any = await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    if (res?.accessToken) authStore.setToken(res.accessToken)
    authStore.clearMustChangePassword()
    ElMessage.success('密码已更新')
    currentStep.value = 1
  } catch {
    // 拦截器已提示
  } finally { pwdSaving.value = false }
}

// ---------- 步骤二：环境 ----------
const envSaving = ref(false)
const testing = ref(false)
const testResult = ref<any>(null)
const testError = ref('')
const envForm = reactive({
  name: '本机 Docker',
  type: 'LOCAL' as 'LOCAL' | 'TCP' | 'SSH',
  endpoint: 'unix:///var/run/docker.sock',
  tlsEnabled: false,
  tlsCaCert: '',
  tlsCert: '',
  tlsKey: '',
  sshUser: '',
  sshKey: '',
})

const endpointPlaceholder = computed(() => {
  switch (envForm.type) {
    case 'LOCAL': return 'unix:///var/run/docker.sock'
    case 'TCP': return 'tcp://192.168.1.100:2375'
    case 'SSH': return 'ssh://user@host'
  }
})

async function testConn() {
  if (!envForm.endpoint) { ElMessage.warning('请先填写端点'); return }
  testing.value = true
  testResult.value = null
  testError.value = ''
  try {
    testResult.value = await testEnvironmentDraft({ ...envForm })
  } catch (e: any) {
    testError.value = e?.message || '连接失败'
  } finally { testing.value = false }
}

async function submitEnv() {
  if (!envForm.name || !envForm.endpoint) { ElMessage.warning('请填写名称和端点'); return }
  envSaving.value = true
  try {
    const created: any = await createEnvironment({ ...envForm })
    ElMessage.success('环境已创建')
    await envStore.loadEnvironments()
    envOptions.value = await getEnvironments()
    swarmEnvId.value = created?.id || envOptions.value[0]?.id
    currentStep.value = 2
  } catch {
    // 拦截器已提示
  } finally { envSaving.value = false }
}

// ---------- 步骤三：Swarm ----------
async function doInitSwarm() {
  if (!swarmEnvId.value) return
  swarmIniting.value = true
  try {
    await initSwarm(swarmEnvId.value, advertiseAddr.value.trim() || undefined)
    ElMessage.success('Swarm 集群初始化成功')
    currentStep.value = 3
  } catch (e: any) {
    // 40901 以外的错误拦截器已提示；已初始化视为成功
    if (e?.response?.status !== 409) ElMessage.warning('初始化未成功，可稍后在 Swarm 页面重试')
    else currentStep.value = 3
  } finally { swarmIniting.value = false }
}

function finish() {
  router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 24px;
}

.setup-card {
  width: 640px;
  max-width: 100%;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  padding: 32px 40px;
}

.setup-header {
  text-align: center;

  h1 {
    margin: 8px 0 4px;
    font-size: 22px;
    color: #303133;
  }

  p {
    margin: 0;
    color: #909399;
    font-size: 14px;
  }
}

.step-body {
  margin-top: 8px;
}

.hint {
  font-size: 12px;
  color: #909399;
  // 不能上负边距：el-form-item 的校验报错是绝对定位往下浮的，负边距会让说明文字和报错重叠
  margin: 6px 0 12px;
}

.test-detail {
  font-size: 13px;
  line-height: 1.7;
}

.skip-bar {
  text-align: center;
  margin-top: 16px;
}
</style>
