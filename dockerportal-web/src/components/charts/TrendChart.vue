<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

export interface TrendSeries {
  name: string
  color: string
  data: number[]
}

const props = withDefaults(defineProps<{
  title?: string
  labels: string[]
  series: TrendSeries[]
  height?: number
  unit?: string
}>(), {
  title: '',
  height: 210,
  unit: '',
})

const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null

function fmtBytes(n: number) {
  const units = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  let v = Math.abs(n)
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v >= 100 || i === 0 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`
}

function fmtValue(v: number) {
  if (props.unit === 'B/s') return fmtBytes(v)
  return `${Number(v).toFixed(2)}${props.unit === '%' ? '%' : ' ' + props.unit}`
}

function updateChart() {
  if (!chart) return
  const single = props.series.length === 1
  chart.setOption({
    title: { text: props.title, left: 8, top: 4, textStyle: { fontSize: 13, color: '#303133' } },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const list = Array.isArray(params) ? params : [params]
        const rows = list.map((p: any) =>
          `${p.marker}${p.seriesName}: ${fmtValue(p.value ?? 0)}`)
        return [String(list[0]?.axisValue ?? ''), ...rows].join('<br/>')
      },
    },
    legend: single ? undefined : {
      data: props.series.map(s => s.name), bottom: 0, itemWidth: 14, itemHeight: 8,
      textStyle: { fontSize: 10, color: '#909399' },
    },
    grid: { left: 58, right: 16, top: 36, bottom: single ? 26 : 44 },
    xAxis: {
      type: 'category',
      data: props.labels,
      boundaryGap: false,
      axisLabel: { fontSize: 10, color: '#909399' },
    },
    yAxis: {
      type: 'value',
      min: 0,
      // 速率类数据为整数，避免全 0 时出现 0.25/0.5 被四舍五入成重复刻度
      minInterval: props.unit === 'B/s' ? 1 : undefined,
      axisLabel: {
        fontSize: 10,
        color: '#909399',
        formatter: (v: number) => (props.unit === 'B/s' ? fmtBytes(v) : `${v}${props.unit}`),
      },
    },
    series: props.series.map((s) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      smooth: true,
      showSymbol: false,
      lineStyle: { color: s.color, width: 2 },
      itemStyle: { color: s.color },
      areaStyle: single ? {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: s.color + '40' },
          { offset: 1, color: s.color + '05' },
        ]),
      } : undefined,
    })),
  }, true)
}

function handleResize() {
  chart?.resize()
}

watch([() => props.labels, () => props.series], updateChart, { deep: true })

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    updateChart()
    // tab 刚激活时容器宽度可能尚未稳定（init 出默认窄画布），用 ResizeObserver 跟随真实尺寸重绘
    ro = new ResizeObserver(() => chart?.resize())
    ro.observe(chartRef.value)
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  ro?.disconnect()
  ro = null
  chart?.dispose()
  chart = null
})
</script>
