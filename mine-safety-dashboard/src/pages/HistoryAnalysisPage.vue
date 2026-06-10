<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import {
  fetchHistoryStatistics,
  fetchHistoryStatisticsByType,
  fetchHistoryOverview,
  fetchSensors
} from '@/utils/api'

const sensorTypeOptions = [
  { label: '全部', value: '' },
  { label: '瓦斯(GAS)', value: 'GAS' },
  { label: '粉尘(DUST)', value: 'DUST' },
  { label: '一氧化碳(CO)', value: 'CO' }
]
const dimensionOptions = [
  { label: '按小时', value: 'HOUR' },
  { label: '按日', value: 'DAY' },
  { label: '按月', value: 'MONTH' }
]

const sensorType = ref('GAS')
const zoneCode = ref('')
const sensorId = ref('')
const startDate = ref(getDefaultStart())
const endDate = ref(getDefaultEnd())
const timeDimension = ref('DAY')
const sensors = ref<any[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const statistics = ref<any>(null)
const detailData = ref<any>(null)

function getDefaultStart() {
  const d = new Date()
  d.setDate(d.getDate() - 7)
  return formatDate(d)
}
function getDefaultEnd() {
  return formatDate(new Date())
}
function formatDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadSensors() {
  try {
    const res: any = await fetchSensors({ type: sensorType.value || undefined })
    if (res?.data) {
      sensors.value = res.data
    }
  } catch (e) {
    console.warn('loadSensors failed', e)
  }
}

async function queryOverview() {
  loading.value = true
  try {
    const res: any = await fetchHistoryOverview(
      sensorType.value || undefined,
      zoneCode.value || undefined,
      startDate.value,
      endDate.value
    )
    statistics.value = res?.data ?? null
  } catch (e) {
    statistics.value = null
  } finally {
    loading.value = false
  }
}

async function queryByType() {
  if (!sensorType.value) return
  loading.value = true
  try {
    const res: any = await fetchHistoryStatisticsByType(
      sensorType.value,
      zoneCode.value || undefined,
      startDate.value,
      endDate.value,
      timeDimension.value
    )
    statistics.value = { list: res?.data ?? [] }
    renderBarChart(res?.data ?? [])
  } catch (e) {
    statistics.value = null
  } finally {
    loading.value = false
  }
}

async function querySensorDetail() {
  if (!sensorId.value) return
  detailLoading.value = true
  try {
    const res: any = await fetchHistoryStatistics(
      sensorId.value, startDate.value, endDate.value, timeDimension.value
    )
    detailData.value = res?.data ?? null
    renderLineChart(detailData.value)
  } catch (e) {
    detailData.value = null
  } finally {
    detailLoading.value = false
  }
}

let barChart: any = null
let lineChart: any = null

function renderBarChart(list: any[]) {
  setTimeout(() => {
    const el = document.getElementById('historyBarChart') as HTMLElement
    if (!el) return
    if (!barChart) barChart = echarts.init(el)
    barChart.setOption({
      title: { text: '传感器平均浓度对比', left: 'center', textStyle: { color: '#1f2937', fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { data: ['平均值', '最大值'], bottom: 0 },
      grid: { left: 50, right: 20, top: 50, bottom: 50 },
      xAxis: {
        type: 'category',
        data: list.map((d: any) => d.sensorName ?? d.sensorId),
        axisLabel: { rotate: 30, color: '#4b5563' }
      },
      yAxis: { type: 'value', axisLabel: { color: '#4b5563' } },
      series: [
        { name: '平均值', type: 'bar', data: list.map((d: any) => d.avgValue ?? 0), itemStyle: { color: '#3b82f6' } },
        { name: '最大值', type: 'bar', data: list.map((d: any) => d.maxValue ?? 0), itemStyle: { color: '#f59e0b' } }
      ]
    })
  }, 50)
}

function renderLineChart(detail: any) {
  setTimeout(() => {
    const el = document.getElementById('historyLineChart') as HTMLElement
    if (!el) return
    if (!lineChart) lineChart = echarts.init(el)
    const points = detail?.timeSeries ?? []
    lineChart.setOption({
      title: { text: `${detail?.sensorName ?? detail?.sensorId ?? ''} 浓度趋势`, left: 'center', textStyle: { color: '#1f2937', fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 50, bottom: 50 },
      xAxis: {
        type: 'category',
        data: points.map((p: any) => p.time),
        axisLabel: { rotate: 30, color: '#4b5563' }
      },
      yAxis: { type: 'value', name: detail?.unit ?? '', axisLabel: { color: '#4b5563' } },
      series: [
        {
          name: '浓度值',
          type: 'line',
          smooth: true,
          data: points.map((p: any) => p.value),
          itemStyle: { color: '#ef4444' },
          areaStyle: { color: 'rgba(239,68,68,0.15)' }
        }
      ]
    })
  }, 50)
}

watch(sensorType, () => {
  sensorId.value = ''
  loadSensors()
})

onMounted(() => {
  loadSensors()
  queryByType()
  window.addEventListener('resize', () => {
    barChart?.resize()
    lineChart?.resize()
  })
})

const formattedOverMinutes = computed(() => {
  const v = Number(detailData.value?.overThresholdDurationMinutes ?? 0)
  const h = Math.floor(v / 60)
  const m = v % 60
  return h > 0 ? `${h}小时${m}分钟` : `${m}分钟`
})
</script>

<template>
  <div class="page-container">
    <div class="page-title">
      <h2>历史数据分析</h2>
      <span class="sub">按小时/日/月维度查询历史瓦斯、粉尘、CO浓度数据</span>
    </div>

    <div class="filter-card">
      <div class="filter-row">
        <label>传感器类型</label>
        <select v-model="sensorType">
          <option v-for="o in sensorTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>

        <label>区域编码</label>
        <input v-model="zoneCode" type="text" placeholder="如 ZONE-01（可选）" />

        <label>开始日期</label>
        <input v-model="startDate" type="date" />

        <label>结束日期</label>
        <input v-model="endDate" type="date" />

        <label>时间维度</label>
        <select v-model="timeDimension">
          <option v-for="o in dimensionOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>

        <button class="btn btn-primary" @click="queryByType" :disabled="loading">
          {{ loading ? '查询中...' : '按类型查询' }}
        </button>
      </div>

      <div class="filter-row">
        <label>选择传感器</label>
        <select v-model="sensorId">
          <option value="">-- 请选择传感器（查看单传感器详情）--</option>
          <option v-for="s in sensors" :key="s.sensorId" :value="s.sensorId">
            {{ s.name }} ({{ s.sensorId }})
          </option>
        </select>
        <button class="btn btn-primary" @click="querySensorDetail" :disabled="detailLoading || !sensorId">
          {{ detailLoading ? '加载中...' : '查询详情' }}
        </button>
      </div>
    </div>

    <div v-if="statistics?.list" class="chart-card">
      <div id="historyBarChart" class="chart-box"></div>
    </div>

    <div v-if="statistics?.list" class="table-card">
      <div class="card-title">批量统计结果</div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>传感器ID</th>
              <th>名称</th>
              <th>区域</th>
              <th>平均值</th>
              <th>最大值</th>
              <th>最小值</th>
              <th>报警次数</th>
              <th>断电次数</th>
              <th>超标时长(分)</th>
              <th>数据条数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in statistics.list" :key="i">
              <td>{{ row.sensorId }}</td>
              <td>{{ row.sensorName }}</td>
              <td>{{ row.zoneCode ?? row.location }}</td>
              <td>{{ row.avgValue }}</td>
              <td class="danger">{{ row.maxValue }}</td>
              <td>{{ row.minValue }}</td>
              <td class="warn">{{ row.overWarningCount }}</td>
              <td class="danger">{{ row.overPowerOffCount }}</td>
              <td>{{ row.overThresholdDurationMinutes }}</td>
              <td>{{ row.dataCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="detailData" class="detail-section">
      <div class="section-title">
        <h3>单传感器详情 - {{ detailData.sensorName }} ({{ detailData.sensorId }})</h3>
      </div>

      <div class="stat-grid">
        <div class="stat-box">
          <span class="stat-label">平均值</span>
          <span class="stat-value">{{ detailData.avgValue }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-label">最大值</span>
          <span class="stat-value danger">{{ detailData.maxValue }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-label">最小值</span>
          <span class="stat-value">{{ detailData.minValue }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-label">预警次数</span>
          <span class="stat-value warn">{{ detailData.overWarningCount }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-label">断电次数</span>
          <span class="stat-value danger">{{ detailData.overPowerOffCount }}</span>
        </div>
        <div class="stat-box">
          <span class="stat-label">超标时长</span>
          <span class="stat-value warn">{{ formattedOverMinutes }}</span>
        </div>
      </div>

      <div id="historyLineChart" class="chart-box" style="height:360px;margin-top:16px"></div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; background: #f3f4f6; min-height: 100vh; }
.page-title { margin-bottom: 16px; }
.page-title h2 { margin: 0; color: #111827; font-size: 22px; }
.page-title .sub { color: #6b7280; font-size: 13px; margin-left: 12px; }

.filter-card, .chart-card, .table-card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.filter-row label { color: #374151; font-size: 13px; min-width: 72px; }
.filter-row input, .filter-row select {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  min-width: 120px;
}

.btn {
  padding: 7px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-size: 13px;
}
.btn-primary { background: #1d4ed8; color: #fff; }
.btn-primary:disabled { background: #9ca3af; cursor: not-allowed; }

.card-title { font-size: 15px; font-weight: 600; color: #111827; margin-bottom: 12px; }
.chart-box { width: 100%; height: 340px; }

.table-wrap { overflow-x: auto; }
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table th, .data-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.data-table th { background: #f9fafb; color: #374151; font-weight: 600; }
.data-table td.danger, .stat-value.danger { color: #dc2626; font-weight: 600; }
.data-table td.warn, .stat-value.warn { color: #d97706; font-weight: 600; }

.detail-section {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.section-title h3 { margin: 0 0 12px; color: #111827; font-size: 17px; }

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.stat-box {
  background: #f9fafb;
  border-radius: 6px;
  padding: 14px;
  text-align: center;
}
.stat-label { display: block; font-size: 12px; color: #6b7280; margin-bottom: 6px; }
.stat-value { font-size: 20px; color: #111827; font-weight: 700; }

@media (max-width: 1024px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
