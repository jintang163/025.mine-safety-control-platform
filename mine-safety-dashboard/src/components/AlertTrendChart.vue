<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{ data: any }>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)

function getOption(data: any) {
  const values = data?.values ?? Array(24).fill(0).map(() => Math.floor(Math.random() * 15))

  return {
    backgroundColor: 'transparent',
    grid: { top: 20, right: 16, bottom: 24, left: 36 },
    xAxis: {
      type: 'category',
      data: hours,
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
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#00d4ff', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,212,255,0.35)' },
          { offset: 1, color: 'rgba(0,212,255,0.02)' }
        ])
      }
    }],
    animation: true,
    animationDuration: 800,
    animationEasing: 'cubicOut'
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
