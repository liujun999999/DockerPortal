<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { formatBytes } from '@/utils/format'

const props = withDefaults(defineProps<{
  rxData: number[]
  txData: number[]
  height?: number
  title?: string
}>(), {
  height: 200,
  title: '网络 IO',
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
  const labels = props.rxData.map((_, i) => `${i * 5}s`)
  chart.setOption({
    title: { text: props.title, textStyle: { fontSize: 14 } },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const idx = params[0].dataIndex
        return `${labels[idx]}<br/>接收: ${formatBytes(props.rxData[idx])}<br/>发送: ${formatBytes(props.txData[idx])}`
      },
    },
    legend: { data: ['接收', '发送'], bottom: 0 },
    grid: { left: 60, right: 20, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: labels, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: (v: number) => formatBytes(v) } },
    series: [
      {
        name: '接收', type: 'line', data: props.rxData, smooth: true,
        lineStyle: { color: '#409EFF', width: 2 },
        areaStyle: { color: '#409EFF20' },
        itemStyle: { color: '#409EFF' }, showSymbol: false,
      },
      {
        name: '发送', type: 'line', data: props.txData, smooth: true,
        lineStyle: { color: '#F56C6C', width: 2 },
        areaStyle: { color: '#F56C6C20' },
        itemStyle: { color: '#F56C6C' }, showSymbol: false,
      },
    ],
  })
}

watch(() => [props.rxData, props.txData], updateChart, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', () => chart?.resize())
})
</script>
