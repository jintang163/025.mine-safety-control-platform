<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  sensors: any[]
  personnel: any[]
  heatmapData: any[]
  wsSensorData: Map<string, any>
}>()

const emit = defineEmits<{
  (e: 'select-tunnel', tunnel: string): void
}>()

const canvasRef = ref<HTMLCanvasElement>()
let animFrame = 0
let ctx: CanvasRenderingContext2D | null = null

interface TunnelDef {
  name: string
  x1: number; y1: number
  x2: number; y2: number
  width: number
  color: string
}

const tunnelDefs: TunnelDef[] = [
  { name: '运输巷', x1: 60, y1: 280, x2: 320, y2: 280, width: 50, color: 'rgba(0,212,255,0.15)' },
  { name: '回风巷', x1: 60, y1: 120, x2: 320, y2: 120, width: 50, color: 'rgba(0,212,255,0.15)' },
  { name: '中央变电所', x1: 320, y1: 120, x2: 320, y2: 280, width: 50, color: 'rgba(168,85,247,0.15)' },
  { name: '掘进工作面', x1: 320, y1: 120, x2: 620, y2: 120, width: 50, color: 'rgba(255,184,0,0.15)' },
  { name: '采煤工作面', x1: 320, y1: 280, x2: 620, y2: 280, width: 50, color: 'rgba(255,71,87,0.15)' }
]

const tunnelCenterMap: Record<string, { cx: number; cy: number }> = {}
tunnelDefs.forEach(t => {
  tunnelCenterMap[t.name] = { cx: (t.x1 + t.x2) / 2, cy: (t.y1 + t.y2) / 2 }
})

const defaultTunnelCenters = [
  { tunnel: '回风巷', cx: 180, cy: 120 },
  { tunnel: '运输巷', cx: 180, cy: 280 },
  { tunnel: '中央变电所', cx: 320, cy: 200 },
  { tunnel: '掘进工作面', cx: 470, cy: 120 },
  { tunnel: '采煤工作面', cx: 470, cy: 280 }
]

function getSensorPosition(sensor: any, index: number): { x: number; y: number } {
  if (sensor.coordinatesX != null && sensor.coordinatesY != null) {
    return { x: sensor.coordinatesX, y: sensor.coordinatesY }
  }
  const tunnel = sensor.tunnel || sensor.location || ''
  const center = tunnelCenterMap[tunnel]
  if (center) {
    const offsetX = ((index % 3) - 1) * 60
    const offsetY = ((Math.floor(index / 3) % 2) - 0.5) * 20
    return { x: center.cx + offsetX, y: center.cy + offsetY }
  }
  return { x: 100 + index * 70, y: 200 }
}

function getSensorLevel(sensor: any): 'normal' | 'warning' | 'danger' {
  const status = sensor.status
  if (status === 2 || status === 'danger') return 'danger'
  if (status === 1 || status === 'warning') return 'warning'
  return 'normal'
}

function getSensorLabel(sensor: any): string {
  const type = sensor.sensorType || ''
  if (type.includes('GAS') || type.includes('瓦斯') || type.includes('CH')) return 'CH₄'
  if (type.includes('DUST') || type.includes('粉尘')) return '粉尘'
  if (type.includes('CO')) return 'CO'
  if (type.includes('TEMP') || type.includes('温度')) return '温度'
  if (type.includes('WIND') || type.includes('风速')) return '风速'
  return type || '传感器'
}

function getSensorUnit(sensor: any): string {
  const type = sensor.sensorType || ''
  if (type.includes('GAS') || type.includes('瓦斯') || type.includes('CH')) return '%'
  if (type.includes('DUST') || type.includes('粉尘')) return 'mg/m³'
  if (type.includes('CO')) return 'ppm'
  if (type.includes('TEMP') || type.includes('温度')) return '°C'
  if (type.includes('WIND') || type.includes('风速')) return 'm/s'
  return sensor.unit || ''
}

const mergedSensors = computed(() => {
  const list = props.sensors || []
  return list.map((s, i) => {
    const pos = getSensorPosition(s, i)
    const wsData = props.wsSensorData?.get(s.sensorId)
    const value = wsData?.value ?? s.currentValue ?? 0
    return {
      x: pos.x,
      y: pos.y,
      value,
      label: getSensorLabel(s),
      unit: getSensorUnit(s),
      level: getSensorLevel(wsData ? { ...s, currentValue: value, status: wsData.status ?? s.status } : s),
      tunnel: s.tunnel || s.location || '',
      sensorId: s.sensorId,
      sensorName: s.sensorName || s.name || ''
    }
  })
})

const mergedPersonnel = computed(() => {
  const list = props.personnel || []
  if (list.length === 0) return []
  return list.map(p => {
    const match = defaultTunnelCenters.find(c => c.tunnel === p.zoneName || c.tunnel === (p.zoneCode || ''))
    const cx = p.coordinatesX ?? match?.cx ?? 200
    const cy = p.coordinatesY ?? match?.cy ?? 200
    return {
      x: cx,
      y: cy,
      name: p.zoneName || p.zoneCode || '',
      personnel: p.count ?? 0,
      tunnel: p.zoneName || p.zoneCode || ''
    }
  })
})

let tick = 0

function getLevelColor(level: string, alpha = 1) {
  if (level === 'danger') return `rgba(255,71,87,${alpha})`
  if (level === 'warning') return `rgba(255,184,0,${alpha})`
  return `rgba(46,213,115,${alpha})`
}

function drawTunnel(t: TunnelDef) {
  if (!ctx) return
  const isVertical = t.x1 === t.x2
  ctx.save()

  if (isVertical) {
    const x = t.x1 - t.width / 2
    const y = Math.min(t.y1, t.y2)
    const w = t.width
    const h = Math.abs(t.y2 - t.y1)
    ctx.fillStyle = t.color
    ctx.fillRect(x, y, w, h)
    ctx.strokeStyle = 'rgba(0,212,255,0.3)'
    ctx.lineWidth = 1
    ctx.strokeRect(x, y, w, h)
  } else {
    const x = Math.min(t.x1, t.x2)
    const y = t.y1 - t.width / 2
    const w = Math.abs(t.x2 - t.x1)
    const h = t.width
    ctx.fillStyle = t.color
    ctx.fillRect(x, y, w, h)
    ctx.strokeStyle = 'rgba(0,212,255,0.3)'
    ctx.lineWidth = 1
    ctx.strokeRect(x, y, w, h)
  }

  const cx = (t.x1 + t.x2) / 2
  const cy = (t.y1 + t.y2) / 2
  ctx.font = '12px "Noto Sans SC"'
  ctx.fillStyle = 'rgba(232,240,254,0.6)'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(t.name, cx, cy)

  ctx.restore()
}

function drawSensor(s: any) {
  if (!ctx) return
  ctx.save()

  const r = 6
  const pulse = Math.sin(tick * 0.05 + s.x) * 0.3 + 0.7

  if (s.level === 'danger') {
    ctx.beginPath()
    ctx.arc(s.x, s.y, r + 4, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(255,71,87,${0.15 * pulse})`
    ctx.fill()
  }

  ctx.beginPath()
  ctx.arc(s.x, s.y, r, 0, Math.PI * 2)
  ctx.fillStyle = getLevelColor(s.level, 0.9)
  ctx.fill()
  ctx.strokeStyle = getLevelColor(s.level, 0.4)
  ctx.lineWidth = 2
  ctx.stroke()

  ctx.font = 'bold 11px "Orbitron", monospace'
  ctx.fillStyle = '#e8f0fe'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'bottom'
  ctx.fillText(`${s.value}`, s.x, s.y - 10)

  ctx.font = '10px "Noto Sans SC"'
  ctx.fillStyle = 'rgba(232,240,254,0.6)'
  ctx.textBaseline = 'top'
  ctx.fillText(`${s.label} ${s.unit}`, s.x, s.y + 10)

  ctx.restore()
}

function drawZone(z: any) {
  if (!ctx) return
  ctx.save()

  const bubbleR = 18
  ctx.beginPath()
  ctx.arc(z.x, z.y + 50, bubbleR, 0, Math.PI * 2)
  ctx.fillStyle = 'rgba(0,212,255,0.12)'
  ctx.fill()
  ctx.strokeStyle = 'rgba(0,212,255,0.4)'
  ctx.lineWidth = 1
  ctx.stroke()

  ctx.font = 'bold 13px "Orbitron", monospace'
  ctx.fillStyle = '#00d4ff'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(String(z.personnel), z.x, z.y + 46)

  ctx.font = '9px "Noto Sans SC"'
  ctx.fillStyle = 'rgba(232,240,254,0.5)'
  ctx.fillText('人', z.x, z.y + 60)

  ctx.restore()
}

function drawDustFlicker() {
  if (!ctx) return
  const dustSensors = mergedSensors.value.filter(s => s.level === 'warning' || s.level === 'danger')
  if (dustSensors.length === 0) return

  const alpha = Math.sin(tick * 0.08) * 0.08 + 0.08
  dustSensors.forEach(s => {
    ctx.save()
    const gradient = ctx.createRadialGradient(s.x, s.y, 10, s.x, s.y, 60)
    const color = s.level === 'danger' ? '255,71,87' : '255,184,0'
    gradient.addColorStop(0, `rgba(${color},${alpha})`)
    gradient.addColorStop(1, `rgba(${color},0)`)
    ctx.fillStyle = gradient
    ctx.fillRect(s.x - 60, s.y - 60, 120, 120)
    ctx.restore()
  })
}

function drawHeatmap() {
  if (!ctx) return
  const data = props.heatmapData || []
  if (data.length === 0) return

  data.forEach(p => {
    const x = p.coordinatesX ?? 0
    const y = p.coordinatesY ?? 0
    const val = p.value ?? 0
    const alpha = Math.min(val / 1.5, 0.35)
    ctx.save()
    const gradient = ctx.createRadialGradient(x, y, 5, x, y, 50)
    gradient.addColorStop(0, `rgba(255,71,87,${alpha})`)
    gradient.addColorStop(0.5, `rgba(255,184,0,${alpha * 0.5})`)
    gradient.addColorStop(1, 'rgba(255,184,0,0)')
    ctx.fillStyle = gradient
    ctx.fillRect(x - 50, y - 50, 100, 100)
    ctx.restore()
  })
}

function draw() {
  if (!ctx || !canvasRef.value) return
  const w = canvasRef.value.width
  const h = canvasRef.value.height
  ctx.clearRect(0, 0, w, h)

  tunnelDefs.forEach(drawTunnel)
  drawHeatmap()
  drawDustFlicker()
  mergedSensors.value.forEach(drawSensor)
  mergedPersonnel.value.forEach(drawZone)

  ctx.font = '11px "Noto Sans SC"'
  ctx.fillStyle = 'rgba(232,240,254,0.3)'
  ctx.textAlign = 'left'
  ctx.fillText('矿井通风网络示意图', 20, h - 16)

  tick++
  animFrame = requestAnimationFrame(draw)
}

function handleClick(e: MouseEvent) {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top

  for (const z of mergedPersonnel.value) {
    const dx = x - z.x
    const dy = y - (z.y + 50)
    if (dx * dx + dy * dy < 400) {
      emit('select-tunnel', z.tunnel)
      return
    }
  }
  for (const s of mergedSensors.value) {
    const dx = x - s.x
    const dy = y - s.y
    if (dx * dx + dy * dy < 200) {
      emit('select-tunnel', s.tunnel)
      return
    }
  }
}

function handleResize() {
  if (!canvasRef.value) return
  const parent = canvasRef.value.parentElement
  if (!parent) return
  canvasRef.value.width = parent.clientWidth
  canvasRef.value.height = parent.clientHeight
}

onMounted(() => {
  if (!canvasRef.value) return
  ctx = canvasRef.value.getContext('2d')
  handleResize()
  draw()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  cancelAnimationFrame(animFrame)
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="mine-map-wrapper panel-card">
    <div class="map-title">矿井通风网络</div>
    <canvas
      ref="canvasRef"
      class="mine-canvas"
      @click="handleClick"
    ></canvas>
    <div class="map-legend">
      <span class="legend-item"><span class="dot normal"></span>正常</span>
      <span class="legend-item"><span class="dot warning"></span>预警</span>
      <span class="legend-item"><span class="dot danger"></span>报警</span>
    </div>
  </div>
</template>

<style scoped>
.mine-map-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.map-title {
  position: absolute;
  top: 12px;
  left: 16px;
  font-size: 14px;
  color: var(--accent);
  z-index: 1;
  padding-left: 10px;
  border-left: 3px solid var(--accent);
  letter-spacing: 2px;
}

.mine-canvas {
  flex: 1;
  width: 100%;
  cursor: pointer;
}

.map-legend {
  position: absolute;
  bottom: 12px;
  right: 16px;
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: var(--text-secondary);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.normal { background: var(--success); }
.dot.warning { background: var(--accent-warm); }
.dot.danger { background: var(--danger); }
</style>
