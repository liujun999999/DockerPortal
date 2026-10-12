import { ref, onUnmounted } from 'vue'

export function useWebSocket(path: string) {
  const ws = ref<WebSocket | null>(null)
  const connected = ref(false)
  const messages = ref<any[]>([])

  function connect(token?: string) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const url = `${protocol}//${host}${path}${token ? `?token=${token}` : ''}`

    const socket = new WebSocket(url)
    ws.value = socket

    socket.onopen = () => {
      connected.value = true
    }

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        messages.value.push(data)
      } catch {
        messages.value.push(event.data)
      }
    }

    socket.onclose = () => {
      connected.value = false
    }

    socket.onerror = () => {
      connected.value = false
    }

    return socket
  }

  function send(data: string | ArrayBuffer) {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(data)
    }
  }

  function close() {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
  }

  onUnmounted(() => {
    close()
  })

  return { ws, connected, messages, connect, send, close }
}
