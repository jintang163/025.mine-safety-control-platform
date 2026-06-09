<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const emit = defineEmits<{
  (e: 'select-tunnel', tunnel: string): void
}>()

const canvasRef = ref<HTMLCanvasElement>()
let animFrame = 0
let ctx: CanvasRenderingContext2D | null = null

interface Tunnel {
  name: string
  x1: number; y1: number
  x2: number; y2: number
  width: number
  color: string
}

interface Sensor {
  x: number; y: number
  value: number
  label: string
  unit: string
  level: 'normal' | 'warning' | 'danger'
  tunnel: string
}

interface Zone {
  x: number; y: number
  name: string
  personnel: number
  tunnel: string
}

const tunnels: Tunnel[] = [
  { name: '运输巷', x1: 60, y1: 280, x2: 320, y2: 280, width: 50, color: 'rgba(0,212,255,0.15)' },
  { name: '回风巷', x1: 60, y1: 120, x2: 320, y2: 120, width: 50, color: 'rgba(0,212,255,0.15)' },
  { name: '中央变电所', x1: 320, y1: 120, x2: 320, y2: 280, width: 50, color: 'rgba(168,85,247,0.15)' },
  { name: '掘进工作面', x1: 320, y1: 120, x2: 620, y2: 120, width: 50, color: 'rgba(255,184,0,0.15)' },
  { name: '采煤工作面', x1: 320, y1: 280, x2: 620, y2: 280, width: 50, color: 'rgba(255,71,87,0.15)' }
]

const sensors: Sensor[] = [
  { x: 150, y: 100, value: 0.32, label: 'CH₄', unit: '%', level: 'normal', tunnel: '回风巷' },
  { x: 400, y: 100, value: 0.68, label: 'CH₄', unit: '%', level: 'warning', tunnel: '掘进工作面' },
  { x: 550, y: 100, value: 0.95, label: 'CO', unit: 'ppm', level: 'danger', tunnel: '掘进工作面' },
  { x: 150, y: 260, value: 1.2, label: '粉尘', unit: 'mg/m³', level: 'warning', tunnel: '运输巷' },
  { x: 400, y: 260, value: 0.25, label: 'CH₄', unit: '%', level: 'normal', tunnel: '采煤工作面' },
  { x: 550, y: 260, value: 0.88, label: 'CH₄', unit: '%', level: 'danger', tunnel: '采煤工作面' },
  { x: 300, y: 200, value: 24.5, label: '温度', unit: '°C', level: 'normal', tunnel: '中央变电所' },
  { x: 340, y: 200, value: 1.8, label: '风速', unit: 'm/s', level: 'normal', tunnel: '中央变电所' }
]

const zones: Zone[] = [
  { x: 180, y: 120, name: '回风巷', personnel: 5, tunnel: '回风巷' },
  { x: 180, y: 280, name: '运输巷', personnel: 8, tunnel: '运输巷' },
  { x: 320, y: 200, name: '中央变电所', personnel: 2, tunnel: '中央变电所' },
  { x: 470, y: 120, name: '掘进面', personnel: 12, tunnel: '掘进工作面' },
  { x: 470, y: 280, name: '采煤面', personnel: 15, tunnel: '采煤工作面' }
]

let tick = 0

function getLevelColor(level: string, alpha = 1) {
  if (level === 'danger') return `rgba(255,71,87,${alpha})`
  if (level === 'warning') return `rgba(255,184,0,${alpha})`
  return `rgba(46,213,115,${alpha})`
}

function drawTunnel(t: Tunnel) {
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

function drawSensor(s: Sensor) {
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

function drawZone(z: Zone) {
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
  const dustSensor = sensors.find(s => s.label === '粉尘' && s.level === 'warning')
  if (!dustSensor) return

  const alpha = Math.sin(tick * 0.08) * 0.08 + 0.08
  ctx.save()
  const gradient = ctx.createRadialGradient(dustSensor.x, dustSensor.y, 10, dustSensor.x, dustSensor.y, 60)
  gradient.addColorStop(0, `rgba(255,184,0,${alpha})`)
  gradient.addColorStop(1, 'rgba(255,184,0,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(dustSensor.x - 60, dustSensor.y - 60, 120, 120)
  ctx.restore()
}

function draw() {
  if (!ctx || !canvasRef.value) return
  const w = canvasRef.value.width
  const h = canvasRef.value.height
  ctx.clearRect(0, 0, w, h)

  tunnels.forEach(drawTunnel)
  drawDustFlicker()
  sensors.forEach(drawSensor)
  zones.forEach(drawZone)

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

  for (const z of zones) {
    const dx = x - z.x
    const dy = y - (z.y + 50)
    if (dx * dx + dy * dy < 400) {
      emit('select-tunnel', z.tunnel)
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
