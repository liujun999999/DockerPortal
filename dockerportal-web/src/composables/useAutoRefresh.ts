import { ref, watch, onUnmounted } from 'vue'

/**
 * 日志「自动刷新」轮询器：开关 + 间隔可调，开启后按间隔静默执行 run。
 *
 * - 上一轮没跑完不会叠加下一轮：日志接口偶尔会慢（聚合多副本 / 跨节点取），
 *   固定 setInterval 直接调 run 会在慢请求时把请求堆起来，这里用 running 标志挡掉。
 * - 组件卸载自动停，避免离开页面后还在后台打接口。
 * - run 应传入「静默刷新」版本（不要触发整块 loading 遮罩），否则每几秒闪一次。
 *
 * 调用方若只想在某个 tab 激活时轮询，在 run 里自行判断即可（到点空转、不发请求）。
 */
export function useAutoRefresh(
  run: () => Promise<void> | void,
  options: { defaultInterval?: number } = {},
) {
  const enabled = ref(false)
  const interval = ref(options.defaultInterval ?? 5000)
  let timer: ReturnType<typeof setInterval> | null = null
  let running = false

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  async function tick() {
    if (running) return
    running = true
    try {
      await run()
    } finally {
      running = false
    }
  }

  function start() {
    stop()
    timer = setInterval(tick, interval.value)
  }

  watch(enabled, (on) => (on ? start() : stop()))
  // 开着的时候改间隔，要按新间隔重新计时，否则要等到下一轮旧周期才生效
  watch(interval, () => {
    if (enabled.value) start()
  })
  onUnmounted(stop)

  return { enabled, interval, start, stop }
}
