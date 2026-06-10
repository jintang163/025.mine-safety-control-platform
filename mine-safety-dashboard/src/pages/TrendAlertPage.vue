<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import {
  fetchTrendAlerts,
  fetchTrendRules,
  saveTrendRule,
  acknowledgeTrendAlert,
  runTrendCheck
} from '@/utils/api'

const sensorTypeOptions = [
  { label: '全部', value: '' },
  { label: '瓦斯(GAS)', value: 'GAS' },
  { label: '粉尘(DUST)', value: 'DUST' },
  { label: '一氧化碳(CO)', value: 'CO' }
]
const statusOptions = [
  { label: '全部', value: -1 },
  { label: '待处理', value: 0 },
  { label: '已确认', value: 1 },
  { label: '已忽略', value: 2 }
]
const directionOptions = [
  { label: '上升', value: 'RISING' },
  { label: '下降', value: 'FALLING' }
]
const metricOptions = [
  { label: '日平均值', value: 'DAILY_AVG' },
  { label: '日最大值', value: 'DAILY_MAX' },
  { label: '超标次数', value: 'OVER_THRESHOLD_COUNT' }
]
const periodOptions = [
  { label: '日', value: 'DAY' },
  { label: '周', value: 'WEEK' },
  { label: '月', value: 'MONTH' }
]
const severityOptions = [
  { label: '信息', value: 'INFO' },
  { label: '警告', value: 'WARNING' },
  { label: '报警', value: 'ALERT' },
  { label: '严重', value: 'CRITICAL' }
]

const filter = ref({ sensorType: '', status: -1 as number })
const alerts = ref<any[]>([])
const rules = ref<any[]>([])
const loadingAlerts = ref(false)
const loadingRules = ref(false)
const running = ref(false)
const ruleModal = ref(false)
const ackVisible = ref(false)
const ackAlertNo = ref('')
const ackOperator = ref('')
const ackLoading = ref(false)

const emptyRule = () => ({
  id: null as number | null,
  ruleCode: '',
  ruleName: '',
  description: '',
  sensorType: 'GAS',
  zoneCode: '',
  metric: 'DAILY_AVG',
  trendDirection: 'RISING',
  consecutivePeriods: 3,
  periodUnit: 'WEEK',
  thresholdValue: null as number | null,
  severity: 'WARNING',
  notificationChannels: 'APP,WECHAT_WORK',
  enabled: true
})
const editingRule = ref<any>(emptyRule())

let trendChart: any = null

async function loadAlerts() {
  loadingAlerts.value = true
  try {
    const res: any = await fetchTrendAlerts(
      filter.value.sensorType || undefined,
      undefined,
      filter.value.status >= 0 ? filter.value.status : undefined
    )
    alerts.value = res?.data ?? []
  } catch (e) {
    alerts.value = []
  } finally {
    loadingAlerts.value = false
  }
}

async function loadRules() {
  loadingRules.value = true
  try {
    const res: any = await fetchTrendRules()
    rules.value = res?.data ?? []
  } catch (e) {
    rules.value = []
  } finally {
    loadingRules.value = false
  }
}

function editRule(r?: any) {
  editingRule.value = r ? { ...r } : emptyRule()
  ruleModal.value = true
}

async function handleSaveRule() {
  try {
    await saveTrendRule(editingRule.value)
    ruleModal.value = false
    loadRules()
    alert('规则保存成功')
  } catch (e: any) {
    alert('保存失败：' + (e.message ?? ''))
  }
}

function openAck(alertNo: string) {
  ackAlertNo.value = alertNo
  ackOperator.value = ''
  ackVisible.value = true
}

async function handleAck() {
  if (!ackOperator.value.trim()) {
    alert('请输入确认人')
    return
  }
  ackLoading.value = true
  try {
    await acknowledgeTrendAlert(ackAlertNo.value, ackOperator.value.trim())
    ackVisible.value = false
    loadAlerts()
  } catch (e: any) {
    alert('确认失败：' + (e.message ?? ''))
  } finally {
    ackLoading.value = false
  }
}

async function handleRunCheck() {
  if (!confirm('确认立即执行趋势分析检测吗？')) return
  running.value = true
  try {
    await runTrendCheck()
    loadAlerts()
    alert('趋势分析执行完成')
  } catch (e: any) {
    alert('执行失败：' + (e.message ?? ''))
  } finally {
    running.value = false
  }
}

function getStatusText(s: number) {
  return { 0: '待处理', 1: '已确认', 2: '已忽略' }[s] ?? '未知'
}
function getStatusClass(s: number) {
  return { 0: 'status-warn', 1: 'status-success', 2: 'status-info' }[s] ?? ''
}
function getSeverityClass(level: string) {
  return { INFO: 'sev-info', WARNING: 'sev-warn', ALERT: 'sev-alert', CRITICAL: 'sev-critical' }[level] ?? 'sev-info'
}

function renderTrendChart(alert: any) {
  setTimeout(() => {
    const id = `chart-${alert.alertNo}`
    const el = document.getElementById(id) as HTMLElement
    if (!el) return
    if (!trendChart) trendChart = echarts.init(el)
    else trendChart.dispose(), trendChart = echarts.init(el)
    const pvs = alert.periodValues ?? []
    trendChart.setOption({
      title: { text: '趋势数据', left: 'center', textStyle: { fontSize: 13 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: pvs.map((p: any) => p.period),
        axisLabel: { color: '#4b5563' }
      },
      yAxis: { type: 'value', axisLabel: { color: '#4b5563' } },
      series: [{
        type: 'line',
        smooth: true,
        data: pvs.map((p: any) => p.value),
        itemStyle: { color: alert.trendDirection === 'RISING' ? '#dc2626' : '#16a34a' },
        areaStyle: {
          color: alert.trendDirection === 'RISING'
            ? 'rgba(220,38,38,0.15)' : 'rgba(22,163,74,0.15)'
        },
        markPoint: {
          data: [{ type: 'max', name: '最高' }, { type: 'min', name: '最低' }]
        }
      }]
    })
  }, 50)
}

onMounted(() => {
  loadAlerts()
  loadRules()
})
</script>

<template>
  <div class="page-container">
    <div class="page-title">
      <h2>趋势预警</h2>
      <span class="sub">识别长期趋势（如瓦斯浓度连续上升），自动生成预警并推送</span>
      <button class="btn btn-warn" style="margin-left:auto" @click="handleRunCheck" :disabled="running">
        {{ running ? '执行中...' : '立即执行检测' }}
      </button>
    </div>

    <div class="card">
      <div class="card-title">趋势预警列表</div>
      <div class="filter-bar">
        <label>传感器类型</label>
        <select v-model="filter.sensorType">
          <option v-for="o in sensorTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <label>状态</label>
        <select v-model="filter.status">
          <option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
        <button class="btn btn-primary" @click="loadAlerts">查询</button>
      </div>

      <div v-if="loadingAlerts" class="loading">加载中...</div>
      <div v-else class="alert-list">
        <div v-for="alert in alerts" :key="alert.alertNo" class="alert-card"
             @click="alert._expanded = !alert._expanded">
          <div class="alert-header">
            <span :class="['sev-tag', getSeverityClass(alert.severity)]">{{ alert.severity }}</span>
            <span class="alert-title">{{ alert.description }}</span>
            <span :class="['status-tag', getStatusClass(alert.status)]">{{ getStatusText(alert.status) }}</span>
          </div>
          <div class="alert-meta">
            <span>编号：<code>{{ alert.alertNo }}</code></span>
            <span>规则：{{ alert.ruleName }}</span>
            <span>类型：{{ alert.sensorType }}</span>
            <span>区域：{{ alert.zoneCode ?? '全局' }}</span>
            <span>指标：{{ alert.metric }} / 连续{{ alert.consecutivePeriods }}{{ alert.periodUnit }}</span>
            <span>周期：{{ alert.startDate }} ~ {{ alert.endDate }}</span>
          </div>
          <div v-if="alert._expanded" class="alert-detail" @click.stop>
            <div :id="`chart-${alert.alertNo}`" class="chart-box"></div>
            <div class="alert-actions">
              <button v-if="alert.status === 0" class="btn btn-primary" @click="openAck(alert.alertNo)">
                确认预警
              </button>
              <button class="btn" @click.stop="renderTrendChart(alert)">重新渲染图表</button>
            </div>
          </div>
        </div>
        <div v-if="alerts.length === 0" class="empty">暂无趋势预警</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">
        <span>趋势规则管理</span>
        <button class="btn btn-primary" @click="editRule()">新增规则</button>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>规则编码</th>
              <th>规则名称</th>
              <th>传感器类型</th>
              <th>指标</th>
              <th>方向</th>
              <th>连续周期</th>
              <th>严重级别</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rules" :key="r.id">
              <td class="mono">{{ r.ruleCode }}</td>
              <td>{{ r.ruleName }}</td>
              <td>{{ r.sensorType }}</td>
              <td>{{ r.metric }}</td>
              <td>{{ r.trendDirection === 'RISING' ? '上升' : '下降' }}</td>
              <td>{{ r.consecutivePeriods }}{{ r.periodUnit }}</td>
              <td><span :class="['sev-tag', getSeverityClass(r.severity)]">{{ r.severity }}</span></td>
              <td>{{ r.enabled ? '启用' : '禁用' }}</td>
              <td>
                <button class="btn btn-link" @click="editRule(r)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 规则编辑 -->
    <div v-if="ruleModal" class="modal-mask" @click.self="ruleModal = false">
      <div class="modal modal-lg">
        <div class="modal-header">
          <h3>{{ editingRule.id ? '编辑趋势规则' : '新增趋势规则' }}</h3>
          <button class="close" @click="ruleModal = false">&times;</button>
        </div>
        <div class="modal-body form-grid-2">
          <div class="form-item">
            <label>规则编码 *</label>
            <input v-model="editingRule.ruleCode" type="text" :disabled="!!editingRule.id" />
          </div>
          <div class="form-item">
            <label>规则名称 *</label>
            <input v-model="editingRule.ruleName" type="text" />
          </div>
          <div class="form-item">
            <label>传感器类型 *</label>
            <select v-model="editingRule.sensorType">
              <option v-for="o in sensorTypeOptions.filter(o => o.value)" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>区域编码（可选）</label>
            <input v-model="editingRule.zoneCode" type="text" placeholder="空=全部" />
          </div>
          <div class="form-item">
            <label>监测指标 *</label>
            <select v-model="editingRule.metric">
              <option v-for="o in metricOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>趋势方向 *</label>
            <select v-model="editingRule.trendDirection">
              <option v-for="o in directionOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>连续周期数 *</label>
            <input v-model.number="editingRule.consecutivePeriods" type="number" min="2" />
          </div>
          <div class="form-item">
            <label>周期单位 *</label>
            <select v-model="editingRule.periodUnit">
              <option v-for="o in periodOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>阈值（可选）</label>
            <input v-model.number="editingRule.thresholdValue" type="number" step="0.0001" />
          </div>
          <div class="form-item">
            <label>严重级别 *</label>
            <select v-model="editingRule.severity">
              <option v-for="o in severityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="form-item span-2">
            <label>通知渠道</label>
            <input v-model="editingRule.notificationChannels" type="text" placeholder="APP,WECHAT_WORK,SMS" />
          </div>
          <div class="form-item span-2">
            <label>规则描述</label>
            <input v-model="editingRule.description" type="text" />
          </div>
          <div class="form-item span-2">
            <label>
              <input v-model="editingRule.enabled" type="checkbox" />
              启用规则
            </label>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="ruleModal = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveRule">保存</button>
        </div>
      </div>
    </div>

    <!-- 确认预警 -->
    <div v-if="ackVisible" class="modal-mask" @click.self="ackVisible = false">
      <div class="modal">
        <div class="modal-header">
          <h3>确认趋势预警</h3>
          <button class="close" @click="ackVisible = false">&times;</button>
        </div>
        <div class="modal-body">
          <label>确认人</label>
          <input v-model="ackOperator" type="text" placeholder="请输入确认人姓名/工号" class="full-width" />
        </div>
        <div class="modal-footer">
          <button class="btn" @click="ackVisible = false">取消</button>
          <button class="btn btn-primary" @click="handleAck" :disabled="ackLoading">
            {{ ackLoading ? '提交中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; background: #f3f4f6; min-height: 100vh; }
.page-title {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
.page-title h2 { margin: 0; color: #111827; font-size: 22px; }
.page-title .sub { color: #6b7280; font-size: 13px; margin-left: 12px; }

.card {
  background: #ffffff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 14px;
}

.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.filter-bar label { font-size: 13px; color: #374151; }
.filter-bar select {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
}

.btn {
  padding: 7px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  background: #e5e7eb;
  color: #1f2937;
}
.btn-primary { background: #1d4ed8; color: #fff; }
.btn-warn { background: #d97706; color: #fff; }
.btn-link { background: transparent; color: #1d4ed8; padding: 4px 8px; }

.loading, .empty { padding: 20px; text-align: center; color: #6b7280; }

.alert-list { display: flex; flex-direction: column; gap: 10px; }
.alert-card {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.alert-card:hover { border-color: #93c5fd; background: #f0f9ff; }
.alert-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.alert-title { flex: 1; color: #111827; font-size: 14px; font-weight: 500; }
.alert-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 12px;
}
.alert-meta code {
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 3px;
  color: #1d4ed8;
  font-size: 12px;
}
.alert-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e5e7eb;
}
.alert-actions { margin-top: 10px; display: flex; gap: 10px; }

.sev-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}
.sev-info { background: #dbeafe; color: #1d4ed8; }
.sev-warn { background: #fef3c7; color: #92400e; }
.sev-alert { background: #fed7aa; color: #9a3412; }
.sev-critical { background: #fee2e2; color: #991b1b; }

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.status-warn { background: #fef3c7; color: #92400e; }
.status-success { background: #dcfce7; color: #166534; }
.status-info { background: #e0e7ff; color: #3730a3; }

.chart-box { width: 100%; height: 260px; }

.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td {
  padding: 8px 10px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.data-table th { background: #f9fafb; color: #374151; font-weight: 600; }
.data-table .mono { font-family: monospace; color: #1d4ed8; }

.modal-mask {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.modal {
  background: #fff; border-radius: 8px; width: 480px; max-width: 90%;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
  max-height: 90vh;
  overflow-y: auto;
}
.modal-lg { width: 700px; }
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px; border-bottom: 1px solid #e5e7eb;
}
.modal-header h3 { margin: 0; font-size: 16px; }
.modal-header .close { background: none; border: none; font-size: 22px; cursor: pointer; color: #6b7280; }
.modal-body { padding: 18px; }
.modal-body label { font-size: 13px; color: #374151; margin-bottom: 6px; display: block; }
.modal-body input, .modal-body select {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 13px;
}
.modal-body .full-width { width: 100%; box-sizing: border-box; }
.form-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.form-item { display: flex; flex-direction: column; }
.form-item.span-2 { grid-column: span 2; }
.modal-footer {
  padding: 12px 18px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .form-grid-2 { grid-template-columns: 1fr; }
  .form-item.span-2 { grid-column: span 1; }
}
</style>
