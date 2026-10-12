<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(defineProps<{
  data: number[]
  height?: number
  title?: string
  max?: number
  color?: string
}>(), {
  height: 200,
  title: 'CPU 使用率',
  max: 100,
  color: '#409EFF',
})

const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chart) return
  const labels = props.data.map((_, i) => `${i * 5}s`)
  chart.setOption({
    title: { text: props.title, textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', formatter: '{b}<br/>{c}%' },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value', min: 0, max: props.max, axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'line',
      data: props.data,
      smooth: true,
      lineStyle: { color: props.color, width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: props.color + '40' },
          { offset: 1, color: props.color + '05' },
        ]),
      },
      itemStyle: { color: props.color },
      showSymbol: false,
    }],
  })
}

watch(() => props.data, updateChart, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', () => chart?.resize())
})
</script>
