<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ data: any }>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const onlineRate = computed(() => {
  if (!props.data) return 92
  const total = (props.data.online ?? 46) + (props.data.offline ?? 2) + (props.data.fault ?? 2)
  return Math.round(((props.data.online ?? 46) / total) * 100)
})

const stats = computed(() => ({
  online: props.data?.online ?? 46,
  offline: props.data?.offline ?? 2,
  fault: props.data?.fault ?? 2
}))

function getOption() {
  const rate = onlineRate.value

  return {
    backgroundColor: 'transparent',
    series: [{
      type: 'pie',
      radius: ['60%', '75%'],
      center: ['50%', '40%'],
      startAngle: 90,
      silent: true,
      label: { show: false },
      data: [
        {
          value: rate,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
              { offset: 0, color: '#2ed573' },
              { offset: 1, color: '#00d4ff' }
            ])
          }
        },
        {
          value: 100 - rate,
          itemStyle: { color: 'rgba(255,255,255,0.06)' }
        }
      ]
    }],
    graphic: [{
      type: 'text',
      left: 'center',
      top: '30%',
      style: {
        text: `${rate}%`,
        textAlign: 'center',
        fill: '#00d4ff',
        fontSize: 28,
        fontFamily: 'Orbitron',
        fontWeight: 700
      }
    }, {
      type: 'text',
      left: 'center',
      top: '46%',
      style: {
        text: '在线率',
        textAlign: 'center',
        fill: 'rgba(232,240,254,0.5)',
        fontSize: 12
      }
    }],
    animation: true,
    animationDuration: 1200
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, 'dark')
  chart.setOption(getOption())
}

watch(() => props.data, () => {
  chart?.setOption(getOption())
})

onMounted(initChart)

onUnmounted(() => {
  chart?.dispose()
})
</script>

<template>
  <div class="device-chart-wrapper">
    <div ref="chartRef" class="chart-area"></div>
    <div class="stat-row">
      <div class="stat-item">
        <span class="stat-dot online"></span>
        <span class="stat-label">在线</span>
        <span class="number-display stat-value">{{ stats.online }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-dot offline"></span>
        <span class="stat-label">离线</span>
        <span class="number-display stat-value">{{ stats.offline }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-dot fault"></span>
        <span class="stat-label">故障</span>
        <span class="number-display stat-value danger">{{ stats.fault }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.device-chart-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chart-area {
  flex: 1;
  min-height: 0;
}

.stat-row {
  display: flex;
  justify-content: space-around;
  padding: 8px 0 4px;
  border-top: 1px solid rgba(0, 212, 255, 0.1);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.stat-dot.online { background: var(--success); box-shadow: 0 0 6px rgba(46,213,115,0.5); }
.stat-dot.offline { background: #666; }
.stat-dot.fault { background: var(--danger); box-shadow: 0 0 6px rgba(255,71,87,0.5); }

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 16px;
  color: var(--text-primary);
}

.stat-value.danger {
  color: var(--danger);
}
</style>
