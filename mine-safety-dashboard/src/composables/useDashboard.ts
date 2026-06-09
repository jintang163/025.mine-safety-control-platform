import { ref, onMounted, onUnmounted } from 'vue'
import {
  fetchOverview,
  fetchSensorRealtime,
  fetchAlertTrend,
  fetchAlertTypeDistribution,
  fetchDeviceStatus,
  fetchPersonnelDistribution,
  fetchHeatmap
} from '@/utils/api'
import { useWebSocket } from './useWebSocket'

export function useDashboard() {
  const overview = ref<any>(null)
  const sensors = ref<any[]>([])
  const alertTrend = ref<any>(null)
  const alertTypeDistribution = ref<any>(null)
  const deviceStatus = ref<any>(null)
  const personnelDistribution = ref<any>(null)
  const heatmap = ref<any>(null)

  const { sensorData, alertData, connected, connect, disconnect } = useWebSocket()

  let refreshTimer: ReturnType<typeof setInterval> | null = null

  async function refreshAll() {
    try {
      const [ov, sr, at, atd, ds, pd, hm] = await Promise.allSettled([
        fetchOverview(),
        fetchSensorRealtime(),
        fetchAlertTrend(),
        fetchAlertTypeDistribution(),
        fetchDeviceStatus(),
        fetchPersonnelDistribution(),
        fetchHeatmap()
      ])

      if (ov.status === 'fulfilled') overview.value = ov.value
      if (sr.status === 'fulfilled') sensors.value = sr.value
      if (at.status === 'fulfilled') alertTrend.value = at.value
      if (atd.status === 'fulfilled') alertTypeDistribution.value = atd.value
      if (ds.status === 'fulfilled') deviceStatus.value = ds.value
      if (pd.status === 'fulfilled') personnelDistribution.value = pd.value
      if (hm.status === 'fulfilled') heatmap.value = hm.value
    } catch (e) {
      console.error('[Dashboard] Refresh error', e)
    }
  }

  onMounted(() => {
    refreshAll()
    connect()
    refreshTimer = setInterval(refreshAll, 30000)
  })

  onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
    disconnect()
  })

  return {
    overview,
    sensors,
    alertTrend,
    alertTypeDistribution,
    deviceStatus,
    personnelDistribution,
    heatmap,
    sensorData,
    alertData,
    connected,
    refreshAll
  }
}
