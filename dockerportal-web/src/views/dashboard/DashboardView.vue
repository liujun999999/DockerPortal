<template>
  <div class="dashboard">
    <h2 class="page-title">总览</h2>

    <!-- 首次使用引导：尚未配置任何 Docker 环境时展示 -->
    <el-card v-if="!hasEnv" shadow="never" class="onboarding-card">
      <div class="ob-hero">
        <el-icon class="ob-logo" size="40"><Ship /></el-icon>
        <div>
          <h3>欢迎使用 DockerPortal 🎉</h3>
          <p class="ob-sub">您还没有配置任何 Docker 环境。按下面步骤开始管理你的容器。</p>
        </div>
      </div>

      <el-steps v-if="isAdmin" :active="0" align-center finish-status="wait" class="ob-steps">
        <el-step title="添加环境" description="连接本地/远程 Docker 或 Swarm" />
        <el-step title="连接验证" description="状态变为“已连接”" />
        <el-step title="管理容器" description="启动、终端、日志、清理" />
        <el-step title="监控告警" description="配置规则自动通知" />
      </el-steps>

      <div class="ob-actions" v-if="isAdmin">
        <el-button type="primary" @click="go('/settings/environments/create')">＋ 添加第一个环境</el-button>
        <el-button @click="go('/containers')">先看看容器</el-button>
      </div>
      <el-alert v-else type="info" :closable="false" show-icon
        title="当前环境列表为空，且您的角色无法添加环境，请联系管理员添加 Docker 环境。" />
    </el-card>

    <template v-if="hasEnv">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/containers', { state: 'running' })">
          <div class="stat-icon" style="background: #67c23a"><el-icon size="28"><Box /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.runningContainers }}</div>
            <div class="stat-label">运行中容器</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/containers', { state: 'exited' })">
          <div class="stat-icon" style="background: #909399"><el-icon size="28"><Box /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.stoppedContainers }}</div>
            <div class="stat-label">已停止容器</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/images')">
          <div class="stat-icon" style="background: #409eff"><el-icon size="28"><Files /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.totalImages }}</div>
            <div class="stat-label">本地镜像</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/networks')">
          <div class="stat-icon" style="background: #e6a23c"><el-icon size="28"><Connection /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.totalNetworks }}</div>
            <div class="stat-label">网络</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/volumes')">
          <div class="stat-icon" style="background: #f56c6c"><el-icon size="28"><Coin /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.totalVolumes }}</div>
            <div class="stat-label">数据卷</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" v-if="data.swarmMode">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/swarm/nodes')">
          <div class="stat-icon" style="background: #9b59b6"><el-icon size="28"><Grid /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.swarmNodes }}</div>
            <div class="stat-label">Swarm 节点</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" v-if="data.swarmMode">
        <el-card shadow="hover" class="stat-card clickable" @click="go('/swarm/services')">
          <div class="stat-icon" style="background: #3498db"><el-icon size="28"><Service /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ data.swarmServices }}</div>
            <div class="stat-label">Swarm 服务</div>
          </div>
        </el-card>
      </el-col>
      <!-- 开源项目的打赏入口，图标直接用收款码缩略图，点开看大图 -->
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card clickable" @click="donateVisible = true">
          <img class="stat-icon donate-icon" :src="donateQr" alt="微信收款码" />
          <div class="stat-info">
            <div class="stat-value">打赏</div>
            <div class="stat-label">如果这个项目帮到了你</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 磁盘占用与资源清理 -->
    <el-card shadow="never" class="df-card" style="margin-top: 20px">
      <template #header>
        <div class="df-header">
          <span class="df-title">磁盘占用（docker system df）</span>
          <div class="header-actions">
            <el-button size="small" :loading="dfLoading" @click="loadDf">刷新</el-button>
            <el-button size="small" @click="doPruneBuilder" v-if="canOperate">清理构建缓存</el-button>
            <el-button size="small" type="warning" @click="doPruneSystem" v-if="isAdmin">清理整个系统</el-button>
          </div>
        </div>
      </template>
      <el-table :data="dfRows" v-loading="dfLoading" size="small" empty-text="暂无数据">
        <el-table-column label="资源类型" prop="type" width="140" />
        <el-table-column label="总数" prop="total" width="90" />
        <el-table-column label="活跃" prop="active" width="90" />
        <el-table-column label="总占用" width="130">
          <template #default="{ row }">
            <span v-if="row.sizeKnown === false" class="size-unknown">未上报</span>
            <span v-else>{{ formatBytesDecimal(row.size) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可回收" min-width="200">
          <template #default="{ row }">
            <span>{{ formatBytesDecimal(row.reclaimable) }}</span>
            <span v-if="row.size > 0" class="reclaim-pct">（{{ Math.floor((row.reclaimable / row.size) * 100) }}%）</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="df-hint">
        数字与 docker system df 逐项对齐（含 1000 进制的 kB/MB/GB）：镜像总占用是镜像层的实际占用（多个镜像共享的层只算一次），
        与构建缓存共用同一份存储，两者不可相加；容器只统计可写层，写入数据卷的内容不计入容器。
        “活跃”指被引用或在运行，“可回收”为对应 prune 命令能释放的空间。
      </div>
    </el-card>

    <el-dialog v-model="donateVisible" title="打赏支持" width="360px" align-center>
      <div class="donate-body">
        <el-image class="donate-qr" :src="donateQr" fit="contain" :preview-src-list="[donateQr]" preview-teleported
          hide-on-click-modal alt="微信收款码" />
        <p>DockerPortal 是开源项目，所有功能免费。如果它帮到了你，可以扫码请作者喝杯咖啡。</p>
        <p class="donate-tip">微信扫一扫 · 点击图片可放大</p>
      </div>
    </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useEnvironmentStore } from '@/stores/environment'
import { usePermission } from '@/composables/usePermission'
import { getDashboard } from '@/api/dashboard'
import { getSystemDf, pruneBuilder, pruneSystem } from '@/api/system'
import { runPrune, runPruneWithAll } from '@/utils/prune'
import { formatBytesDecimal } from '@/utils/format'
import donateQr from '@/assets/donate-wechat.png'

const router = useRouter()
const envStore = useEnvironmentStore()
const { canOperate, isAdmin } = usePermission()
const hasEnv = computed(() => envStore.environments.length > 0)
const donateVisible = ref(false)
const data = ref({
  runningContainers: 0,
  stoppedContainers: 0,
  totalImages: 0,
  totalNetworks: 0,
  totalVolumes: 0,
  swarmMode: false,
  swarmNodes: 0,
  swarmServices: 0,
  swarmTasks: 0,
})

async function loadData() {
  if (!envStore.currentEnvId) return
  try {
    data.value = await getDashboard(envStore.currentEnvId)
  } catch (e) {
    console.error('Failed to load dashboard', e)
  }
}

function go(path: string, query?: Record<string, string>) {
  router.push({ path, query })
}

/* ---------------- 磁盘占用 / 清理 ---------------- */
const dfLoading = ref(false)
const dfRows = ref<{
  type: string; total: number; active: number; size: number; reclaimable: number; sizeKnown: boolean
}[]>([])

/** daemon 用 -1 表示“这项大小拿不到”（比如非 local 驱动的数据卷），不能当成 0 累加 */
function sz(v: unknown): number {
  return typeof v === 'number' && v >= 0 ? v : 0
}

/**
 * /system/df 的四个汇总列（总数/活跃/总占用/可回收）优先用 daemon 自己算好的：
 * Docker 29（API 1.56）起响应里多了 ImageUsage / ContainerUsage / VolumeUsage /
 * BuildCacheUsage 四个汇总块，新版 docker CLI 就是直接打印它们。
 * 老 daemon 只有明细，就得按旧版 CLI 的口径聚合；注意自己算在新版上会算错：
 * 构建缓存条目的 Size 已变成“可回收”，真实占用挪到了汇总块里，用旧算法会把 1.8GB 缓存算成 0。
 */
function buildDfRows(df: any) {
  const images: any[] = df?.Images ?? []
  const containers: any[] = df?.Containers ?? []
  const volumes: any[] = df?.Volumes ?? []
  const cache: any[] = df?.BuildCache ?? []

  // 镜像：LayersSize = 镜像仓库里全部层的实际占用（共享层只算一次），比各镜像 Size 相加更接近真实磁盘占用；
  //      在用镜像独占的那部分（Size - SharedSize）算“用掉了”，剩下的才是可回收；
  //      -1 表示 daemon 没算这一项，按 CLI 的做法整张镜像跳过（归入可回收）。
  const layersSize = sz(df?.LayersSize)
  const imageUsed = images
    .filter((i) => i.Containers > 0 && i.Size >= 0 && i.SharedSize !== -1)
    .reduce((s, i) => s + Math.max(0, i.Size - i.SharedSize), 0)

  // 容器：只统计可写层 SizeRw；daemon 不返回该字段（老版本未算大小，或大小为 0 被 omitempty 省略）
  // 时宁可显示“未上报”，也不把未知当成 0 B
  const isActiveContainer = (c: any) => /running|paused|restarting/.test(String(c?.State ?? ''))
  const sizedContainers = containers.filter((c) => typeof c.SizeRw === 'number')
  const containerSize = sizedContainers.reduce((s, c) => s + sz(c.SizeRw), 0)
  const containerReclaimable = sizedContainers
    .filter((c) => !isActiveContainer(c))
    .reduce((s, c) => s + sz(c.SizeRw), 0)

  // 数据卷：Size / RefCount 都在 UsageData 里；-1 = 驱动不提供大小（非 local 驱动），或引用数未知
  const sizedVolumes = volumes.filter((v) => typeof v.UsageData?.Size === 'number' && v.UsageData.Size >= 0)
  const volumeSize = sizedVolumes.reduce((s, v) => s + v.UsageData.Size, 0)
  const volumeReclaimable = sizedVolumes
    .filter((v) => v.UsageData.RefCount === 0)
    .reduce((s, v) => s + v.UsageData.Size, 0)

  // 构建缓存：Shared 的层与镜像共用，删了也不会释放空间，两列都要排除
  const ownCache = cache.filter((c) => !c.Shared && typeof c.Size === 'number')
  const cacheSize = ownCache.reduce((s, c) => s + sz(c.Size), 0)
  const cacheInUse = ownCache.filter((c) => c.InUse).reduce((s, c) => s + sz(c.Size), 0)

  return [
    dfRow('镜像', df?.ImageUsage, {
      total: images.length,
      active: images.filter((i) => i.Containers > 0).length,
      size: layersSize, reclaimable: Math.max(0, layersSize - imageUsed),
      sizeKnown: images.length === 0 || typeof df?.LayersSize === 'number',
    }),
    dfRow('容器', df?.ContainerUsage, {
      total: containers.length,
      active: containers.filter(isActiveContainer).length,
      size: containerSize, reclaimable: containerReclaimable,
      sizeKnown: containers.length === 0 || sizedContainers.length > 0,
    }),
    dfRow('数据卷', df?.VolumeUsage, {
      total: volumes.length,
      active: volumes.filter((v) => (v.UsageData?.RefCount ?? 0) > 0).length,
      size: volumeSize, reclaimable: volumeReclaimable,
      sizeKnown: volumes.length === 0 || sizedVolumes.length > 0,
    }),
    dfRow('构建缓存', df?.BuildCacheUsage, {
      total: cache.length,
      active: cache.filter((c) => c.InUse).length,
      size: cacheSize, reclaimable: Math.max(0, cacheSize - cacheInUse),
      sizeKnown: cache.length === 0 || ownCache.length > 0,
    }),
  ]
}

/** daemon 给了汇总就用它，没给（老版本）才回落到前端聚合的 {computed} */
function dfRow(
  type: string,
  summary: any,
  computed: { total: number; active: number; size: number; reclaimable: number; sizeKnown: boolean },
) {
  if (summary && typeof summary.TotalCount === 'number') {
    return {
      type,
      total: summary.TotalCount,
      active: sz(summary.ActiveCount),
      size: sz(summary.TotalSize),
      // ContainerUsage 在没有可回收内容时不返回该字段（Go 侧 omitempty）
      reclaimable: sz(summary.Reclaimable),
      sizeKnown: true,
    }
  }
  return { type, ...computed }
}

async function loadDf() {
  if (!envStore.currentEnvId) return
  dfLoading.value = true
  try {
    const df: any = await getSystemDf(envStore.currentEnvId)
    dfRows.value = buildDfRows(df)
  } catch (e) {
    console.error('Failed to load system df', e)
  } finally {
    dfLoading.value = false
  }
}

function doPruneBuilder() {
  runPrune('清理构建缓存（docker builder prune）',
    '将删除所有未被使用的 BuildKit 构建缓存，下次构建需重新生成（仅变慢，不影响镜像本身）。是否继续？',
    () => pruneBuilder(envStore.currentEnvId!), loadDf)
}

function doPruneSystem() {
  runPruneWithAll('清理整个系统（docker system prune）',
    '高危：将按顺序删除所有已停止容器、未使用网络、悬空镜像、构建缓存（默认不删数据卷）。请输入 PRUNE 确认。',
    (all) => pruneSystem(envStore.currentEnvId!, all), () => { loadDf(); loadData() }, 'PRUNE')
}

onMounted(() => { loadData(); loadDf() })
watch(() => envStore.currentEnvId, () => { loadData(); loadDf() })
</script>

<style scoped lang="scss">
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
  color: #303133;
}

.stat-card {
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
  }

  &.clickable {
    cursor: pointer;
    transition: transform 0.2s, box-shadow 0.2s;

    &:hover {
      transform: translateY(-3px);
    }
  }
}

.df-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .df-title {
    font-weight: bold;
  }
}

.reclaim-pct {
  color: #909399;
  font-size: 12px;
}

.size-unknown {
  color: #c0c4cc;
}

.df-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.donate-body {
  text-align: center;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;

  .donate-qr {
    width: 220px;
    height: 226px;
    margin-bottom: 8px;
    cursor: zoom-in;
  }

  .donate-tip {
    color: #909399;
    font-size: 12px;
  }
}

// 卡片里的二维码缩略图：contain + 白底，不裁掉边缘的定位块
.donate-icon {
  object-fit: contain;
  background: #fff;
  box-shadow: inset 0 0 0 1px #ebeef5;
  box-sizing: border-box;
  padding: 3px;
}

.stat-info {
  .stat-value {
    font-size: 28px;
    font-weight: bold;
    color: #303133;
  }
  .stat-label {
    font-size: 13px;
    color: #909399;
    margin-top: 4px;
  }
}

.onboarding-card {
  .ob-hero {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;
    .ob-logo {
      color: #409eff;
      background: rgba(64, 158, 255, 0.1);
      border-radius: 12px;
      padding: 10px;
    }
    h3 { margin: 0 0 4px; font-size: 18px; }
    .ob-sub { margin: 0; color: #606266; font-size: 13px; }
  }
  .ob-steps { margin: 24px 0; }
  .ob-actions {
    display: flex;
    gap: 12px;
    justify-content: center;
    margin-top: 8px;
  }
}
</style>
