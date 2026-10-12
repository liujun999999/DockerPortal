import { ElMessageBox } from 'element-plus'

/**
 * 危险操作分级确认工具
 *
 * 分级策略：
 *  - L0/L1（可逆/常规）：普通二次确认 confirmDeletable
 *  - L2（不可逆/批量破坏）：必须手动输入指定文本才能执行 requireTypedConfirm
 */

/** 普通危险操作二次确认（删除容器等）。用户取消时抛错，调用方 catch 即可。 */
export function confirmDeletable(
  title: string,
  message: string,
  confirmText = '删除',
): Promise<void> {
  return ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: confirmText,
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).then(() => undefined)
}

/**
 * 破坏性操作打字确认：用户必须精确输入 requireText 才能继续。
 * 返回 Promise，用户取消/输入不符时 reject。
 */
export function requireTypedConfirm(opts: {
  title: string
  message: string
  requireText: string
  confirmText?: string
}): Promise<void> {
  return ElMessageBox.prompt(opts.message, opts.title, {
    inputPlaceholder: `请输入 "${opts.requireText}" 以确认`,
    inputValidator: (v: string) =>
      (v || '').trim() === opts.requireText ? true : `请精确输入：${opts.requireText}`,
    confirmButtonText: opts.confirmText ?? '确认执行',
    cancelButtonText: '取消',
    type: 'error',
    confirmButtonClass: 'el-button--danger',
  }).then(() => undefined)
}
