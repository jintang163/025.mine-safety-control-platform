<script setup lang="ts">
import { ref, computed } from 'vue'
import DashboardPage from './pages/DashboardPage.vue'
import HistoryAnalysisPage from './pages/HistoryAnalysisPage.vue'
import ReportManagementPage from './pages/ReportManagementPage.vue'
import TrendAlertPage from './pages/TrendAlertPage.vue'

type TabKey = 'dashboard' | 'history' | 'report' | 'trend'

const currentTab = ref<TabKey>('dashboard')

const tabs = [
  { key: 'dashboard' as TabKey, name: '态势感知大屏', icon: '📊' },
  { key: 'history' as TabKey, name: '历史数据分析', icon: '📈' },
  { key: 'report' as TabKey, name: '报表管理', icon: '📋' },
  { key: 'trend' as TabKey, name: '趋势预警', icon: '⚠️' }
]

const now = ref(new Date())
setInterval(() => { now.value = new Date() }, 1000)

const datetime = computed(() => {
  const d = now.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})
</script>

<template>
  <div class="app-root">
    <nav class="app-nav">
      <div class="nav-left">
        <div class="logo">⛏️</div>
        <div class="title-group">
          <h1 class="nav-title">煤矿安全风险态势感知平台</h1>
          <span class="nav-sub">Mine Safety Risk Control Platform</span>
        </div>
      </div>
      <div class="nav-tabs">
        <button v-for="t in tabs" :key="t.key"
                :class="['nav-tab', { active: currentTab === t.key }]"
                @click="currentTab = t.key">
          <span class="tab-icon">{{ t.icon }}</span>
          <span class="tab-name">{{ t.name }}</span>
        </button>
      </div>
      <div class="nav-right">
        <span class="nav-time">{{ datetime }}</span>
      </div>
    </nav>

    <main class="app-main">
      <DashboardPage v-if="currentTab === 'dashboard'" />
      <HistoryAnalysisPage v-else-if="currentTab === 'history'" />
      <ReportManagementPage v-else-if="currentTab === 'report'" />
      <TrendAlertPage v-else-if="currentTab === 'trend'" />
    </main>
  </div>
</template>

<style>
html, body, #app { margin: 0; padding: 0; height: 100%; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", "Segoe UI", sans-serif;
  color: #1f2937;
  background: #f3f4f6;
}
* { box-sizing: border-box; }
</style>

<style scoped>
.app-root {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 20px;
  height: 64px;
  background: linear-gradient(90deg, #1e3a8a 0%, #1d4ed8 50%, #2563eb 100%);
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo { font-size: 28px; }
.title-group { display: flex; flex-direction: column; }
.nav-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
}
.nav-sub {
  font-size: 11px;
  opacity: 0.8;
  letter-spacing: 0.5px;
}

.nav-tabs {
  display: flex;
  gap: 4px;
  margin-left: 10px;
  flex: 1;
}
.nav-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  color: #dbeafe;
  border: none;
  padding: 8px 16px;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.nav-tab:hover {
  background: rgba(255,255,255,0.1);
  color: #ffffff;
}
.nav-tab.active {
  background: #f3f4f6;
  color: #1e3a8a;
  font-weight: 600;
}
.tab-icon { font-size: 16px; }

.nav-right {
  display: flex;
  align-items: center;
}
.nav-time {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  padding: 6px 12px;
  background: rgba(0,0,0,0.2);
  border-radius: 4px;
}

.app-main {
  flex: 1;
  overflow: auto;
}
</style>
