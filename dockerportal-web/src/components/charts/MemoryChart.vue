<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { formatBytes } from '@/utils/format'

const props = withDefaults(defineProps<{
  usageData: number[]
  limitData: number[]
  height?: number
  title?: string
  color?: string
}>(), {
  height: 200,
  title: '内存使用',
  color: '#67C23A',
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
  const labels = props.usageData.map((_, i) => `${i * 5}s`)
  chart.setOption({
    title: { text: props.title, textStyle: { fontSize: 14 } },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const idx = params[0].dataIndex
        return `${labels[idx]}<br/>使用: ${formatBytes(props.usageData[idx])}<br/>限制: ${formatBytes(props.limitData[idx])}`
      },
    },
    legend: { data: ['使用', '限制'], bottom: 0 },
    grid: { left: 60, right: 20, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: (v: number) => formatBytes(v) } },
    series: [
      {
        name: '使用', type: 'line', data: props.usageData, smooth: true,
        lineStyle: { color: props.color, width: 2 },
        areaStyle: { color: props.color + '30' },
        itemStyle: { color: props.color }, showSymbol: false,
      },
      {
        name: '限制', type: 'line', data: props.limitData, smooth: true,
        lineStyle: { color: '#E6A23C', width: 1, type: 'dashed' },
        itemStyle: { color: '#E6A23C' }, showSymbol: false,
      },
    ],
  })
}

watch(() => [props.usageData, props.limitData], updateChart, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', () => chart?.resize())
})
</script>
