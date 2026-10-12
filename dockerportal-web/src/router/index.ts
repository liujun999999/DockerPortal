import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useEnvironmentStore } from '@/stores/environment'
import { useSetupStore } from '@/stores/setup'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true },
    },
    {
      // 首访安装向导：独立全屏页，不走 AppLayout。未初始化时它是匿名可进的
      path: '/setup',
      name: 'Setup',
      component: () => import('@/views/setup/SetupWizard.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '总览', icon: 'Monitor' },
        },
        {
          path: 'containers',
          name: 'Containers',
          component: () => import('@/views/container/ContainerList.vue'),
          meta: { title: '容器', icon: 'Box' },
        },
        {
          path: 'containers/create',
          name: 'ContainerCreate',
          component: () => import('@/views/container/ContainerForm.vue'),
          meta: { title: '创建容器', hidden: true },
        },
        {
          path: 'containers/:id',
          name: 'ContainerDetail',
          component: () => import('@/views/container/ContainerDetail.vue'),
          meta: { title: '容器详情', hidden: true },
        },
        {
          path: 'containers/:id/logs',
          name: 'ContainerLog',
          component: () => import('@/views/container/ContainerLog.vue'),
          meta: { title: '容器日志', hidden: true },
        },
        {
          path: 'containers/:id/console',
          name: 'ContainerConsole',
          component: () => import('@/views/container/ContainerConsole.vue'),
          meta: { title: '容器终端', hidden: true },
        },
        {
          path: 'images',
          name: 'Images',
          component: () => import('@/views/image/ImageList.vue'),
          meta: { title: '镜像', icon: 'Files' },
        },
        {
          path: 'images/build',
          name: 'ImageBuild',
          component: () => import('@/views/image/ImageBuild.vue'),
          meta: { title: '构建镜像', hidden: true },
        },
        {
          path: 'networks',
          name: 'Networks',
          component: () => import('@/views/network/NetworkList.vue'),
          meta: { title: '网络', icon: 'Connection' },
        },
        {
          path: 'volumes',
          name: 'Volumes',
          component: () => import('@/views/volume/VolumeList.vue'),
          meta: { title: '数据卷', icon: 'Coin' },
        },
        {
          path: 'swarm',
          name: 'Swarm',
          redirect: '/swarm/overview',
          meta: { title: 'Swarm', icon: 'Grid' },
          children: [
            {
              path: 'overview',
              name: 'SwarmOverview',
              component: () => import('@/views/swarm/SwarmOverview.vue'),
              meta: { title: '集群总览' },
            },
            {
              path: 'nodes',
              name: 'Nodes',
              component: () => import('@/views/swarm/NodeList.vue'),
              meta: { title: '节点' },
            },
            {
              path: 'nodes/:id',
              name: 'NodeDetail',
              component: () => import('@/views/swarm/NodeDetail.vue'),
              meta: { title: '节点详情', hidden: true },
            },
            {
              path: 'services',
              name: 'Services',
              component: () => import('@/views/swarm/ServiceList.vue'),
              meta: { title: '服务' },
            },
            {
              path: 'services/create',
              name: 'ServiceCreate',
              component: () => import('@/views/swarm/ServiceForm.vue'),
              meta: { title: '创建服务', hidden: true },
            },
            {
              path: 'services/:id',
              name: 'ServiceDetail',
              component: () => import('@/views/swarm/ServiceDetail.vue'),
              meta: { title: '服务详情', hidden: true },
            },
            {
              path: 'services/:id/edit',
              name: 'ServiceEdit',
              component: () => import('@/views/swarm/ServiceForm.vue'),
              meta: { title: '编辑服务', hidden: true },
            },
            {
              path: 'stacks',
              name: 'Stacks',
              component: () => import('@/views/swarm/StackList.vue'),
              meta: { title: 'Stacks' },
            },
            {
              path: 'secrets',
              name: 'Secrets',
              component: () => import('@/views/swarm/SecretList.vue'),
              meta: { title: 'Secrets' },
            },
            {
              path: 'configs',
              name: 'Configs',
              component: () => import('@/views/swarm/ConfigList.vue'),
              meta: { title: 'Configs' },
            },
            {
              path: 'tasks',
              name: 'Tasks',
              component: () => import('@/views/swarm/TaskList.vue'),
              meta: { title: '任务列表' },
            },
          ],
        },
        {
          path: 'ssh',
          name: 'SshTerminal',
          component: () => import('@/views/ssh/SshTerminal.vue'),
          meta: { title: 'SSH 终端', icon: 'Terminal', roles: ['ADMIN'] },
        },
        {
          path: 'monitor',
          name: 'Monitor',
          component: () => import('@/views/monitor/MonitorDashboard.vue'),
          meta: { title: '监控', icon: 'DataLine' },
        },
        {
          path: 'alerts',
          name: 'Alerts',
          redirect: '/alerts/rules',
          meta: { title: '告警', icon: 'Bell' },
          children: [
            {
              path: 'rules',
              name: 'AlertRules',
              component: () => import('@/views/alert/AlertRuleList.vue'),
              meta: { title: '告警规则', roles: ['ADMIN'] },
            },
            {
              path: 'rules/create',
              name: 'AlertRuleForm',
              component: () => import('@/views/alert/AlertRuleForm.vue'),
              meta: { title: '规则配置', hidden: true, roles: ['ADMIN'] },
            },
            {
              path: 'history',
              name: 'AlertHistory',
              component: () => import('@/views/alert/AlertHistory.vue'),
              meta: { title: '告警历史' },
            },
          ],
        },
        {
          path: 'settings',
          name: 'Settings',
          redirect: '/settings/environments',
          meta: { title: '设置', icon: 'Setting', roles: ['ADMIN'] },
          children: [
            {
              path: 'environments',
              name: 'Environments',
              component: () => import('@/views/environment/EnvironmentList.vue'),
              meta: { title: '环境管理' },
            },
            {
              path: 'environments/create',
              name: 'EnvironmentCreate',
              component: () => import('@/views/environment/EnvironmentForm.vue'),
              meta: { title: '添加环境', hidden: true },
            },
            {
              path: 'environments/:id/edit',
              name: 'EnvironmentEdit',
              component: () => import('@/views/environment/EnvironmentForm.vue'),
              meta: { title: '编辑环境', hidden: true },
            },
            {
              path: 'registries',
              name: 'Registries',
              component: () => import('@/views/settings/RegistryList.vue'),
              meta: { title: '镜像仓库' },
            },
            {
              path: 'users',
              name: 'Users',
              component: () => import('@/views/user/UserList.vue'),
              meta: { title: '用户管理' },
            },
            {
              path: 'general',
              name: 'GeneralSettings',
              component: () => import('@/views/settings/SettingsView.vue'),
              meta: { title: '系统设置' },
            },
            {
              path: 'audit-logs',
              name: 'AuditLogs',
              component: () => import('@/views/settings/AuditLogList.vue'),
              meta: { title: '审计日志' },
            },
          ],
        },
      ],
    },
  ],
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const setupStore = useSetupStore()
  await setupStore.ensureLoaded()
  if (!to.meta.public && !authStore.isAuthenticated) {
    // 实例连管理员账号都还没有，登录页上那个表单是死路，直接送进安装向导
    if (setupStore.needsSetup) {
      next('/setup')
      return
    }
    next('/login')
    return
  }
  // 待强制改密：一律先进安装向导（改密前其它业务 API 会被后端 40310 拦截）
  if (authStore.isAuthenticated && authStore.mustChangePassword && to.name !== 'Setup') {
    next('/setup')
    return
  }
  // 已完成全部设置的人不需要向导
  if (to.name === 'Setup' && authStore.isAuthenticated && !authStore.mustChangePassword) {
    const envStore = useEnvironmentStore()
    if (envStore.environments.length > 0) {
      next('/dashboard')
      return
    }
  }
  // 角色级路由拦截：菜单已隐藏，这里防止直连 URL
  const roles = to.meta.roles as string[] | undefined
  if (roles && roles.length && !roles.includes(authStore.role)) {
    next('/dashboard')
    return
  }
  next()
})

export default router
