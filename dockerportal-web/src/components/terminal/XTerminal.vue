<template>
  <div ref="terminalContainer" class="x-terminal"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'

const props = withDefaults(defineProps<{
  wsUrl?: string
  fontSize?: number
  theme?: 'dark' | 'light'
}>(), {
  fontSize: 14,
  theme: 'dark',
})

const emit = defineEmits<{
  (e: 'data', data: string): void
  (e: 'ready'): void
  (e: 'open'): void
  (e: 'close'): void
}>()

const terminalContainer = ref<HTMLElement | null>(null)
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let resizeObserver: ResizeObserver | null = null

function initTerminal() {
  if (!terminalContainer.value) return

  terminal = new Terminal({
    fontSize: props.fontSize,
    fontFamily: "'Cascadia Code', 'Fira Code', 'Consolas', monospace",
    theme: props.theme === 'light' ? {
      background: '#ffffff',
      foreground: '#333333',
      cursor: '#333333',
      selectionBackground: '#add6ff',
    } : {
      background: '#1e1e1e',
      foreground: '#d4d4d4',
      cursor: '#d4d4d4',
      selectionBackground: '#264f78',
    },
    cursorBlink: true,
    scrollback: 10000,
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(terminalContainer.value)
  fitAddon.fit()
  // 字体/布局稳定前 charMeasure 可能为 0 导致 fit() 静默失效，延迟重试几次兜底
  scheduleFit()

  terminal.onData((data) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      // 按键输入用二进制帧发送，与文本帧的控制消息区分
      ws.send(new TextEncoder().encode(data))
    }
    emit('data', data)
  })

  resizeObserver = new ResizeObserver(() => {
    try { fitAddon?.fit() } catch { /* ignore */ }
  })
  resizeObserver.observe(terminalContainer.value)

  // 终端尺寸变化时同步到后端（docker exec TTY / SSH channel）
  terminal.onResize(({ cols, rows }) => {
    sendResize(cols, rows)
  })

  emit('ready')
}

function scheduleFit() {
  requestAnimationFrame(() => { try { fitAddon?.fit() } catch { /* ignore */ } })
  setTimeout(() => { try { fitAddon?.fit() } catch { /* ignore */ } }, 100)
  setTimeout(() => { try { fitAddon?.fit() } catch { /* ignore */ } }, 350)
}

function sendResize(cols: number, rows: number) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    // 控制消息用文本帧，后端 handleTextMessage 专处理，不会污染 stdin
    ws.send(JSON.stringify({ type: 'resize', cols, rows }))
  }
}

function connectWebSocket(url: string) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const fullUrl = url.startsWith('ws') ? url : `${protocol}//${host}${url}`

  ws = new WebSocket(fullUrl)
  ws.binaryType = 'arraybuffer'

  ws.onopen = () => {
    terminal?.writeln('\r\n\x1b[32mConnected.\x1b[0m\r\n')
    // 连接建立时布局已稳定，再 fit 一次确保尺寸铺满容器，然后同步到后端
    try { fitAddon?.fit() } catch { /* ignore */ }
    if (terminal) sendResize(terminal.cols, terminal.rows)
    emit('open')
  }

  ws.onmessage = (event) => {
    if (event.data instanceof ArrayBuffer) {
      terminal?.write(new Uint8Array(event.data))
    } else {
      terminal?.write(event.data)
    }
  }

  ws.onclose = () => {
    terminal?.writeln('\r\n\x1b[31mDisconnected.\x1b[0m\r\n')
    emit('close')
  }

  ws.onerror = () => {
    terminal?.writeln('\r\n\x1b[31mConnection error.\x1b[0m\r\n')
  }
}

function write(data: string | Uint8Array) {
  terminal?.write(data as any)
}

function fit() {
  try { fitAddon?.fit() } catch { /* ignore */ }
}

watch(() => props.wsUrl, (url) => {
  if (url) connectWebSocket(url)
})

onMounted(() => {
  initTerminal()
  if (props.wsUrl) connectWebSocket(props.wsUrl)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  ws?.close()
  terminal?.dispose()
})

defineExpose({ write, fit, terminal })
</script>

<style scoped>
.x-terminal {
  width: 100%;
  height: 100%;
  min-height: 300px;
}
/* 注意：不要在终端元素（.xterm）上加 padding，会让滚动视口与内容错位、fit 测量不准 */
</style>
