<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ data: any }>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const colors = ['#ff4757', '#ffb800', '#00d4ff', '#2ed573', '#a855f7']

function mapItems(data: any) {
  const raw = Array.isArray(data) ? data : (data?.items ?? [])
  if (raw.length === 0) return []
  return raw.map((d: any) => ({
    name: d.type ?? d.sensorType ?? d.name ?? '未知',
    value: d.count ?? d.value ?? 0
  }))
}

const total = computed(() => {
  const items = mapItems(props.data)
  return items.reduce((s: number, d: any) => s + (d.value ?? 0), 0)
})

function getOption(data: any) {
  const items = mapItems(data)

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10,22,40,0.9)',
      borderColor: 'rgba(0,212,255,0.3)',
      textStyle: { color: '#e8f0fe', fontSize: 12 }
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: 'rgba(232,240,254,0.7)', fontSize: 11 },
      itemGap: 10
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      label: { show: false },
      labelLine: { show: false },
      data: items.map((d: any, i: number) => ({
        ...d,
        itemStyle: { color: colors[i % colors.length] }
      })),
      emphasis: {
        itemStyle: {
          shadowBlur: 20,
          shadowColor: 'rgba(0,212,255,0.4)'
        }
      }
    }],
    graphic: [{
      type: 'text',
      left: '30%',
      top: '42%',
      style: {
        text: String(total.value),
        textAlign: 'center',
        fill: '#00d4ff',
        fontSize: 22,
        fontFamily: 'Orbitron',
        fontWeight: 700
      }
    }, {
      type: 'text',
      left: '30%',
      top: '58%',
      style: {
        text: '报警总数',
        textAlign: 'center',
        fill: 'rgba(232,240,254,0.5)',
        fontSize: 11
      }
    }],
    animation: true,
    animationDuration: 800
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, 'dark')
  chart.setOption(getOption(props.data))
}

watch(() => props.data, (val) => {
  chart?.setOption(getOption(val))
})

onMounted(initChart)

onUnmounted(() => {
  chart?.dispose()
})
</script>

<template>
  <div ref="chartRef" class="chart-container"></div>
</template>

<style scoped>
.chart-container {
  flex: 1;
  min-height: 0;
}
</style>
