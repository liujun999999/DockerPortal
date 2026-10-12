<template>
  <div class="service-form-page">
    <div class="page-header">
      <h2>{{ editingId ? `编辑服务：${form.name || editingId}` : '创建服务' }}</h2>
      <div class="header-actions">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存' : '创建' }}</el-button>
      </div>
    </div>

    <el-card shadow="never" v-loading="loadingInitial">
      <el-form :model="form" label-width="120px" size="default">
        <el-tabs v-model="activeTab">
          <!-- ==================== 基础 ==================== -->
          <el-tab-pane label="基础" name="basic">
            <el-form-item label="服务名称" required>
              <el-input v-model="form.name" :disabled="!!editingId" placeholder="如：web-frontend" />
            </el-form-item>
            <el-form-item label="镜像仓库">
              <el-select v-model="form.registryId" style="width:100%" @change="onRegistryChange">
                <el-option v-for="r in registries" :key="r.id" :label="registryLabel(r)" :value="r.id" />
              </el-select>
              <div class="hint">内置 Docker Hub 为默认；如需私有仓库凭据请先在「设置 → 镜像仓库」新增。</div>
            </el-form-item>
            <el-form-item label="镜像名" required>
              <el-input v-model="form.image">
                <template v-if="registryPrefix" #prepend>{{ registryPrefix }}</template>
              </el-input>
              <div class="hint">例如 nginx:latest、my-app:1.0.0；私有仓库只需填仓库内路径。</div>
            </el-form-item>
            <el-form-item label="部署模式">
              <el-radio-group v-model="form.mode">
                <el-radio-button value="replicated">复制副本 (Replicated)</el-radio-button>
                <el-radio-button value="global">每节点一个 (Global)</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.mode === 'replicated'" label="副本数">
              <el-input-number v-model="form.replicas" :min="0" :max="1000" />
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 启动 ==================== -->
          <el-tab-pane label="启动" name="startup">
            <el-form-item label="Entrypoint">
              <el-input v-model="form.entrypoint" placeholder="覆盖镜像 Entrypoint，例如：/bin/sh -c（留空沿用镜像默认）" />
              <div class="hint">按空白拆分为数组；对应 compose 的 entrypoint。</div>
            </el-form-item>
            <el-form-item label="命令 Command">
              <el-input v-model="form.command" placeholder="传给 Entrypoint 的参数，例如：nginx -g 'daemon off;'" />
              <div class="hint">对应 compose 的 command；留空沿用镜像默认 CMD。</div>
            </el-form-item>
            <el-form-item label="工作目录">
              <el-input v-model="form.workDir" placeholder="如 /app" />
            </el-form-item>
            <el-form-item label="运行用户">
              <el-input v-model="form.user" placeholder="如 1000:1000 或 appuser" />
            </el-form-item>
            <el-form-item label="主机名">
              <el-input v-model="form.hostname" placeholder="可选，容器内 hostname" />
            </el-form-item>
            <el-form-item label="停止信号">
              <el-input v-model="form.stopSignal" placeholder="如 SIGTERM" />
            </el-form-item>
            <el-form-item label="停止宽限期">
              <el-input-number v-model="form.stopGracePeriodSec" :min="0" :step="1" />
              <span class="unit">秒，0 表示默认 10s</span>
            </el-form-item>
            <el-form-item label="其他">
              <el-checkbox v-model="form.tty">分配伪终端 (TTY)</el-checkbox>
              <el-checkbox v-model="form.readOnly">根文件系统只读</el-checkbox>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 变量与标签 ==================== -->
          <el-tab-pane label="变量与标签" name="env">
            <el-form-item label="环境变量">
              <div class="kv-list">
                <div v-for="(e, i) in form.env" :key="i" class="kv-row">
                  <el-input v-model="e.key" placeholder="KEY" style="width:200px" />
                  <span class="kv-eq">=</span>
                  <el-input v-model="e.value" placeholder="value" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.env.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.env.push({ key: '', value: '' })">+ 添加变量</el-button>
              </div>
            </el-form-item>
            <el-form-item label="服务标签">
              <div class="kv-list">
                <div v-for="(l, i) in form.labels" :key="i" class="kv-row">
                  <el-input v-model="l.key" placeholder="label.key" style="width:220px" />
                  <span class="kv-eq">=</span>
                  <el-input v-model="l.value" placeholder="value" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.labels.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.labels.push({ key: '', value: '' })">+ 添加标签</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 网络与端口 ==================== -->
          <el-tab-pane label="网络与端口" name="network">
            <el-form-item label="附加网络">
              <el-select v-model="form.networks" multiple filterable placeholder="选择 overlay 网络（可多选）" style="width:100%">
                <el-option v-for="n in networks" :key="n.Id || n.Name" :label="n.Name || n.Id" :value="n.Name || n.Id" />
              </el-select>
            </el-form-item>
            <el-form-item label="发布模式">
              <el-radio-group v-model="form.endpointMode">
                <el-radio-button value="vip">VIP（默认）</el-radio-button>
                <el-radio-button value="dnsrr">DNSRR</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="端口映射">
              <div class="kv-list">
                <div v-for="(p, i) in form.ports" :key="i" class="kv-row">
                  <el-input v-model="p.publishedPort" placeholder="对外端口" style="width:120px" />
                  <span class="kv-eq">→</span>
                  <el-input v-model="p.targetPort" placeholder="容器端口" style="width:120px" />
                  <el-select v-model="p.protocol" style="width:90px">
                    <el-option label="tcp" value="tcp" />
                    <el-option label="udp" value="udp" />
                    <el-option label="sctp" value="sctp" />
                  </el-select>
                  <el-select v-model="p.publishMode" style="width:120px">
                    <el-option label="ingress" value="ingress" />
                    <el-option label="host" value="host" />
                  </el-select>
                  <el-input v-model="p.name" placeholder="名字（可选）" style="width:130px" />
                  <el-button size="small" text type="danger" @click="form.ports.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.ports.push({ publishedPort: '', targetPort: '', protocol: 'tcp', publishMode: 'ingress', name: '' })">+ 添加端口</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 存储 ==================== -->
          <el-tab-pane label="存储" name="storage">
            <el-form-item label="挂载">
              <div class="kv-list">
                <div v-for="(v, i) in form.mounts" :key="i" class="kv-row">
                  <el-select v-model="v.type" style="width:110px">
                    <el-option label="bind" value="bind" />
                    <el-option label="volume" value="volume" />
                    <el-option label="tmpfs" value="tmpfs" />
                  </el-select>
                  <el-input v-model="v.source" :placeholder="v.type === 'volume' ? '卷名（可留空自动创建）' : '主机路径 / tmpfs 无需源'" style="flex:1" />
                  <span class="kv-eq">→</span>
                  <el-input v-model="v.target" placeholder="容器内绝对路径" style="flex:1" />
                  <el-checkbox v-model="v.readOnly">只读</el-checkbox>
                  <el-button size="small" text type="danger" @click="form.mounts.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.mounts.push({ type: 'volume', source: '', target: '', readOnly: false })">+ 添加挂载</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 敏感数据 ==================== -->
          <el-tab-pane label="敏感数据" name="sensitive">
            <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
              title="Secret/Config 以文件形式挂载进任务容器；未填写目标路径时默认 /run/secrets/&lt;名称&gt;。需先在 Swarm → Secrets/Configs 页面创建资源。" />
            <el-form-item label="Secrets">
              <div class="kv-list">
                <div v-for="(s, i) in form.secretsRef" :key="i" class="kv-row">
                  <el-select v-model="s.resourceId" filterable placeholder="选择 Secret" style="width:240px">
                    <el-option v-for="sec in secretOptions" :key="sec.id" :label="sec.name" :value="sec.id" />
                  </el-select>
                  <span class="kv-eq">→</span>
                  <el-input v-model="s.target" :placeholder="'容器内路径（默认 /run/secrets/<名称>）'" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.secretsRef.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.secretsRef.push({ resourceId: '', target: '' })">+ 挂载 Secret</el-button>
              </div>
            </el-form-item>
            <el-form-item label="Configs">
              <div class="kv-list">
                <div v-for="(c, i) in form.configsRef" :key="i" class="kv-row">
                  <el-select v-model="c.resourceId" filterable placeholder="选择 Config" style="width:240px">
                    <el-option v-for="cfg in configOptions" :key="cfg.id" :label="cfg.name" :value="cfg.id" />
                  </el-select>
                  <span class="kv-eq">→</span>
                  <el-input v-model="c.target" placeholder="容器内路径（默认 /run/configs/<名称>）" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.configsRef.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.configsRef.push({ resourceId: '', target: '' })">+ 挂载 Config</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 资源与健康 ==================== -->
          <el-tab-pane label="资源与健康" name="resources">
            <el-form-item label="CPU 上限">
              <el-input-number v-model="form.limitsCpus" :step="0.1" :precision="2" :min="0" :controls="false" style="width:120px" />
              <span class="unit">核（0 不限）</span>
              <span style="margin:0 12px;color:#999">保留：</span>
              <el-input-number v-model="form.resCpus" :step="0.1" :precision="2" :min="0" :controls="false" style="width:120px" />
              <span class="unit">核</span>
            </el-form-item>
            <el-form-item label="内存上限">
              <el-input-number v-model="form.limitsMemMb" :step="64" :min="0" :controls="false" style="width:120px" />
              <span class="unit">MB（0 不限）</span>
              <span style="margin:0 12px;color:#999">保留：</span>
              <el-input-number v-model="form.resMemMb" :step="64" :min="0" :controls="false" style="width:120px" />
              <span class="unit">MB</span>
            </el-form-item>
            <el-divider content-position="left">健康检查 Healthcheck</el-divider>
            <el-form-item label="检查命令">
              <el-input v-model="form.healthTest" placeholder="例如：CMD curl -f http://localhost/ 或 CMD-SHELL bc 'curl -f http://localhost/'" />
              <div class="hint">按空白拆分，首元素应为 CMD / CMD-SHELL / NONE；留空表示不启用健康检查。</div>
            </el-form-item>
            <el-form-item label="时间参数">
              <el-input-number v-model="form.healthIntervalSec" :min="0" style="width:110px" /><span class="unit">间隔</span>
              <el-input-number v-model="form.healthTimeoutSec" :min="0" style="width:110px" /><span class="unit">超时</span>
              <el-input-number v-model="form.healthStartPeriodSec" :min="0" style="width:110px" /><span class="unit">启动宽限</span>
              <el-input-number v-model="form.healthRetries" :min="0" style="width:100px" /><span class="unit">重试</span>
              <span class="unit">（单位：秒 / 次）</span>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 部署策略 ==================== -->
          <el-tab-pane label="部署策略" name="deploy">
            <el-divider content-position="left">重启策略 Restart</el-divider>
            <el-form-item label="条件">
              <el-select v-model="form.restartCondition" style="width:200px">
                <el-option label="默认（不覆盖）" value="" />
                <el-option label="any（总是重启）" value="any" />
                <el-option label="none（不重启）" value="none" />
                <el-option label="on-failure（仅失败）" value="on-failure" />
              </el-select>
              <span style="margin:0 12px;color:#999">延迟：</span>
              <el-input-number v-model="form.restartDelaySec" :min="0" style="width:110px" /><span class="unit">秒</span>
              <span style="margin:0 12px;color:#999">最大重试：</span>
              <el-input-number v-model="form.restartMaxAttempts" :min="0" style="width:110px" />
              <span style="margin:0 12px;color:#999">窗口：</span>
              <el-input-number v-model="form.restartWindowSec" :min="0" style="width:110px" /><span class="unit">秒</span>
            </el-form-item>

            <el-divider content-position="left">调度约束 Placement</el-divider>
            <el-form-item label="约束条件">
              <el-input v-model="form.placementConstraints" type="textarea" :rows="3"
                placeholder="每行一条，如：node.role==worker&#10;node.labels.env==prod" />
            </el-form-item>
            <el-form-item label="最大副本数">
              <el-input-number v-model="form.placementMaxReplicas" :min="0" style="width:120px" />
              <span class="unit">0 表示不限制</span>
            </el-form-item>

            <el-divider content-position="left">滚动更新 Update</el-divider>
            <el-form-item label="更新参数">
              <span class="lbl">并行度</span>
              <el-input-number v-model="form.updateParallelism" :min="0" style="width:100px" />
              <span class="lbl">延迟</span>
              <el-input-number v-model="form.updateDelaySec" :min="0" style="width:100px" /><span class="unit">s</span>
              <span class="lbl">失败动作</span>
              <el-select v-model="form.updateFailureAction" style="width:130px" clearable placeholder="默认">
                <el-option label="continue" value="continue" />
                <el-option label="rollback" value="rollback" />
                <el-option label="pause" value="pause" />
              </el-select>
              <span class="lbl">顺序</span>
              <el-select v-model="form.updateOrder" style="width:130px" clearable placeholder="默认">
                <el-option label="stop-first" value="stop-first" />
                <el-option label="start-first" value="start-first" />
              </el-select>
            </el-form-item>

            <el-divider content-position="left">回滚 Rollback</el-divider>
            <el-form-item label="回滚参数">
              <span class="lbl">并行度</span>
              <el-input-number v-model="form.rollbackParallelism" :min="0" style="width:100px" />
              <span class="lbl">延迟</span>
              <el-input-number v-model="form.rollbackDelaySec" :min="0" style="width:100px" /><span class="unit">s</span>
              <span class="lbl">失败动作</span>
              <el-select v-model="form.rollbackFailureAction" style="width:130px" clearable placeholder="默认">
                <el-option label="continue" value="continue" />
                <el-option label="pause" value="pause" />
              </el-select>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <div class="form-footer">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存' : '创建' }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createService, updateService, getService } from '@/api/service'
import { getRegistries } from '@/api/registry'
import { getNetworks } from '@/api/network'
import { listSecrets, listConfigs } from '@/api/swarmResource'
import { useEnvironmentStore } from '@/stores/environment'

const route = useRoute()
const router = useRouter()
const envStore = useEnvironmentStore()
const editingId = computed(() => (route.params.id as string) || null)

const activeTab = ref('basic')
const submitting = ref(false)
const loadingInitial = ref(false)
const registries = ref<any[]>([])
const networks = ref<any[]>([])
const secretOptions = ref<any[]>([])
const configOptions = ref<any[]>([])

function defaultForm() {
  return {
    name: '',
    registryId: 0 as number,
    image: '',
    mode: 'replicated' as 'replicated' | 'global',
    replicas: 1,
    entrypoint: '',
    command: '',
    workDir: '',
    user: '',
    hostname: '',
    stopSignal: '',
    stopGracePeriodSec: 0,
    tty: false,
    readOnly: false,
    env: [] as { key: string; value: string }[],
    labels: [] as { key: string; value: string }[],
    networks: [] as string[],
    endpointMode: 'vip' as 'vip' | 'dnsrr',
    ports: [] as { publishedPort: string; targetPort: string; protocol: string; publishMode: string; name: string }[],
    mounts: [] as { type: string; source: string; target: string; readOnly: boolean }[],
    secretsRef: [] as { resourceId: string; target: string }[],
    configsRef: [] as { resourceId: string; target: string }[],
    limitsCpus: 0, resCpus: 0,
    limitsMemMb: 0, resMemMb: 0,
    healthTest: '', healthIntervalSec: 0, healthTimeoutSec: 0, healthStartPeriodSec: 0, healthRetries: 0,
    restartCondition: '', restartDelaySec: 0, restartMaxAttempts: 0, restartWindowSec: 0,
    placementConstraints: '', placementMaxReplicas: 0,
    updateParallelism: 1, updateDelaySec: 0, updateFailureAction: '', updateOrder: '',
    rollbackParallelism: 1, rollbackDelaySec: 0, rollbackFailureAction: '',
  }
}
const form = reactive(defaultForm())

onMounted(async () => {
  const envId = envStore.currentEnvId
  if (!envId) { ElMessage.warning('请先选择环境'); return }
  loadingInitial.value = true
  try {
    const [regs, nets] = await Promise.all([getRegistries(), getNetworks(envId)])
    registries.value = (regs as any) || []
    networks.value = (nets as any) || []
    // Secrets/Configs 需要 Swarm Manager；非集群环境静默为空
    const [secs, cfgs] = await Promise.all([
      listSecrets(envId).catch(() => []),
      listConfigs(envId).catch(() => []),
    ])
    secretOptions.value = secs || []
    configOptions.value = cfgs || []
    if (editingId.value) {
      const svc = await getService(envId, editingId.value)
      hydrateFromSpec(svc)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载表单数据失败')
  } finally {
    loadingInitial.value = false
  }
})

const registryPrefix = computed(() => {
  const r = registries.value.find((x) => x.id === form.registryId)
  return r && !r.builtin && r.url ? r.url + '/' : ''
})
function registryLabel(r: any) {
  if (r.builtin) return `${r.name}（内置默认）`
  return `${r.name}（${r.url || 'Docker Hub'}）`
}
function onRegistryChange() {
  // 从带完整地址的镜像切走时，剥离已存在的 host 前缀
  if (!form.image) return
  const r = registries.value.find((x) => x.id === form.registryId)
  if (r?.url && form.image.startsWith(r.url + '/')) return
  // 若之前从别的仓库带过来的前缀，剥掉第一段
  const idx = form.image.indexOf('/')
  if (idx > 0) {
    const first = form.image.slice(0, idx)
    if (first.includes('.') || first.includes(':')) form.image = form.image.slice(idx + 1)
  }
}

/** 编辑模式：docker-java 原生模型是 PascalCase，做一次反填 */
function hydrateFromSpec(svc: any) {
  const spec = svc?.Spec || svc
  form.name = spec?.Name || ''
  const tt = spec?.TaskTemplate || {}
  const cs = tt.ContainerSpec || {}
  form.image = cs.Image || ''
  form.entrypoint = (cs.Command || []).join(' ')
  form.command = (cs.Args || []).join(' ')
  form.workDir = cs.Dir || ''
  form.user = cs.User || ''
  form.hostname = cs.Hostname || ''
  form.stopSignal = cs.StopSignal || ''
  form.tty = !!cs.TTY
  form.readOnly = !!cs.ReadOnly
  if (Array.isArray(cs.Env)) {
    form.env = cs.Env.map((e: string) => {
      const i = e.indexOf('='); return i < 0 ? { key: e, value: '' } : { key: e.slice(0, i), value: e.slice(i + 1) }
    })
  }
  if (cs.Labels) form.labels = Object.entries(cs.Labels).map(([k, v]) => ({ key: k, value: String(v) }))
  if (Array.isArray(cs.Mounts)) {
    form.mounts = cs.Mounts.map((m: any) => ({
      type: (m.Type || 'volume').toLowerCase(),
      source: m.Source || '', target: m.Target || '', readOnly: !!m.ReadOnly,
    }))
  }
  if (Array.isArray(cs.Secrets)) {
    form.secretsRef = cs.Secrets.map((s: any) => ({
      resourceId: s.SecretID || s.Secret?.ID || s.Secret || '',
      target: s.File?.Name || s.FileName || '',
    }))
  }
  if (Array.isArray(cs.Configs)) {
    form.configsRef = cs.Configs.map((c: any) => ({
      resourceId: c.ConfigID || c.Config?.ID || c.Config || '',
      target: c.File?.Name || c.FileName || '',
    }))
  }
  // docker-java 里这个字段序列化为 Healthcheck（小写 c），写成 HealthCheck 永远取不到值
  if (cs.Healthcheck?.Test?.length && cs.Healthcheck.Test[0] !== 'NONE') {
    form.healthTest = cs.Healthcheck.Test.join(' ')
    form.healthIntervalSec = Math.round((cs.Healthcheck.Interval || 0) / 1e9)
    form.healthTimeoutSec = Math.round((cs.Healthcheck.Timeout || 0) / 1e9)
    form.healthStartPeriodSec = Math.round((cs.Healthcheck.StartPeriod || 0) / 1e9)
    form.healthRetries = cs.Healthcheck.Retries || 0
  }
  form.networks = (spec.Networks || []).map((n: any) => n.Target || n.Target)
  const ports = spec.EndpointSpec?.Ports || []
  form.ports = ports.map((p: any) => ({
    publishedPort: String(p.PublishedPort ?? ''),
    targetPort: String(p.TargetPort ?? ''),
    protocol: (p.Protocol || 'tcp').toLowerCase(),
    publishMode: (p.PublishMode || 'ingress').toLowerCase(),
    name: p.Name || '',
  }))
  if (spec.EndpointSpec?.Mode) {
    const m = String(spec.EndpointSpec.Mode).toLowerCase()
    form.endpointMode = m === 'dnsrr' ? 'dnsrr' : 'vip'
  }
  const rr = tt.Resources || {}
  form.limitsCpus = (rr.Limits?.NanoCPUs || 0) / 1e9
  form.limitsMemMb = Math.round((rr.Limits?.MemoryBytes || 0) / 1024 / 1024)
  form.resCpus = (rr.Reservations?.NanoCPUs || 0) / 1e9
  form.resMemMb = Math.round((rr.Reservations?.MemoryBytes || 0) / 1024 / 1024)
  const rp = tt.RestartPolicy || {}
  if (rp.Condition) {
    form.restartCondition = String(rp.Condition).toLowerCase() === 'on_failure' ? 'on-failure' : String(rp.Condition).toLowerCase()
    form.restartDelaySec = Math.round((rp.Delay || 0) / 1e9)
    form.restartMaxAttempts = rp.MaxAttempts || 0
    form.restartWindowSec = Math.round((rp.Window || 0) / 1e9)
  }
  const pl = tt.Placement || {}
  form.placementConstraints = (pl.Constraints || []).join('\n')
  form.placementMaxReplicas = pl.MaxReplicas || 0
  const uc = spec.UpdateConfig || {}
  form.updateParallelism = uc.Parallelism ?? 1
  form.updateDelaySec = Math.round((uc.Delay || 0) / 1e9)
  if (uc.FailureAction) form.updateFailureAction = String(uc.FailureAction).toLowerCase()
  if (uc.Order) form.updateOrder = String(uc.Order).toLowerCase().replace('_', '-')
  const rb = spec.RollbackConfig || {}
  form.rollbackParallelism = rb.Parallelism ?? 1
  form.rollbackDelaySec = Math.round((rb.Delay || 0) / 1e9)
  if (rb.FailureAction) form.rollbackFailureAction = String(rb.FailureAction).toLowerCase()
  form.replicas = spec.Mode?.Replicated?.Replicas ?? 1
  form.mode = spec.Mode?.Global ? 'global' : 'replicated'
}

function splitOnSpace(s: string): string[] {
  if (!s) return []
  // 保留引号内的整体（如 'daemon off;'）
  const out: string[] = []
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(s)) !== null) out.push(m[1] ?? m[2] ?? m[3])
  return out
}

function buildPayload(): any {
  const p: any = {
    name: form.name.trim(),
    image: (registryPrefix.value + form.image.trim()),
    registryId: form.registryId,
    mode: form.mode,
    replicas: form.mode === 'replicated' ? form.replicas : undefined,
  }
  const entry = splitOnSpace(form.entrypoint); if (entry.length) p.entrypoint = entry
  const cmd = splitOnSpace(form.command); if (cmd.length) p.command = cmd
  if (form.workDir.trim()) p.workDir = form.workDir.trim()
  if (form.user.trim()) p.user = form.user.trim()
  if (form.hostname.trim()) p.hostname = form.hostname.trim()
  if (form.stopSignal.trim()) p.stopSignal = form.stopSignal.trim()
  if (form.stopGracePeriodSec > 0) p.stopGracePeriod = form.stopGracePeriodSec
  if (form.tty) p.tty = true
  if (form.readOnly) p.readOnly = true
  const env: Record<string, string> = {}
  form.env.forEach((e) => { if (e.key.trim()) env[e.key.trim()] = e.value })
  if (Object.keys(env).length) p.env = env
  const labels: Record<string, string> = {}
  form.labels.forEach((l) => { if (l.key.trim()) labels[l.key.trim()] = l.value })
  if (Object.keys(labels).length) p.labels = labels
  if (form.networks.length) p.networks = form.networks.slice()
  if (form.endpointMode) p.endpointMode = form.endpointMode
  const ports = form.ports
    .filter((x) => x.targetPort.trim())
    .map((x) => ({
      publishedPort: parseInt(x.publishedPort) || undefined,
      targetPort: parseInt(x.targetPort),
      protocol: x.protocol || 'tcp',
      publishMode: x.publishMode || 'ingress',
      name: x.name.trim() || undefined,
    }))
  if (ports.length) p.ports = ports
  const mounts = form.mounts
    .filter((m) => m.target.trim())
    .map((m) => ({ type: m.type, source: m.source.trim() || undefined, target: m.target.trim(), readOnly: !!m.readOnly }))
  if (mounts.length) p.mounts = mounts
  const secs = form.secretsRef
    .filter((x) => x.resourceId)
    .map((x) => {
      const opt = secretOptions.value.find((s) => s.id === x.resourceId)
      return { id: x.resourceId, name: opt?.name || '', target: x.target.trim() || undefined }
    })
  if (secs.length) p.secrets = secs
  const cfgs = form.configsRef
    .filter((x) => x.resourceId)
    .map((x) => {
      const opt = configOptions.value.find((c) => c.id === x.resourceId)
      return { id: x.resourceId, name: opt?.name || '', target: x.target.trim() || undefined }
    })
  if (cfgs.length) p.configs = cfgs
  if (form.limitsCpus > 0 || form.limitsMemMb > 0 || form.resCpus > 0 || form.resMemMb > 0) {
    p.resources = {
      limits: { cpus: form.limitsCpus || undefined, memoryMb: form.limitsMemMb || undefined },
      reservations: { cpus: form.resCpus || undefined, memoryMb: form.resMemMb || undefined },
    }
  }
  const testArr = splitOnSpace(form.healthTest)
  if (testArr.length) {
    p.healthcheck = {
      test: testArr,
      intervalSec: form.healthIntervalSec || undefined,
      timeoutSec: form.healthTimeoutSec || undefined,
      startPeriodSec: form.healthStartPeriodSec || undefined,
      retries: form.healthRetries || undefined,
    }
  }
  if (form.restartCondition) {
    p.restart = {
      condition: form.restartCondition,
      delaySec: form.restartDelaySec || undefined,
      maxAttempts: form.restartMaxAttempts || undefined,
      windowSec: form.restartWindowSec || undefined,
    }
  }
  const constraints = (form.placementConstraints || '').split(/\r?\n/).map((s) => s.trim()).filter(Boolean)
  if (constraints.length || form.placementMaxReplicas > 0) {
    p.placement = { constraints, maxReplicas: form.placementMaxReplicas || undefined }
  }
  if (form.updateParallelism || form.updateDelaySec || form.updateFailureAction || form.updateOrder) {
    p.update = {
      parallelism: form.updateParallelism,
      delaySec: form.updateDelaySec || undefined,
      failureAction: form.updateFailureAction || undefined,
      order: form.updateOrder || undefined,
    }
  }
  if (form.rollbackParallelism || form.rollbackDelaySec || form.rollbackFailureAction) {
    p.rollback = {
      parallelism: form.rollbackParallelism,
      delaySec: form.rollbackDelaySec || undefined,
      failureAction: form.rollbackFailureAction || undefined,
    }
  }
  return p
}

async function submit() {
  const envId = envStore.currentEnvId
  if (!envId) { ElMessage.warning('请先选择环境'); return }
  if (!form.name.trim()) { ElMessage.warning('服务名称不能为空'); activeTab.value = 'basic'; return }
  if (!form.image.trim()) { ElMessage.warning('镜像不能为空'); activeTab.value = 'basic'; return }
  const payload = buildPayload()
  submitting.value = true
  try {
    if (editingId.value) {
      await updateService(envId, editingId.value, payload)
      ElMessage.success('服务已更新')
      router.push(`/swarm/services/${editingId.value}`)
    } else {
      await createService(envId, payload)
      ElMessage.success('服务创建成功')
      router.push('/swarm/services')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.form-footer { display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid #ebeef5; padding-top: 16px; margin-top: 8px; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4; }
.unit { font-size: 12px; color: #909399; margin-left: 6px; margin-right: 12px; }
.lbl { font-size: 13px; color: #606266; margin-right: 6px; }
.kv-list { width: 100%; }
.kv-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.kv-eq { color: #909399; }
:deep(.el-tabs__header) { margin-bottom: 12px; }
:deep(.el-divider__text) { font-size: 13px; color: #303133; font-weight: 500; }
</style>
