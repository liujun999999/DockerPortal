/**
 * 格式化工具函数
 */

/** 格式化字节大小 */
export function formatBytes(bytes: number, decimals = 2): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i]
}

/**
 * 按 1000 进制格式化字节，对齐 docker CLI 的 units.HumanSize（kB/MB/GB）。
 * 磁盘占用（docker system df）那一块用它，否则同一份数据会比 CLI 少一圈，
 * 容易让人误判统计有误；站内其他位置仍用 1024 进制的 formatBytes。
 */
export function formatBytesDecimal(bytes: number, decimals = 2): string {
  if (bytes === 0) return '0 B'
  const sizes = ['B', 'kB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.max(0, Math.min(Math.floor(Math.log(Math.abs(bytes)) / Math.log(1000)), sizes.length - 1))
  return parseFloat((bytes / Math.pow(1000, i)).toFixed(decimals)) + ' ' + sizes[i]
}

/** 格式化持续时间 */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}小时 ${Math.floor((seconds % 3600) / 60)}分钟`
  return `${Math.floor(seconds / 86400)}天 ${Math.floor((seconds % 86400) / 3600)}小时`
}

/** 格式化日期时间 */
export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

/** 格式化相对时间 */
export function formatRelativeTime(dateStr: string): string {
  const now = Date.now()
  const date = new Date(dateStr).getTime()
  const diff = Math.floor((now - date) / 1000)

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return formatDateTime(dateStr)
}

/** 格式化百分比 */
export function formatPercent(value: number, decimals = 1): string {
  return `${value.toFixed(decimals)}%`
}

/** 截断字符串 */
export function truncate(str: string, maxLen = 12): string {
  if (!str) return ''
  return str.length > maxLen ? str.substring(0, maxLen) + '...' : str
}
