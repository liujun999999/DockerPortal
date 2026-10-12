import { onUnmounted, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'

/** 目标可以是普通 DOM 元素，也可以是 el-card 这类组件实例（取其根元素 $el） */
type FsTarget = HTMLElement | { $el?: HTMLElement } | null | undefined

/**
 * 把某个面板切成浏览器全屏，用于日志、终端这类需要大面积阅读的内容。
 *
 * 用原生 Fullscreen API 而不是自己写 position:fixed 铺满：ESC 即可退出、与浏览器
 * 手势一致，也不会被 el-card / el-dialog 的层叠上下文影响 z-index。
 *
 * 全屏态通过绑定 is-fs 类来改样式，而不是写 :fullscreen 伪类 —— Safari 的伪类带
 * webkit 前缀，用类名可以一套样式覆盖所有内核。
 */
export function useFullscreen(target: Ref<FsTarget>) {
  const isFullscreen = ref(false)

  function currentElement(): HTMLElement | null {
    const doc = document as Document & { webkitFullscreenElement?: Element | null }
    return (doc.fullscreenElement || doc.webkitFullscreenElement || null) as HTMLElement | null
  }

  function sync() {
    isFullscreen.value = currentElement() === resolveEl()
  }

  function resolveEl(): HTMLElement | null {
    const t = target.value
    if (!t) return null
    if (t instanceof HTMLElement) return t
    const inst = t as { $el?: HTMLElement }
    return inst.$el instanceof HTMLElement ? inst.$el : null
  }

  async function exit() {
    const doc = document as Document & { webkitExitFullscreen?: () => Promise<void> }
    try {
      const fn = doc.exitFullscreen ? doc.exitFullscreen.bind(doc)
              : doc.webkitExitFullscreen ? doc.webkitExitFullscreen.bind(doc) : null
      if (fn) await fn()
    } catch { /* 退出失败无需处理，最差用户再按一次 ESC */ }
    sync()
  }

  async function toggle() {
    const el = resolveEl()
    if (!el) return
    if (currentElement()) {
      await exit()
      return
    }
    const dom = el as HTMLElement & { webkitRequestFullscreen?: () => Promise<void> }
    const req = dom.requestFullscreen ? dom.requestFullscreen.bind(dom)
            : dom.webkitRequestFullscreen ? dom.webkitRequestFullscreen.bind(dom) : null
    if (!req) {
      ElMessage.warning('当前浏览器不支持全屏，可改用浏览器菜单的缩放功能')
      return
    }
    try {
      await req()
    } catch { /* 不在用户手势内或被页面策略拒绝，保持原样即可 */ }
    sync()
  }

  document.addEventListener('fullscreenchange', sync)
  document.addEventListener('webkitfullscreenchange', sync)
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', sync)
    document.removeEventListener('webkitfullscreenchange', sync)
  })

  return { isFullscreen, toggle, exit }
}
