<script setup lang="ts">
import { ref, watch, computed } from 'vue'

const props = defineProps<{ overview: any }>()

const todayAlerts = ref(0)
const confirmed = ref(0)
const unconfirmed = ref(0)

const confirmRate = computed(() => {
  if (todayAlerts.value === 0) return 0
  return Math.round((confirmed.value / todayAlerts.value) * 100)
})

const barColor = computed(() => {
  const r = confirmRate.value
  if (r >= 80) return 'linear-gradient(90deg, #2ed573, #00d4ff)'
  if (r >= 50) return 'linear-gradient(90deg, #ffb800, #2ed573)'
  return 'linear-gradient(90deg, #ff4757, #ffb800)'
})

function updateFromOverview(ov: any) {
  if (!ov) {
    todayAlerts.value = 156
    confirmed.value = 138
    unconfirmed.value = 18
    return
  }
  todayAlerts.value = ov.todayAlerts ?? 0
  confirmed.value = ov.confirmedAlerts ?? 0
  unconfirmed.value = ov.unconfirmedAlerts ?? 0
}

watch(() => props.overview, updateFromOverview, { immediate: true })
</script>

<template>
  <div class="confirm-panel">
    <div class="progress-wrapper">
      <div class="progress-bar">
        <div
          class="progress-fill"
          :style="{ width: confirmRate + '%', background: barColor }"
        ></div>
      </div>
      <div class="rate-label number-display">{{ confirmRate }}%</div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-title">今日报警数</div>
        <div class="number-display stat-num accent">{{ todayAlerts }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">已确认</div>
        <div class="number-display stat-num success">{{ confirmed }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">未确认</div>
        <div class="number-display stat-num danger">{{ unconfirmed }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 20px;
  padding: 8px 0;
}

.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 12px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(0, 212, 255, 0.1);
}

.progress-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.8s ease, background 0.8s ease;
  box-shadow: 0 0 10px rgba(0, 212, 255, 0.3);
}

.rate-label {
  font-size: 18px;
  color: var(--accent);
  min-width: 52px;
  text-align: right;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}

.stat-card {
  text-align: center;
  padding: 10px 4px;
  background: rgba(0, 212, 255, 0.04);
  border-radius: 6px;
  border: 1px solid rgba(0, 212, 255, 0.08);
}

.stat-title {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.stat-num {
  font-size: 22px;
}

.stat-num.accent { color: var(--accent); }
.stat-num.success { color: var(--success); }
.stat-num.danger { color: var(--danger); }
</style>
