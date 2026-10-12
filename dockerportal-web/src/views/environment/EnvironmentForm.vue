<template>
  <div class="environment-form">
    <el-card shadow="never">
      <template #header>
        <span>{{ isEdit ? '编辑环境' : '添加环境' }}</span>
      </template>
      <el-form :model="form" label-width="100px" style="max-width: 600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：生产环境" />
        </el-form-item>
        <el-form-item label="连接类型" required>
          <el-radio-group v-model="form.type">
            <el-radio value="LOCAL">本地 Socket</el-radio>
            <el-radio value="TCP">远程 TCP</el-radio>
            <el-radio value="SSH">SSH 隧道</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="端点" required>
          <el-input v-model="form.endpoint" :placeholder="endpointPlaceholder" />
        </el-form-item>
        <template v-if="form.type === 'TCP'">
          <el-form-item label="TLS">
            <el-switch v-model="form.tlsEnabled" />
          </el-form-item>
          <template v-if="form.tlsEnabled">
            <el-form-item label="CA 证书">
              <el-input v-model="form.tlsCaCert" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="客户端证书">
              <el-input v-model="form.tlsCert" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="客户端密钥">
              <el-input v-model="form.tlsKey" type="textarea" :rows="3" />
            </el-form-item>
          </template>
        </template>
        <template v-if="form.type === 'SSH'">
          <el-form-item label="SSH 用户">
            <el-input v-model="form.sshUser" placeholder="root" />
          </el-form-item>
          <el-form-item label="SSH 密钥">
            <el-input v-model="form.sshKey" type="textarea" :rows="4" placeholder="私钥内容" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button @click="testConn" :loading="testing">测试连接</el-button>
        </el-form-item>
        <el-form-item v-if="testResult || testError" label=" ">
          <el-alert v-if="testResult" type="success" :closable="true" show-icon
            :title="`连接成功：Docker ${testResult.dockerVersion}（API ${testResult.apiVersion}）`"
            @close="testResult = null">
            <div class="test-detail">
              <div>系统：{{ testResult.os }}/{{ testResult.arch }}，内核 {{ testResult.kernelVersion }}</div>
              <div>资源：{{ testResult.ncpu }} 核 / {{ testResult.memGB }} GB，容器 {{ testResult.containerCount }} 个</div>
              <div>Swarm：{{ swarmStateLabel(testResult.swarmState) }}</div>
            </div>
          </el-alert>
          <el-alert v-else type="error" :closable="true" show-icon :title="testError" @close="testError = ''" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit" :loading="saving">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getEnvironment, createEnvironment, updateEnvironment, testEnvironmentDraft } from '@/api/environment'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const saving = ref(false)
const testing = ref(false)
const testResult = ref<any>(null)
const testError = ref('')

const form = ref({
  name: '',
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
  switch (form.value.type) {
    case 'LOCAL': return 'unix:///var/run/docker.sock'
    case 'TCP': return 'tcp://192.168.1.100:2375'
    case 'SSH': return 'ssh://user@host'
  }
})

async function loadEnv() {
  if (!route.params.id) return
  try {
    const env = await getEnvironment(Number(route.params.id))
    form.value = { ...form.value, ...env }
  } catch (e) { ElMessage.error('加载环境失败') }
}

async function submit() {
  if (!form.value.name || !form.value.endpoint) {
    ElMessage.warning('请填写名称和端点')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateEnvironment(Number(route.params.id), form.value)
      ElMessage.success('环境已更新')
    } else {
      await createEnvironment(form.value)
      ElMessage.success('环境已创建')
    }
    router.push('/settings/environments')
  } catch (e) { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

async function testConn() {
  if (!form.value.endpoint) {
    ElMessage.warning('请先填写端点')
    return
  }
  testing.value = true
  testResult.value = null
  testError.value = ''
  try {
    testResult.value = await testEnvironmentDraft(form.value)
  } catch (e: any) {
    // 拦截器已弹全局提示，表单内再展示一次可读原因
    testError.value = e?.friendlyMessage || e?.message || '连接失败'
  } finally { testing.value = false }
}

function swarmStateLabel(s?: string) {
  if (s === 'active') return '已初始化（本节点在集群中）'
  if (s === 'pending') return '集群初始化中'
  return '未初始化（单节点模式）'
}

onMounted(loadEnv)
</script>

<style scoped>
.test-detail {
  font-size: 13px;
  line-height: 1.7;
}
</style>
