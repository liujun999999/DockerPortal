import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 前端角色权限辅助：与后端 @PreAuthorize 分级保持一致。
 *  - VIEWER：只读
 *  - OPERATOR：容器级日常操作（创建/启停/删除容器、拉取/打标签镜像、文件写、服务/Stack 部署）
 *  - ADMIN：基础设施与破坏性清理（卷删除、volume/system prune、Swarm 变更、SSH、用户/环境/仓库/告警配置）
 */
export function usePermission() {
  const auth = useAuthStore()
  return {
    role: computed(() => auth.role),
    isAdmin: computed(() => auth.isAdmin),
    canOperate: computed(() => auth.isOperator),
    isViewer: computed(() => !auth.isOperator),
  }
}
