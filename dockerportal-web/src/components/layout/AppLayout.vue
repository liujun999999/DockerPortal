<template>
  <el-container class="app-layout">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon size="24"><Ship /></el-icon>
        <span v-show="!isCollapse" class="logo-text">DockerPortal</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        background-color="#1d1e1f"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>总览</template>
        </el-menu-item>
        <el-menu-item index="/containers">
          <el-icon><Box /></el-icon>
          <template #title>容器</template>
        </el-menu-item>
        <el-menu-item index="/images">
          <el-icon><Files /></el-icon>
          <template #title>镜像</template>
        </el-menu-item>
        <el-menu-item index="/networks">
          <el-icon><Connection /></el-icon>
          <template #title>网络</template>
        </el-menu-item>
        <el-menu-item index="/volumes">
          <el-icon><Coin /></el-icon>
          <template #title>数据卷</template>
        </el-menu-item>
        <el-sub-menu index="/swarm">
          <template #title>
            <el-icon><Grid /></el-icon>
            <span>Swarm</span>
          </template>
          <el-menu-item index="/swarm/overview">集群总览</el-menu-item>
          <el-menu-item index="/swarm/nodes">节点</el-menu-item>
          <el-menu-item index="/swarm/services">服务</el-menu-item>
          <el-menu-item index="/swarm/stacks">Stacks</el-menu-item>
          <el-menu-item index="/swarm/secrets">Secrets</el-menu-item>
          <el-menu-item index="/swarm/configs">Configs</el-menu-item>
          <el-menu-item index="/swarm/tasks">任务</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/ssh" v-if="authStore.isAdmin">
          <el-icon><Platform /></el-icon>
          <template #title>SSH 终端</template>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><DataLine /></el-icon>
          <template #title>监控</template>
        </el-menu-item>
        <el-sub-menu index="/alerts">
          <template #title>
            <el-icon><Bell /></el-icon>
            <span>告警</span>
          </template>
          <el-menu-item index="/alerts/rules" v-if="authStore.isAdmin">告警规则</el-menu-item>
          <el-menu-item index="/alerts/history">告警历史</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="/settings" v-if="authStore.isAdmin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>设置</span>
          </template>
          <el-menu-item index="/settings/environments">环境管理</el-menu-item>
          <el-menu-item index="/settings/registries">镜像仓库</el-menu-item>
          <el-menu-item index="/settings/users">用户管理</el-menu-item>
          <el-menu-item index="/settings/audit-logs">审计日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="sidebar-footer" v-show="!isCollapse">
        <span class="version">DockerPortal {{ appVersion }}</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-select
            v-model="envStore.currentEnvId"
            placeholder="选择环境"
            size="small"
            style="width: 200px"
            @change="envStore.setCurrentEnv"
          >
            <el-option
              v-for="env in envStore.environments"
              :key="env.id"
              :label="env.name"
              :value="env.id"
            >
              <span>{{ env.name }}</span>
              <el-tag :type="env.status === 'CONNECTED' ? 'success' : 'danger'" size="small" style="margin-left: 8px">
                {{ env.status === 'CONNECTED' ? '已连接' : '未连接' }}
              </el-tag>
            </el-option>
          </el-select>
        </div>
        <div class="header-right">
          <el-tag :type="roleTagType" size="small" effect="plain" class="role-badge">{{ roleLabel }}</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ authStore.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="changePwd">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>

    <ChangePasswordDialog
      v-model="showChangePwd"
      :force-mode="authStore.mustChangePassword"
    />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useEnvironmentStore } from '@/stores/environment'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const envStore = useEnvironmentStore()
const isCollapse = ref(false)
const showChangePwd = ref(false)
const appVersion = 'v1.0.0'

const activeMenu = computed(() => route.path)

const roleLabel = computed(() =>
  authStore.role === 'ADMIN' ? '管理员' : authStore.role === 'OPERATOR' ? '运维' : '只读'
)
const roleTagType = computed(() =>
  authStore.role === 'ADMIN' ? 'danger' : authStore.role === 'OPERATOR' ? 'warning' : 'info'
)

// 待强制改密：路由守卫会重定向到安装向导；这里兜底（如会话中途被置标）
watch(
  () => authStore.mustChangePassword,
  (v) => { if (v) router.push('/setup') },
  { immediate: true }
)

onMounted(() => {
  authStore.refreshMe()
  envStore.loadEnvironments()
})

function handleCommand(command: string) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (command === 'changePwd') {
    showChangePwd.value = true
  }
}
</script>

<style scoped lang="scss">
.app-layout {
  height: 100vh;
}

.sidebar {
  background-color: #1d1e1f;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .el-menu {
    flex: 1;
    overflow-y: auto;
  }

  .sidebar-footer {
    padding: 10px 16px;
    border-top: 1px solid #333;
    .version {
      color: #6b6f76;
      font-size: 12px;
    }
  }

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #fff;
    font-size: 18px;
    font-weight: bold;
    border-bottom: 1px solid #333;
  }

  .el-menu {
    border-right: none;
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .collapse-btn {
    font-size: 20px;
    cursor: pointer;
    &:hover { color: #409eff; }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;

    .role-badge {
      margin-right: 4px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 4px;
      cursor: pointer;
      font-size: 14px;
    }
  }
}

.main-content {
  background: #f5f7fa;
  overflow-y: auto;
}
</style>
