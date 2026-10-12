/**
 * 常量定义
 */

/** 容器状态 */
export const CONTAINER_STATES = {
  running: { label: '运行中', type: 'success' },
  exited: { label: '已停止', type: 'danger' },
  paused: { label: '已暂停', type: 'warning' },
  created: { label: '已创建', type: 'info' },
  restarting: { label: '重启中', type: '' },
  dead: { label: '已死亡', type: 'danger' },
} as const

/** 节点角色 */
export const NODE_ROLES = {
  manager: { label: 'Manager', type: 'warning' },
  worker: { label: 'Worker', type: '' },
} as const

/** 节点可用性 */
export const NODE_AVAILABILITY = {
  active: { label: 'Active', type: 'success' },
  pause: { label: 'Pause', type: 'warning' },
  drain: { label: 'Drain', type: 'danger' },
} as const

/** 节点状态 */
export const NODE_STATUS = {
  ready: { label: 'Ready', type: 'success' },
  down: { label: 'Down', type: 'danger' },
  disconnected: { label: 'Disconnected', type: 'warning' },
  unknown: { label: 'Unknown', type: 'info' },
} as const

/** 任务状态 */
export const TASK_STATES = {
  running: { label: '运行中', type: 'success' },
  complete: { label: '已完成', type: '' },
  failed: { label: '失败', type: 'danger' },
  shutdown: { label: '已关闭', type: 'info' },
  accepted: { label: '已接受', type: '' },
  preparing: { label: '准备中', type: 'warning' },
  starting: { label: '启动中', type: 'warning' },
  new: { label: '新建', type: 'info' },
} as const

/** 告警级别 */
export const ALERT_SEVERITY = {
  INFO: { label: '信息', type: 'info' },
  WARNING: { label: '警告', type: 'warning' },
  CRITICAL: { label: '严重', type: 'danger' },
} as const

/** 告警状态 */
export const ALERT_STATUS = {
  FIRING: { label: '触发中', type: 'danger' },
  RESOLVED: { label: '已解决', type: 'success' },
} as const

/** 用户角色 */
export const USER_ROLES = {
  ADMIN: { label: '管理员' },
  OPERATOR: { label: '操作员' },
  VIEWER: { label: '观察者' },
} as const

/** WebSocket 消息类型 */
export const WS_MESSAGE_TYPES = {
  LOG: 'LOG',
  STATS: 'STATS',
  CONSOLE: 'CONSOLE',
  SSH: 'SSH',
  ALERT: 'ALERT',
} as const
