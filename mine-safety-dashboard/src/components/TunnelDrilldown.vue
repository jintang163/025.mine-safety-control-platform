<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { fetchTunnelSensorHistory, fetchTunnelAlertRecords } from '@/utils/api'

const props = defineProps<{ tunnel: string }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const historyData = ref<any>(null)
const alertRecords = ref<any[]>([])
const loading = ref(true)

async function loadData() {
  loading.value = true
  try {
    const [hist, alerts] = await Promise.allSettled([
      fetchTunnelSensorHistory(props.tunnel),
      fetchTunnelAlertRecords(props.tunnel)
    ])
    if (hist.status === 'fulfilled') historyData.value = hist.value
    if (alerts.status === 'fulfilled') alertRecords.value = alerts.value ?? []
  } catch {
    historyData.value = generateMockHistory()
    alertRecords.value = generateMockAlerts()
  }
  loading.value = false
}

function generateMockHistory() {
  const times = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
  return {
    times,
    series: [
      { name: 'CH₄ (%)', values: times.map(() => (Math.random() * 1.2).toFixed(2)) },
      { name: 'CO (ppm)', values: times.map(() => Math.floor(Math.random() * 30)) },
      { name: '温度 (°C)', values: times.map(() => (22 + Math.random() * 6).toFixed(1)) }
    ]
  }
}

function generateMockAlerts() {
  const types = ['瓦斯超限', 'CO超限', '粉尘超标', '温度异常']
  const statuses = ['confirmed', 'pending', 'pending']
  return Array.from({ length: 8 }, (_, i) => ({
    id: i + 1,
    time: `${String(Math.floor(Math.random() * 24)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}`,
    type: types[Math.floor(Math.random() * types.length)],
    value: (Math.random() * 2).toFixed(2),
    status: statuses[Math.floor(Math.random() * statuses.length)]
  }))
}

function getChartOption(data: any) {
  const d = data ?? historyData.value ?? generateMockHistory()

  return {
    backgroundColor: 'transparent',
    grid: { top: 36, right: 16, bottom: 24, left: 44 },
    legend: {
      top: 0,
      textStyle: { color: 'rgba(232,240,254,0.7)', fontSize: 11 },
      itemWidth: 16,
      itemHeight: 2
    },
    xAxis: {
      type: 'category',
      data: d.times,
      axisLine: { lineStyle: { color: 'rgba(0,212,255,0.2)' } },
      axisLabel: { color: 'rgba(232,240,254,0.5)', fontSize: 10, interval: 3 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: 'rgba(232,240,254,0.5)', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0,212,255,0.08)' } }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,22,40,0.9)',
      borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 12 }
    },
    series: d.series.map((s: any, i: number) => ({
      name: s.name,
      type: 'line',
      data: s.values,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2 },
      itemStyle: {
        color: ['#00d4ff', '#ffb800', '#2ed573'][i % 3]
      }
    })),
    animation: true,
    animationDuration: 600
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, 'dark')
  chart.setOption(getChartOption(null))
}

watch(() => props.tunnel, () => {
  loadData()
})

onMounted(() => {
  loadData().then(() => initChart())
})

onUnmounted(() => {
  chart?.dispose()
})
</script>

<template>
  <Transition name="slide">
    <div class="drilldown-panel">
      <div class="drilldown-header">
        <h3 class="glow-text">{{ tunnel }} - 详情</h3>
        <button class="close-btn" @click="emit('close')">×</button>
      </div>

      <div class="drilldown-body">
        <div class="section">
          <div class="section-title">传感器历史数据 (24h)</div>
          <div ref="chartRef" class="history-chart"></div>
        </div>

        <div class="section">
          <div class="section-title">报警记录</div>
          <div class="table-wrapper">
            <table class="alert-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>类型</th>
                  <th>数值</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in alertRecords" :key="r.id">
                  <td class="number-display">{{ r.time }}</td>
                  <td>{{ r.type }}</td>
                  <td class="number-display">{{ r.value }}</td>
                  <td>
                    <span class="status-badge" :class="r.status">
                      {{ r.status === 'confirmed' ? '已确认' : '未确认' }}
                    </span>
                  </td>
                </tr>
                <tr v-if="alertRecords.length === 0">
                  <td colspan="4" class="empty-row">暂无报警记录</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.drilldown-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: 420px;
  height: 100%;
  background: rgba(8, 20, 44, 0.95);
  border-left: 1px solid var(--border-glow);
  backdrop-filter: blur(16px);
  z-index: 100;
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 32px rgba(0, 0, 0, 0.5);
}

.drilldown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-glow);
}

.drilldown-header h3 {
  font-size: 16px;
  letter-spacing: 2px;
}

.close-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-glow);
  background: transparent;
  color: var(--text-primary);
  font-size: 20px;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(255, 71, 87, 0.2);
  border-color: var(--danger);
  color: var(--danger);
}

.drilldown-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 13px;
  color: var(--accent);
  margin-bottom: 10px;
  padding-left: 10px;
  border-left: 3px solid var(--accent);
  letter-spacing: 1px;
}

.history-chart {
  width: 100%;
  height: 200px;
}

.table-wrapper {
  max-height: 280px;
  overflow-y: auto;
}

.alert-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.alert-table th {
  text-align: left;
  padding: 8px 6px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-glow);
  font-weight: 500;
}

.alert-table td {
  padding: 8px 6px;
  border-bottom: 1px solid rgba(0, 212, 255, 0.06);
  color: var(--text-primary);
}

.empty-row {
  text-align: center;
  color: var(--text-secondary);
  padding: 24px 0 !important;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
}

.status-badge.confirmed {
  background: rgba(46, 213, 115, 0.15);
  color: var(--success);
  border: 1px solid rgba(46, 213, 115, 0.3);
}

.status-badge.pending {
  background: rgba(255, 71, 87, 0.15);
  color: var(--danger);
  border: 1px solid rgba(255, 71, 87, 0.3);
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
