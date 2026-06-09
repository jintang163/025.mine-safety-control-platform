import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
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

export default api
