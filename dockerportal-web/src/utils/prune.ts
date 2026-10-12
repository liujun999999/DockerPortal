import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatBytes } from './format'
import { requireTypedConfirm } from './danger'

/** 统一 toast：删除数量 + 释放空间（system prune 有分类明细时附带） */
function reportPrune(res: any) {
  const parts = [`删除 ${res?.deletedCount ?? 0} 项`, `释放空间 ${formatBytes(res?.spaceReclaimed ?? 0)}`]
  const details = res?.details
  if (details) {
    parts.push(`（容器 ${details.containers ?? 0}、网络 ${details.networks ?? 0}、镜像 ${details.images ?? 0}、构建缓存 ${details.builder ?? 0}）`)
  }
  ElMessage.success(`清理完成：${parts.join('，')}`)
}

/**
 * 标准清理交互：二次确认 → 执行 prune → toast 展示结果
 * @param requireText 传入则升级为 L2 打字确认（适用于卷/网络等不可逆批量删除）
 */
export async function runPrune(
  title: string,
  description: string,
  action: () => Promise<any>,
  onDone?: () => void,
  requireText?: string,
) {
  try {
    if (requireText) {
      await requireTypedConfirm({ title, message: description, requireText, confirmText: '执行清理' })
    } else {
      await ElMessageBox.confirm(description, title, {
        type: 'warning',
        confirmButtonText: '执行清理',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      })
    }
  } catch {
    return // 用户取消
  }
  try {
    const res = await action()
    reportPrune(res)
    onDone?.()
  } catch {
    // 错误 toast 已由 request 拦截器统一处理
  }
}

/**
 * 带「全部 / -a」勾选项的清理：确认框内提供复选框切换清理强度
 */
export async function runPruneWithAll(
  title: string,
  description: string,
  action: (all: boolean) => Promise<any>,
  onDone?: () => void,
  requireTypedText?: string,
) {
  const holder = { all: false }
  try {
    if (requireTypedText) {
      // 最危险（system prune）：先打字确认再选清理强度
      await requireTypedConfirm({ title, message: description, requireText: requireTypedText, confirmText: '继续' })
    }
    await ElMessageBox.confirm(
      h('div', { style: 'line-height:1.6' }, [
        h('div', description),
        h(
          'label',
          { style: 'display:flex;align-items:flex-start;gap:6px;margin-top:12px;cursor:pointer;font-weight:normal' },
          [
            h('input', {
              type: 'checkbox',
              style: 'margin-top:3px',
              onChange: (e: Event) => {
                holder.all = (e.target as HTMLInputElement).checked
              },
            }),
            h('span', '同时删除所有未被容器使用的镜像（等价 -a，被删除的镜像下次使用需重新拉取）'),
          ],
        ),
      ]),
      title,
      {
        type: 'warning',
        confirmButtonText: '执行清理',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return // 用户取消
  }
  try {
    const res = await action(holder.all)
    reportPrune(res)
    onDone?.()
  } catch {
    // 错误 toast 已由 request 拦截器统一处理
  }
}

/**
 * 二选一清理：确认按钮与「取消」位置分别对应两种清理强度（prune / prune -a），关闭或 ESC 放弃
 */
export async function runPruneChoice(
  title: string,
  message: string,
  action: (all: boolean) => Promise<any>,
  onDone?: () => void,
  labels: { left: string; right: string } = { left: '仅悬空', right: '全部未使用' },
) {
  let all = false
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: labels.right,
      cancelButtonText: labels.left,
      distinguishCancelAndClose: true,
    })
    all = true
  } catch (e) {
    if (e !== 'cancel') return // 关闭 / ESC = 放弃操作
    all = false
  }
  try {
    const res = await action(all)
    reportPrune(res)
    onDone?.()
  } catch {
    // 错误 toast 已由 request 拦截器统一处理
  }
}
