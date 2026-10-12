import { ref, onMounted, onUnmounted, type Ref } from 'vue'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'

export function useTerminal(
  containerRef: Ref<HTMLElement | null>,
  options?: { fontSize?: number; theme?: string }
) {
  const terminal = ref<Terminal | null>(null)
  const fitAddon = ref<FitAddon | null>(null)
  const ready = ref(false)

  function init() {
    if (!containerRef.value) return

    const term = new Terminal({
      fontSize: options?.fontSize || 14,
      fontFamily: "'Cascadia Code', 'Fira Code', 'Consolas', monospace",
      theme: options?.theme === 'light' ? {
        background: '#ffffff',
        foreground: '#333333',
        cursor: '#333333',
      } : {
        background: '#1e1e1e',
        foreground: '#d4d4d4',
        cursor: '#d4d4d4',
      },
      cursorBlink: true,
      scrollback: 5000,
    })

    const fit = new FitAddon()
    term.loadAddon(fit)
    term.open(containerRef.value)
    fit.fit()

    terminal.value = term
    fitAddon.value = fit
    ready.value = true

    // Handle resize
    const resizeObserver = new ResizeObserver(() => {
      try { fit.fit() } catch { /* ignore */ }
    })
    resizeObserver.observe(containerRef.value)

    return { term, fit }
  }

  function write(data: string | Uint8Array) {
    terminal.value?.write(data as any)
  }

  function clear() {
    terminal.value?.clear()
  }

  function dispose() {
    terminal.value?.dispose()
    terminal.value = null
    fitAddon.value = null
    ready.value = false
  }

  function fit() {
    try { fitAddon.value?.fit() } catch { /* ignore */ }
  }

  onUnmounted(() => {
    dispose()
  })

  return { terminal, fitAddon, ready, init, write, clear, dispose, fit }
}
