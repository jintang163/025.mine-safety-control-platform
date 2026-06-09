<script setup lang="ts">
import { ref, computed } from 'vue'
import { useDashboard } from '@/composables/useDashboard'
import AlertTrendChart from '@/components/AlertTrendChart.vue'
import AlertTypeChart from '@/components/AlertTypeChart.vue'
import DeviceStatusChart from '@/components/DeviceStatusChart.vue'
import ConfirmRatePanel from '@/components/ConfirmRatePanel.vue'
import MineMap from '@/components/MineMap.vue'
import TunnelDrilldown from '@/components/TunnelDrilldown.vue'

const {
  overview,
  alertTrend,
  alertTypeDistribution,
  deviceStatus,
  alertData,
  connected
} = useDashboard()

const selectedTunnel = ref<string | null>(null)

const now = ref(new Date())
setInterval(() => { now.value = new Date() }, 1000)

const datetime = computed(() => {
  const d = now.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

const deviceSummary = computed(() => {
  if (!overview.value) return { online: 0, offline: 0, fault: 0 }
  return {
    online: overview.value.onlineDevices ?? 0,
    offline: overview.value.offlineDevices ?? 0,
    fault: overview.value.faultDevices ?? 0
  }
})

const tickerAlerts = computed(() => {
  if (alertData.value.length === 0) {
    return '系统运行正常，暂无报警信息'
  }
  return alertData.value
    .slice(0, 10)
    .map((a: any) => `[${a.time ?? '--:--'}] ${a.tunnel ?? '未知巷道'} - ${a.type ?? '报警'}: ${a.message ?? ''}`)
    .join('    ///    ')
})

function onSelectTunnel(tunnel: string) {
  selectedTunnel.value = tunnel
}

function closeDrilldown() {
  selectedTunnel.value = null
}
</script>

<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <div class="header-left">
        <div class="header-decoration"></div>
        <h1 class="header-title">煤矿安全风险态势感知平台</h1>
      </div>
      <div class="header-center">
        <span class="number-display datetime">{{ datetime }}</span>
      </div>
      <div class="header-right">
        <div class="device-summary">
          <span class="device-dot online"></span>
          <span>在线 <strong class="number-display">{{ deviceSummary.online }}</strong></span>
          <span class="device-dot offline"></span>
          <span>离线 <strong class="number-display">{{ deviceSummary.offline }}</strong></span>
          <span class="device-dot fault"></span>
          <span>故障 <strong class="number-display">{{ deviceSummary.fault }}</strong></span>
        </div>
        <span class="ws-status" :class="{ connected }">
          {{ connected ? 'WS' : '--' }}
        </span>
      </div>
    </header>

    <aside class="panel-left">
      <div class="panel-card chart-panel">
        <div class="panel-title">报警趋势 (24h)</div>
        <AlertTrendChart :data="alertTrend" />
      </div>
      <div class="panel-card chart-panel">
        <div class="panel-title">报警类型分布</div>
        <AlertTypeChart :data="alertTypeDistribution" />
      </div>
    </aside>

    <main class="map-container">
      <MineMap @select-tunnel="onSelectTunnel" />
    </main>

    <aside class="panel-right">
      <div class="panel-card chart-panel">
        <div class="panel-title">设备在线状态</div>
        <DeviceStatusChart :data="deviceStatus" />
      </div>
      <div class="panel-card chart-panel">
        <div class="panel-title">报警确认率</div>
        <ConfirmRatePanel :overview="overview" />
      </div>
    </aside>

    <footer class="alert-ticker">
      <div class="ticker-label">实时报警</div>
      <div class="ticker-content">
        <div class="ticker-scroll">
          <span class="ticker-text">{{ tickerAlerts }}</span>
          <span class="ticker-text">{{ tickerAlerts }}</span>
        </div>
      </div>
    </footer>

    <TunnelDrilldown
      v-if="selectedTunnel"
      :tunnel="selectedTunnel"
      @close="closeDrilldown"
    />
  </div>
</template>

<style scoped>
.dashboard {
  width: 1920px;
  height: 1080px;
  display: grid;
  grid-template-columns: 320px 1fr 320px;
  grid-template-rows: 60px 1fr 48px;
  grid-template-areas:
    "header header header"
    "left   map    right"
    "footer footer footer";
  gap: 0;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(0, 212, 255, 0.05) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 50%, rgba(0, 212, 255, 0.03) 0%, transparent 50%),
    var(--bg-primary);
  position: relative;
  overflow: hidden;
}

.dashboard-header {
  grid-area: header;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: linear-gradient(180deg, rgba(10, 30, 60, 0.95) 0%, rgba(10, 22, 40, 0.8) 100%);
  border-bottom: 1px solid var(--border-glow);
  position: relative;
}

.dashboard-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-decoration {
  width: 4px;
  height: 28px;
  background: linear-gradient(180deg, var(--accent), transparent);
  border-radius: 2px;
}

.header-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 6px;
  background: linear-gradient(90deg, var(--accent), #fff, var(--accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.header-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.datetime {
  font-size: 16px;
  color: var(--accent);
  letter-spacing: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.device-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.device-summary strong {
  color: var(--text-primary);
  margin-right: 4px;
}

.device-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-left: 8px;
}

.device-dot.online { background: var(--success); box-shadow: 0 0 6px rgba(46, 213, 115, 0.5); }
.device-dot.offline { background: #666; }
.device-dot.fault { background: var(--danger); box-shadow: 0 0 6px rgba(255, 71, 87, 0.5); }

.ws-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(255, 71, 87, 0.2);
  color: var(--danger);
  border: 1px solid rgba(255, 71, 87, 0.3);
  font-family: 'Orbitron', monospace;
}

.ws-status.connected {
  background: rgba(46, 213, 115, 0.2);
  color: var(--success);
  border-color: rgba(46, 213, 115, 0.3);
}

.panel-left {
  grid-area: left;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.panel-right {
  grid-area: right;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.chart-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
  animation: fade-in 0.6s ease-out;
}

.panel-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--accent);
  margin-bottom: 8px;
  padding-left: 10px;
  border-left: 3px solid var(--accent);
  letter-spacing: 2px;
}

.map-container {
  grid-area: map;
  padding: 8px;
  display: flex;
}

.alert-ticker {
  grid-area: footer;
  display: flex;
  align-items: center;
  background: rgba(10, 22, 40, 0.9);
  border-top: 1px solid var(--border-glow);
  overflow: hidden;
}

.ticker-label {
  flex-shrink: 0;
  padding: 0 16px;
  font-size: 12px;
  font-weight: 700;
  color: var(--danger);
  letter-spacing: 2px;
  border-right: 1px solid var(--border-glow);
  height: 100%;
  display: flex;
  align-items: center;
  background: rgba(255, 71, 87, 0.1);
  animation: pulse-red 2s infinite;
}

.ticker-content {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.ticker-scroll {
  display: flex;
  white-space: nowrap;
  animation: ticker-scroll 30s linear infinite;
}

.ticker-text {
  font-size: 13px;
  color: var(--text-secondary);
  padding: 0 40px;
  line-height: 48px;
}
</style>
