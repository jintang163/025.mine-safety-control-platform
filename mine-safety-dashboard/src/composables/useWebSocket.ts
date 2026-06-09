import { ref, onUnmounted } from 'vue'

export function useWebSocket(url = 'ws://localhost:8081/ws') {
  const sensorData = ref<Map<string, any>>(new Map())
  const alertData = ref<any[]>([])
  const connected = ref(false)

  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function connect() {
    if (ws && ws.readyState === WebSocket.OPEN) return

    ws = new WebSocket(url)

    ws.onopen = () => {
      connected.value = true
      console.log('[WS] Connected to', url)
    }

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'topic') {
          const inner = typeof msg.payload === 'string' ? JSON.parse(msg.payload) : msg.payload
          if (inner.dataType === 'SENSOR_DATA' && inner.payload) {
            const sensor = inner.payload
            sensorData.value.set(sensor.sensorId, sensor)
            sensorData.value = new Map(sensorData.value)
          } else if (inner.dataType === 'ALERT' && inner.payload) {
            alertData.value = [inner.payload, ...alertData.value].slice(0, 100)
          }
        } else if (msg.type === 'data') {
          if (msg.dataType === 'SENSOR_DATA' && msg.payload) {
            sensorData.value.set(msg.payload.sensorId, msg.payload)
            sensorData.value = new Map(sensorData.value)
          } else if (msg.dataType === 'ALERT' && msg.payload) {
            alertData.value = [msg.payload, ...alertData.value].slice(0, 100)
          }
        }
      } catch (e) {
        console.error('[WS] Parse error', e)
      }
    }

    ws.onclose = () => {
      connected.value = false
      console.log('[WS] Disconnected, reconnecting in 5s...')
      reconnectTimer = setTimeout(connect, 5000)
    }

    ws.onerror = () => {
      connected.value = false
      ws?.close()
    }
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    ws?.close()
    ws = null
    connected.value = false
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    sensorData,
    alertData,
    connected,
    connect,
    disconnect
  }
}
