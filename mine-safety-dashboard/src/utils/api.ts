import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('[API Error]', error.message)
    return Promise.reject(error)
  }
)

// Dashboard
export function fetchOverview() {
  return api.get('/dashboard/overview')
}

export function fetchSensorRealtime() {
  return api.get('/dashboard/sensor-realtime')
}

export function fetchAlertTrend() {
  return api.get('/dashboard/alert-trend')
}

export function fetchAlertTypeDistribution() {
  return api.get('/dashboard/alert-type-distribution')
}

export function fetchDeviceStatus() {
  return api.get('/dashboard/device-status')
}

export function fetchPersonnelDistribution() {
  return api.get('/dashboard/personnel-distribution')
}

export function fetchHeatmap() {
  return api.get('/dashboard/heatmap')
}

export function fetchTunnelSensorHistory(tunnel: string) {
  return api.get(`/dashboard/tunnel/${encodeURIComponent(tunnel)}/sensor-history`)
}

export function fetchTunnelAlertRecords(tunnel: string) {
  return api.get(`/dashboard/tunnel/${encodeURIComponent(tunnel)}/alert-records`)
}

// Report: History statistics
export function fetchHistoryStatistics(sensorId: string, startDate: string, endDate: string, timeDimension = 'DAY') {
  return api.get(`/report/history/${encodeURIComponent(sensorId)}`, {
    params: { startDate, endDate, timeDimension }
  })
}

export function fetchHistoryStatisticsByType(
  sensorType: string, zoneCode: string | undefined, startDate: string, endDate: string, timeDimension = 'DAY'
) {
  return api.get(`/report/history/type/${encodeURIComponent(sensorType)}`, {
    params: { zoneCode, startDate, endDate, timeDimension }
  })
}

export function fetchHistoryOverview(sensorType: string | undefined, zoneCode: string | undefined, startDate: string, endDate: string) {
  return api.get('/report/history/overview', { params: { sensorType, zoneCode, startDate, endDate } })
}

// Report: Templates & records
export function fetchReportTemplates(templateType?: string) {
  return api.get('/report/templates', { params: { templateType } })
}

export function generateReport(payload: {
  templateCode: string, startDate: string, endDate: string, zoneCode?: string, fileFormat?: string, generatedBy?: string
}) {
  return api.post('/report/generate', payload)
}

export function fetchReportRecords(reportType?: string, status?: number, startDate?: string, endDate?: string) {
  return api.get('/report/records', { params: { reportType, status, startDate, endDate } })
}

export function fetchReportRecord(reportNo: string) {
  return api.get(`/report/records/${encodeURIComponent(reportNo)}`)
}

export function sendReportEmail(reportId: number, recipients: string) {
  return api.post(`/report/records/${reportId}/email`, { recipients })
}

// Trend analysis
export function fetchTrendAlerts(sensorType?: string, zoneCode?: string, status?: number) {
  return api.get('/report/trend-alerts', { params: { sensorType, zoneCode, status } })
}

export function acknowledgeTrendAlert(alertNo: string, acknowledgedBy: string) {
  return api.put(`/report/trend-alerts/${encodeURIComponent(alertNo)}/acknowledge`, { acknowledgedBy })
}

export function fetchTrendRules() {
  return api.get('/report/trend-rules')
}

export function saveTrendRule(payload: any) {
  return api.post('/report/trend-rules', payload)
}

export function runTrendCheck() {
  return api.post('/report/trend-check/run')
}

// Sensors
export function fetchSensors(params?: any) {
  return api.get('/sensor-device/sensors', { params })
}

export default api
