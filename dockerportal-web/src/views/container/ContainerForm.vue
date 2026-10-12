<template>
  <div class="container-form-page">
    <div class="page-header">
      <h2>{{ cloneId ? '克隆容器' : '创建容器' }}</h2>
      <div class="header-actions">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </div>
    </div>

    <el-card shadow="never" v-loading="loadingInitial">
      <el-alert v-if="cloneId" type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="已按源容器配置预填表单（名称加 -copy 后缀）；端口/名称等可能冲突的字段请按需调整后再创建。" />
      <el-form :model="form" label-width="120px" size="default">
        <el-tabs v-model="activeTab">
          <!-- ==================== 基础 ==================== -->
          <el-tab-pane label="基础" name="basic">
            <el-form-item label="镜像仓库">
              <el-select v-model="form.registryId" style="width:100%" @change="onRegistryChange">
                <el-option v-for="r in registries" :key="r.id" :label="registryLabel(r)" :value="r.id" />
              </el-select>
              <div class="hint">内置 Docker Hub 为默认；私有仓库请先在「设置 → 镜像仓库」配置。</div>
            </el-form-item>
            <el-form-item label="镜像名" required>
              <el-input v-model="form.image">
                <template v-if="registryPrefix" #prepend>{{ registryPrefix }}</template>
              </el-input>
              <div class="hint">例如 nginx:1.25、my-app:1.0.0</div>
            </el-form-item>
            <el-form-item label="本地无镜像时">
              <el-checkbox v-model="form.pullIfMissing">自动从仓库拉取（等效 docker run 隐式 pull）</el-checkbox>
            </el-form-item>
            <el-form-item label="容器名称">
              <el-input v-model="form.name" placeholder="留空则由 Docker 自动生成" />
            </el-form-item>
            <el-form-item label="主机名">
              <el-input v-model="form.hostname" placeholder="--hostname，留空默认容器 id" />
            </el-form-item>
            <el-form-item label="域名">
              <el-input v-model="form.domainName" placeholder="--domainname，如 example.local" />
            </el-form-item>
            <el-form-item label="工作目录">
              <el-input v-model="form.workingDir" placeholder="-w，如 /app" />
            </el-form-item>
            <el-form-item label="运行用户">
              <el-input v-model="form.user" placeholder="-u，如 1000:1000 或 appuser" />
            </el-form-item>
            <el-form-item label="标签">
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

          <!-- ==================== 启动 ==================== -->
          <el-tab-pane label="启动" name="startup">
            <el-form-item label="Entrypoint">
              <el-input v-model="form.entrypoint" placeholder="--entrypoint，例如：/bin/sh -c" />
              <div class="hint">按空白拆分（引号内保留整体），覆盖镜像默认入口。</div>
            </el-form-item>
            <el-form-item label="命令 Command">
              <el-input v-model="form.command" placeholder="docker run 尾部命令，例如：nginx -g 'daemon off;'" />
              <div class="hint">留空沿用镜像默认 CMD。</div>
            </el-form-item>
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
            <el-form-item label="终端">
              <el-checkbox v-model="form.tty">分配伪终端 (-t)</el-checkbox>
              <el-checkbox v-model="form.stdinOpen">保持标准输入 (-i)</el-checkbox>
              <el-checkbox v-model="form.init">启动 init 进程 (--init)</el-checkbox>
            </el-form-item>
            <el-form-item label="停止信号">
              <el-input v-model="form.stopSignal" placeholder="--stop-signal，如 SIGTERM" style="width:220px" />
            </el-form-item>
            <el-form-item label="停止超时">
              <el-input-number v-model="form.stopTimeoutSec" :min="0" style="width:120px" />
              <span class="unit">秒，0 为默认 10s（--stop-timeout）</span>
            </el-form-item>
            <el-divider content-position="left">健康检查 Healthcheck</el-divider>
            <el-form-item label="检查命令">
              <el-input v-model="form.healthTest" placeholder='例如：CMD-SHELL curl -f http://localhost/ || exit 1' />
              <div class="hint">首个词用 CMD / CMD-SHELL / NONE，与 docker --health-cmd 一致。</div>
            </el-form-item>
            <el-form-item label="检查参数">
              <span class="lbl">间隔</span><el-input-number v-model="form.healthIntervalSec" :min="0" style="width:90px" /><span class="unit">s</span>
              <span class="lbl">超时</span><el-input-number v-model="form.healthTimeoutSec" :min="0" style="width:90px" /><span class="unit">s</span>
              <span class="lbl">起始延迟</span><el-input-number v-model="form.healthStartPeriodSec" :min="0" style="width:90px" /><span class="unit">s</span>
              <span class="lbl">重试</span><el-input-number v-model="form.healthRetries" :min="0" style="width:90px" />
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 网络 ==================== -->
          <el-tab-pane label="网络" name="network">
            <el-form-item label="网络模式">
              <el-select v-model="form.networkMode" filterable allow-create default-first-option placeholder="bridge" style="width:320px">
                <el-option label="bridge（默认）" value="bridge" />
                <el-option label="host" value="host" />
                <el-option label="none" value="none" />
                <el-option v-for="n in networks" :key="n.Id || n.Name" :label="`${n.Name || n.Id}`" :value="n.Name || n.Id" />
              </el-select>
              <div class="hint">也可直接输入 container:&lt;id&gt; 复用其他容器的网络栈。</div>
            </el-form-item>
            <el-form-item label="网络别名">
              <el-select v-model="form.networkAliases" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="输入后回车添加" style="width:100%" />
            </el-form-item>
            <el-form-item label="容器链接 --link">
              <el-select v-model="form.links" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="container[:alias]，回车添加" style="width:100%" />
            </el-form-item>
            <el-form-item label="额外 hosts">
              <el-select v-model="form.extraHosts" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="hostname:ip，如 my.host:10.0.0.5" style="width:100%" />
            </el-form-item>
            <el-form-item label="DNS">
              <el-select v-model="form.dns" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="--dns，回车添加" style="width:100%" />
            </el-form-item>
            <el-form-item label="DNS 搜索域">
              <el-select v-model="form.dnsSearch" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="--dns-search" style="width:100%" />
            </el-form-item>
            <el-form-item label="MAC 地址">
              <el-input v-model="form.macAddress" placeholder="02:42:ac:11:00:02" style="width:280px" />
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 端口 ==================== -->
          <el-tab-pane label="端口" name="ports">
            <el-form-item label="发布全部">
              <el-checkbox v-model="form.publishAll">-P：把镜像 EXPOSE 的端口全部随机映射到主机</el-checkbox>
            </el-form-item>
            <el-form-item label="端口映射">
              <div class="kv-list">
                <div v-for="(p, i) in form.ports" :key="i" class="kv-row">
                  <el-input v-model="p.hostIp" placeholder="绑定IP(可选)" style="width:130px" />
                  <el-input v-model="p.hostPort" placeholder="主机端口" style="width:110px" />
                  <el-input v-model="p.hostPortEnd" placeholder="范围结束(可选)" style="width:120px" />
                  <span class="kv-eq">→</span>
                  <el-input v-model="p.containerPort" placeholder="容器端口" style="width:110px" />
                  <el-select v-model="p.protocol" style="width:80px">
                    <el-option label="tcp" value="tcp" />
                    <el-option label="udp" value="udp" />
                  </el-select>
                  <el-button size="small" text type="danger" @click="form.ports.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.ports.push({ hostIp: '', hostPort: '', hostPortEnd: '', containerPort: '', protocol: 'tcp' })">+ 添加端口</el-button>
              </div>
              <div class="hint">主机端口留空 = 随机端口；只填容器端口也可以。</div>
            </el-form-item>
            <el-form-item label="仅暴露不映射">
              <el-select v-model="form.exposedPorts" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="如 8080、9000/udp，回车添加（--expose）" style="width:100%" />
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
                  <el-input v-model="v.source" :placeholder="v.type === 'volume' ? '卷名（留空自动建匿名卷）' : v.type === 'tmpfs' ? '可留空' : '主机路径'" style="flex:1" :disabled="v.type === 'tmpfs'" />
                  <span class="kv-eq">→</span>
                  <el-input v-model="v.target" placeholder="容器内绝对路径" style="flex:1" />
                  <el-checkbox v-model="v.readOnly" :disabled="v.type === 'tmpfs'">只读</el-checkbox>
                  <el-button size="small" text type="danger" @click="form.mounts.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.mounts.push({ type: 'bind', source: '', target: '', readOnly: false })">+ 添加挂载（--mount）</el-button>
              </div>
            </el-form-item>
            <el-form-item label="tmpfs 路径">
              <el-select v-model="form.tmpfs" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="/dev/shm、/tmp 等，回车添加（--tmpfs）" style="width:100%" />
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 资源 ==================== -->
          <el-tab-pane label="资源" name="resources">
            <el-form-item label="内存">
              <el-input-number v-model="form.memoryMb" :min="0" :step="128" controls-position="right" style="width:150px" />
              <span class="unit">MB（--memory），0 不限</span>
              <span class="lbl" style="margin-left:24px">swap</span>
              <el-input-number v-model="form.memorySwapMb" :min="0" :step="128" controls-position="right" style="width:150px" />
              <span class="unit">MB（--memory-swap），-1 见下</span>
            </el-form-item>
            <el-form-item label="软限/保留">
              <el-input-number v-model="form.memoryReservationMb" :min="0" :step="64" controls-position="right" style="width:150px" />
              <span class="unit">MB（--memory-reservation）</span>
            </el-form-item>
            <el-form-item label="CPU">
              <el-input-number v-model="form.cpuCores" :min="0" :step="0.5" :precision="2" :controls="false" style="width:110px" />
              <span class="unit">核（--cpus），0 不限</span>
              <span class="lbl" style="margin-left:24px">shares</span>
              <el-input-number v-model="form.cpuShares" :min="0" :step="128" controls-position="right" style="width:130px" />
              <span class="unit">（--cpu-shares）</span>
            </el-form-item>
            <el-form-item label="CPU 绑核">
              <el-input v-model="form.cpusetCpus" placeholder="--cpuset-cpus，如 0-2,4" style="width:240px" />
            </el-form-item>
            <el-form-item label="shm 大小">
              <el-input-number v-model="form.shmSizeMb" :min="0" :step="64" controls-position="right" style="width:150px" />
              <span class="unit">MB（--shm-size）</span>
            </el-form-item>
            <el-form-item label="进程数限制">
              <el-input-number v-model="form.pidsLimit" :min="0" :step="100" controls-position="right" style="width:150px" />
              <span class="unit">（--pids-limit）</span>
              <span class="lbl" style="margin-left:24px">oom-score-adj</span>
              <el-input-number v-model="form.oomScoreAdj" :min="-1000" :max="1000" :step="100" controls-position="right" style="width:130px" />
            </el-form-item>
            <el-form-item label="OOM">
              <el-checkbox v-model="form.oomKillDisable">禁止 OOM 杀进程（--oom-kill-disable）</el-checkbox>
            </el-form-item>
            <el-form-item label="ulimit">
              <div class="kv-list">
                <div v-for="(u, i) in form.ulimits" :key="i" class="kv-row">
                  <el-input v-model="u.name" placeholder="如 nofile、nproc" style="width:180px" />
                  <el-input v-model="u.soft" placeholder="soft" style="width:120px" />
                  <el-input v-model="u.hard" placeholder="hard" style="width:120px" />
                  <el-button size="small" text type="danger" @click="form.ulimits.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.ulimits.push({ name: '', soft: '', hard: '' })">+ 添加 ulimit（--ulimit）</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 安全 ==================== -->
          <el-tab-pane label="安全" name="security">
            <el-form-item label="特权模式">
              <el-checkbox v-model="form.privileged">--privileged（开放全部设备与能力，慎用）</el-checkbox>
              <el-checkbox v-model="form.readOnly">根文件系统只读（--read-only）</el-checkbox>
            </el-form-item>
            <el-form-item label="增加能力">
              <el-select v-model="form.capAdd" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="如 SYS_ADMIN、NET_BIND_SERVICE（CAP_ 前缀可省略）" style="width:100%">
                <el-option v-for="c in COMMON_CAPS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="丢弃能力">
              <el-select v-model="form.capDrop" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="--cap-drop" style="width:100%">
                <el-option v-for="c in COMMON_CAPS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="security-opt">
              <el-select v-model="form.securityOpts" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="如 seccomp=unconfined、apparmor=unconfined" style="width:100%" />
            </el-form-item>
            <el-form-item label="附加用户组">
              <el-select v-model="form.groupAdd" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="--group-add，如 docker、999" style="width:100%" />
            </el-form-item>
            <el-form-item label="设备">
              <el-select v-model="form.devices" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="--device，如 /dev/ttyUSB0" style="width:100%" />
            </el-form-item>
            <el-form-item label="sysctl">
              <div class="kv-list">
                <div v-for="(s, i) in form.sysctls" :key="i" class="kv-row">
                  <el-input v-model="s.key" placeholder="net.ipv4.ip_forward" style="width:260px" />
                  <span class="kv-eq">=</span>
                  <el-input v-model="s.value" placeholder="1" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.sysctls.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.sysctls.push({ key: '', value: '' })">+ 添加 sysctl（--sysctl）</el-button>
              </div>
            </el-form-item>
          </el-tab-pane>

          <!-- ==================== 高级 ==================== -->
          <el-tab-pane label="高级" name="advanced">
            <el-form-item label="重启策略">
              <el-select v-model="form.restartPolicy" style="width:220px">
                <el-option label="不重启（no）" value="no" />
                <el-option label="总是重启（always）" value="always" />
                <el-option label="除非手动停止（unless-stopped）" value="unless-stopped" />
                <el-option label="失败时重启（on-failure）" value="on-failure" />
              </el-select>
              <template v-if="form.restartPolicy === 'on-failure'">
                <span class="lbl" style="margin-left:16px">最大重试</span>
                <el-input-number v-model="form.restartMaxRetry" :min="0" style="width:110px" />
                <span class="unit">0 为无限</span>
              </template>
            </el-form-item>
            <el-form-item label="退出即删除">
              <el-checkbox v-model="form.autoRemove">--rm：容器停止后自动删除</el-checkbox>
              <el-checkbox v-model="form.startAfterCreate">创建后立即启动</el-checkbox>
            </el-form-item>
            <el-divider content-position="left">日志驱动</el-divider>
            <el-form-item label="日志驱动">
              <el-select v-model="form.logDriver" clearable placeholder="默认（json-file）" style="width:200px">
                <el-option v-for="d in LOG_DRIVERS" :key="d" :label="d" :value="d" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.logDriver" label="日志参数">
              <div class="kv-list">
                <div v-for="(o, i) in form.logOpts" :key="i" class="kv-row">
                  <el-input v-model="o.key" placeholder="如 max-size" style="width:220px" />
                  <span class="kv-eq">=</span>
                  <el-input v-model="o.value" placeholder="如 10m" style="flex:1" />
                  <el-button size="small" text type="danger" @click="form.logOpts.splice(i, 1)">删除</el-button>
                </div>
                <el-button size="small" @click="form.logOpts.push({ key: '', value: '' })">+ 添加 --log-opt</el-button>
              </div>
            </el-form-item>
            <el-divider content-position="left">命名空间与运行时</el-divider>
            <el-form-item label="PID 模式">
              <el-input v-model="form.pidMode" placeholder="--pid，如 host" style="width:200px" />
              <span class="lbl" style="margin-left:16px">IPC</span>
              <el-input v-model="form.ipcMode" placeholder="--ipc，如 host" style="width:200px" />
            </el-form-item>
            <el-form-item label="UTS 模式">
              <el-input v-model="form.utsMode" placeholder="--uts，如 host" style="width:200px" />
              <span class="lbl" style="margin-left:16px">userns</span>
              <el-input v-model="form.usernsMode" placeholder="--userns" style="width:200px" />
            </el-form-item>
            <el-form-item label="cgroup 父级">
              <el-input v-model="form.cgroupParent" placeholder="--cgroup-parent" style="width:300px" />
            </el-form-item>
            <el-form-item label="运行时">
              <el-input v-model="form.runtime" placeholder="--runtime，如 runc / runsc（留空用默认）" style="width:300px" />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <div class="form-footer">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createContainer, containerAction, inspectContainer } from '@/api/container'
import { getRegistries } from '@/api/registry'
import { getNetworks } from '@/api/network'
import { useEnvironmentStore } from '@/stores/environment'

const route = useRoute()
const router = useRouter()
const envStore = useEnvironmentStore()
const cloneId = computed(() => (route.query.clone as string) || null)

const COMMON_CAPS = ['SYS_ADMIN', 'NET_ADMIN', 'NET_BIND_SERVICE', 'NET_RAW', 'IPC_LOCK', 'SYS_PTRACE', 'SYS_NICE', 'CHOWN', 'FOWNER', 'SETUID', 'SETGID', 'DAC_OVERRIDE', 'ALL']
const LOG_DRIVERS = ['json-file', 'syslog', 'journald', 'local', 'none', 'fluentd', 'gelf', 'awslogs']

const activeTab = ref('basic')
const submitting = ref(false)
const loadingInitial = ref(false)
const registries = ref<any[]>([])
const networks = ref<any[]>([])

function defaultForm() {
  return {
    registryId: 0 as number,
    image: '',
    pullIfMissing: true,
    name: '',
    hostname: '',
    domainName: '',
    workingDir: '',
    user: '',
    labels: [] as { key: string; value: string }[],
    entrypoint: '',
    command: '',
    env: [] as { key: string; value: string }[],
    tty: false,
    stdinOpen: false,
    init: false,
    stopSignal: '',
    stopTimeoutSec: 0,
    healthTest: '',
    healthIntervalSec: 0,
    healthTimeoutSec: 0,
    healthStartPeriodSec: 0,
    healthRetries: 0,
    networkMode: '',
    networkAliases: [] as string[],
    links: [] as string[],
    extraHosts: [] as string[],
    dns: [] as string[],
    dnsSearch: [] as string[],
    macAddress: '',
    publishAll: false,
    ports: [] as { hostIp: string; hostPort: string; hostPortEnd: string; containerPort: string; protocol: string }[],
    exposedPorts: [] as string[],
    mounts: [] as { type: string; source: string; target: string; readOnly: boolean }[],
    tmpfs: [] as string[],
    memoryMb: 0,
    memorySwapMb: 0,
    memoryReservationMb: 0,
    cpuCores: 0,
    cpuShares: 0,
    cpusetCpus: '',
    shmSizeMb: 0,
    pidsLimit: 0,
    oomScoreAdj: 0,
    oomKillDisable: false,
    ulimits: [] as { name: string; soft: string; hard: string }[],
    privileged: false,
    readOnly: false,
    capAdd: [] as string[],
    capDrop: [] as string[],
    securityOpts: [] as string[],
    groupAdd: [] as string[],
    devices: [] as string[],
    sysctls: [] as { key: string; value: string }[],
    restartPolicy: 'no',
    restartMaxRetry: 0,
    autoRemove: false,
    startAfterCreate: true,
    logDriver: '',
    logOpts: [] as { key: string; value: string }[],
    pidMode: '',
    ipcMode: '',
    utsMode: '',
    usernsMode: '',
    cgroupParent: '',
    runtime: '',
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
    if (cloneId.value) {
      const info = await inspectContainer(envId, cloneId.value)
      hydrateFromInspect(info)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载表单数据失败')
  } finally {
    loadingInitial.value = false
  }
})

/** 克隆：从 inspect 反推主要配置到表单（名称加 -copy 后缀避免冲突；网段/端口等可能冲突的字段保留原值供用户改） */
function hydrateFromInspect(info: any) {
  const cfg = info?.Config || {}
  const host = info?.HostConfig || {}
  form.image = cfg.Image || ''
  const oldName = (info?.Name || '').replace(/^\//, '')
  form.name = oldName ? `${oldName}-copy` : ''
  form.hostname = cfg.Hostname || ''
  form.workingDir = cfg.WorkingDir || ''
  form.user = cfg.User || ''
  form.entrypoint = (cfg.Entrypoint || []).join(' ')
  form.command = (cfg.Cmd || []).join(' ')
  form.env = (cfg.Env || []).map((e: string) => {
    const i = e.indexOf('='); return i < 0 ? { key: e, value: '' } : { key: e.slice(0, i), value: e.slice(i + 1) }
  })
  form.labels = Object.entries(cfg.Labels || {})
    .filter(([k]) => !k.startsWith('com.docker.'))
    .map(([k, v]) => ({ key: k, value: String(v) }))
  form.tty = !!cfg.Tty
  form.stdinOpen = !!cfg.OpenStdin
  form.stopSignal = cfg.StopSignal || ''
  form.stopTimeoutSec = cfg.StopTimeout || 0
  const hc = cfg.Healthcheck
  if (hc?.Test?.length && hc.Test[0] !== 'NONE') {
    form.healthTest = hc.Test.join(' ')
    form.healthIntervalSec = Math.round((hc.Interval || 0) / 1e9)
    form.healthTimeoutSec = Math.round((hc.Timeout || 0) / 1e9)
    form.healthStartPeriodSec = Math.round((hc.StartPeriod || 0) / 1e9)
    form.healthRetries = hc.Retries || 0
  }
  form.networkMode = host.NetworkMode && !['default', 'bridge'].includes(host.NetworkMode) ? host.NetworkMode : ''
  form.dns = host.Dns || []
  form.extraHosts = host.ExtraHosts || []
  form.publishAll = !!host.PublishAllPorts
  // 端口：PortBindings {"80/tcp": [{HostIp, HostPort}]}
  form.ports = Object.entries(host.PortBindings || {}).map(([key, binds]: [string, any]) => {
    const [cp, proto] = key.split('/')
    const b = (binds || [{}])[0] || {}
    return { hostIp: b.HostIp || '', hostPort: b.HostPort || '', hostPortEnd: '', containerPort: cp, protocol: proto || 'tcp' }
  })
  form.mounts = (info?.Mounts || []).map((m: any) => ({
    type: (m.Type || 'bind').toLowerCase(),
    source: m.Type === 'volume' ? (m.Name || '') : (m.Source || ''),
    target: m.Destination || '',
    readOnly: m.RW === false,
  }))
  form.memoryMb = Math.round((host.Memory || 0) / 1024 / 1024)
  form.memorySwapMb = Math.round((host.MemorySwap || 0) / 1024 / 1024)
  form.memoryReservationMb = Math.round((host.MemoryReservation || 0) / 1024 / 1024)
  form.cpuShares = host.CpuShares || 0
  form.cpusetCpus = host.CpusetCpus || ''
  form.cpuCores = host.NanoCpus ? host.NanoCpus / 1e9 : (host.CpuQuota && host.CpuPeriod ? host.CpuQuota / host.CpuPeriod : 0)
  form.shmSizeMb = Math.round((host.ShmSize || 0) / 1024 / 1024)
  form.pidsLimit = host.PidsLimit || 0
  form.privileged = !!host.Privileged
  form.readOnly = !!host.ReadonlyRootfs
  form.capAdd = host.CapAdd || []
  form.capDrop = host.CapDrop || []
  form.securityOpts = host.SecurityOpt || []
  form.ulimits = (host.Ulimits || []).map((u: any) => ({ name: u.Name, soft: String(u.Soft ?? ''), hard: String(u.Hard ?? '') }))
  form.restartPolicy = host.RestartPolicy?.Name || 'no'
  form.restartMaxRetry = host.RestartPolicy?.MaximumRetryCount || 0
  form.autoRemove = !!host.AutoRemove
  form.init = !!host.Init
  form.pidMode = host.PidMode || ''
  form.ipcMode = host.IpcMode || ''
  if (host.LogConfig?.Type && host.LogConfig.Type !== 'json-file') {
    form.logDriver = host.LogConfig.Type
    form.logOpts = Object.entries(host.LogConfig.Config || {}).map(([k, v]) => ({ key: k, value: String(v) }))
  }
  form.startAfterCreate = true
}

const registryPrefix = computed(() => {
  const r = registries.value.find((x) => x.id === form.registryId)
  return r && !r.builtin && r.url ? r.url + '/' : ''
})
function registryLabel(r: any) {
  if (r.builtin) return `${r.name}（内置默认）`
  return `${r.name}（${r.url || 'Docker Hub'}）`
}
function onRegistryChange() {
  if (!form.image) return
  const r = registries.value.find((x) => x.id === form.registryId)
  if (r?.url && form.image.startsWith(r.url + '/')) return
  const idx = form.image.indexOf('/')
  if (idx > 0) {
    const first = form.image.slice(0, idx)
    if (first.includes('.') || first.includes(':')) form.image = form.image.slice(idx + 1)
  }
}

function splitOnSpace(s: string): string[] {
  if (!s) return []
  const out: string[] = []
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(s)) !== null) out.push(m[1] ?? m[2] ?? m[3])
  return out
}

function kvMap(list: { key: string; value: string }[]): Record<string, string> | undefined {
  const map: Record<string, string> = {}
  list.forEach((x) => { if (x.key.trim()) map[x.key.trim()] = x.value })
  return Object.keys(map).length ? map : undefined
}

function buildPayload(): any {
  const p: any = { image: (registryPrefix.value + form.image.trim()) }
  p.registryId = form.registryId
  if (form.pullIfMissing) p.pullIfMissing = true
  if (form.name.trim()) p.name = form.name.trim()
  if (form.hostname.trim()) p.hostname = form.hostname.trim()
  if (form.domainName.trim()) p.domainName = form.domainName.trim()
  if (form.workingDir.trim()) p.workingDir = form.workingDir.trim()
  if (form.user.trim()) p.user = form.user.trim()
  const labels = kvMap(form.labels); if (labels) p.labels = labels

  const entry = splitOnSpace(form.entrypoint); if (entry.length) p.entrypoint = entry
  const cmd = splitOnSpace(form.command); if (cmd.length) p.command = cmd
  const env = form.env.filter((e) => e.key.trim()).map((e) => `${e.key.trim()}=${e.value}`)
  if (env.length) p.env = env
  if (form.tty) p.tty = true
  if (form.stdinOpen) p.stdinOpen = true
  if (form.init) p.init = true
  if (form.stopSignal.trim()) p.stopSignal = form.stopSignal.trim()
  if (form.stopTimeoutSec > 0) p.stopTimeout = form.stopTimeoutSec
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

  if (form.networkMode) p.networkMode = form.networkMode
  if (form.networkAliases.length) p.networkAliases = form.networkAliases.slice()
  if (form.links.length) p.links = form.links.slice()
  if (form.extraHosts.length) p.extraHosts = form.extraHosts.slice()
  if (form.dns.length) p.dns = form.dns.slice()
  if (form.dnsSearch.length) p.dnsSearch = form.dnsSearch.slice()
  if (form.macAddress.trim()) p.macAddress = form.macAddress.trim()

  if (form.publishAll) p.publishAll = true
  const ports = form.ports
    .filter((x) => x.containerPort.trim())
    .map((x) => ({
      hostIp: x.hostIp.trim() || undefined,
      hostPort: x.hostPort.trim() || undefined,
      hostPortRangeEnd: x.hostPortEnd.trim() || undefined,
      containerPort: x.containerPort.trim(),
      protocol: x.protocol || 'tcp',
    }))
  if (ports.length) p.portBindings = ports
  if (form.exposedPorts.length) p.exposedPorts = form.exposedPorts.slice()

  const mounts = form.mounts
    .filter((m) => m.target.trim())
    .map((m) => ({ type: m.type, source: m.source.trim() || undefined, target: m.target.trim(), readOnly: !!m.readOnly }))
  if (mounts.length) p.volumeMounts = mounts
  if (form.tmpfs.length) p.tmpfs = form.tmpfs.slice()

  if (form.memoryMb > 0) p.memoryLimit = Math.round(form.memoryMb * 1024 * 1024)
  if (form.memorySwapMb !== 0) p.memorySwap = Math.round(form.memorySwapMb * 1024 * 1024)
  if (form.memoryReservationMb > 0) p.memoryReservation = Math.round(form.memoryReservationMb * 1024 * 1024)
  if (form.cpuCores > 0) p.cpuLimit = Math.round(form.cpuCores * 1e9)
  if (form.cpuShares > 0) p.cpuShares = form.cpuShares
  if (form.cpusetCpus.trim()) p.cpusetCpus = form.cpusetCpus.trim()
  if (form.shmSizeMb > 0) p.shmSize = Math.round(form.shmSizeMb * 1024 * 1024)
  if (form.pidsLimit > 0) p.pidsLimit = form.pidsLimit
  if (form.oomScoreAdj !== 0) p.oomScoreAdj = form.oomScoreAdj
  if (form.oomKillDisable) p.oomKillDisable = true
  const ulimits = form.ulimits
    .filter((u) => u.name.trim())
    .map((u) => ({ name: u.name.trim(), soft: u.soft === '' ? undefined : Number(u.soft), hard: u.hard === '' ? undefined : Number(u.hard) }))
  if (ulimits.length) p.ulimits = ulimits

  if (form.privileged) p.privileged = true
  if (form.readOnly) p.readOnly = true
  if (form.capAdd.length) p.capAdd = form.capAdd.slice()
  if (form.capDrop.length) p.capDrop = form.capDrop.slice()
  if (form.securityOpts.length) p.securityOpts = form.securityOpts.slice()
  if (form.groupAdd.length) p.groupAdd = form.groupAdd.slice()
  if (form.devices.length) p.devices = form.devices.slice()
  const sysctls = kvMap(form.sysctls); if (sysctls) p.sysctls = sysctls

  if (form.restartPolicy && form.restartPolicy !== 'no') {
    p.restartPolicy = form.restartPolicy === 'on-failure' && form.restartMaxRetry > 0
      ? `on-failure:${form.restartMaxRetry}` : form.restartPolicy
  }
  if (form.autoRemove) p.autoRemove = true
  if (form.logDriver) {
    p.logDriver = form.logDriver
    const opts = kvMap(form.logOpts); if (opts) p.logOpts = opts
  }
  if (form.pidMode.trim()) p.pidMode = form.pidMode.trim()
  if (form.ipcMode.trim()) p.ipcMode = form.ipcMode.trim()
  if (form.utsMode.trim()) p.utsMode = form.utsMode.trim()
  if (form.usernsMode.trim()) p.usernsMode = form.usernsMode.trim()
  if (form.cgroupParent.trim()) p.cgroupParent = form.cgroupParent.trim()
  if (form.runtime.trim()) p.runtime = form.runtime.trim()
  return p
}

async function submit() {
  const envId = envStore.currentEnvId
  if (!envId) { ElMessage.warning('请先选择环境'); return }
  if (!form.image.trim()) { ElMessage.warning('镜像不能为空'); activeTab.value = 'basic'; return }
  const payload = buildPayload()
  submitting.value = true
  try {
    const id: string = await createContainer(envId, payload) as any
    if (form.startAfterCreate) {
      try { await containerAction(envId, id, 'start') }
      catch { ElMessage.warning('容器已创建，但启动失败，请到详情确认') }
    }
    ElMessage.success('容器创建成功')
    router.push(`/containers/${id}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
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
.unit { font-size: 12px; color: #909399; margin-left: 6px; margin-right: 6px; }
.lbl { font-size: 13px; color: #606266; margin-right: 6px; }
.kv-list { width: 100%; }
.kv-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.kv-eq { color: #909399; }
:deep(.el-tabs__header) { margin-bottom: 12px; }
:deep(.el-divider__text) { font-size: 13px; color: #303133; font-weight: 500; }
</style>
