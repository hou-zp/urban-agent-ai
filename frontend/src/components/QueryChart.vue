<template>
  <div class="query-chart-card">
    <div class="query-chart-card__header">
      <strong>{{ chartTitle }}</strong>
      <span>{{ chartCaption }}</span>
    </div>
    <div ref="chartRef" class="query-chart-card__canvas" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts, type SetOptionOpts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { QueryConversationView } from '@/types/api'
import {
  buildQueryChartPayload,
  formatFriendlyCell,
} from '@/utils/queryPresentation'

use([
  BarChart,
  LineChart,
  PieChart,
  CanvasRenderer,
  GridComponent,
  LegendComponent,
  TooltipComponent,
])

type ChartOption = ComposeOption<
  GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
>

const props = defineProps<{
  queryCard: QueryConversationView
}>()

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const chartPayload = computed(() => buildQueryChartPayload(props.queryCard))
const chartTitle = computed(() => chartPayload.value.title)
const chartCaption = computed(() => chartPayload.value.caption)
const chartOption = computed<ChartOption | null>(() => {
  if (!chartPayload.value.mode || !chartPayload.value.points.length) {
    return null
  }

  if (chartPayload.value.mode === 'pie') {
    return {
      color: chartPayload.value.points.map((point) => point.color),
      legend: {
        bottom: 0,
        icon: 'circle',
        itemHeight: 8,
        itemWidth: 8,
        textStyle: {
          color: '#526176',
          fontSize: 12,
        },
      },
      series: [
        {
          type: 'pie',
          radius: ['46%', '72%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderColor: '#ffffff',
            borderWidth: 2,
          },
          label: {
            color: '#344054',
            fontSize: 12,
            formatter: ({ name, percent }) => `${name}\n${percent}%`,
          },
          data: chartPayload.value.points.map((point) => ({
            name: point.label,
            value: point.value,
          })),
        },
      ],
      tooltip: {
        trigger: 'item',
        formatter: (params: { name: string; value: number; percent: number }) => {
          return `${params.name}<br/>${formatFriendlyCell(params.value, 'metric_value', props.queryCard)} · ${params.percent}%`
        },
      },
    }
  }

  const isLine = chartPayload.value.mode === 'line'
  const isRankedBar = chartPayload.value.mode === 'bar' && chartPayload.value.ranked
  const referenceLine = chartPayload.value.referenceLine

  return {
    color: ['#3b82f6'],
    grid: {
      top: 18,
      right: 18,
      bottom: 36,
      left: 52,
      containLabel: true,
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: isLine ? 'line' : 'shadow',
      },
      formatter: (params: Array<{ axisValueLabel: string; value: number }>) => {
        const current = params[0]
        if (!current) {
          return ''
        }
        return `${current.axisValueLabel}<br/>${formatFriendlyCell(current.value, 'metric_value', props.queryCard)}`
      },
    },
    xAxis: {
      type: isRankedBar ? 'value' : 'category',
      name: isLine ? chartPayload.value.dimensionLabel : undefined,
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: '#526176',
        fontSize: 12,
        interval: 0,
        formatter: (value: string | number) => {
          if (isRankedBar) {
            return compactNumber(Number(value))
          }
          return formatAxisLabel(String(value), isLine)
        },
      },
      axisLine: {
        lineStyle: {
          color: '#d8e1eb',
        },
      },
      data: isRankedBar ? undefined : chartPayload.value.points.map((point) => point.label),
    },
    yAxis: {
      type: isRankedBar ? 'category' : 'value',
      axisLabel: {
        color: '#526176',
        fontSize: 12,
        formatter: (value: string | number) => {
          if (isRankedBar) {
            return formatAxisLabel(String(value), false)
          }
          return compactNumber(Number(value))
        },
      },
      splitLine: {
        lineStyle: {
          color: '#eef2f6',
        },
      },
      data: isRankedBar ? chartPayload.value.points.map((point) => point.label) : undefined,
    },
    series: [
      {
        type: isLine ? 'line' : 'bar',
        smooth: isLine,
        symbol: isLine ? 'circle' : 'none',
        symbolSize: isLine ? 8 : undefined,
        barMaxWidth: isLine ? undefined : 28,
        itemStyle: isLine
          ? {
              color: '#2563eb',
            }
          : {
              color: '#60a5fa',
              borderRadius: isRankedBar ? [0, 6, 6, 0] : [6, 6, 0, 0],
            },
        areaStyle: isLine
          ? {
              color: 'rgba(59, 130, 246, 0.12)',
            }
          : undefined,
        emphasis: {
          focus: 'series',
        },
        markLine: isLine && referenceLine
          ? {
              symbol: 'none',
              label: {
                formatter: `${referenceLine.label} ${formatFriendlyCell(referenceLine.value, 'metric_value', props.queryCard)}`,
                color: '#b45309',
                fontSize: 12,
              },
              lineStyle: {
                color: '#f59e0b',
                type: 'dashed',
                width: 2,
              },
              data: [
                {
                  yAxis: referenceLine.value,
                },
              ],
            }
          : undefined,
        data: chartPayload.value.points.map((point) => point.value),
      },
    ],
  }
})

onMounted(async () => {
  await nextTick()
  ensureChart()
  renderChart()
  observeResize()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chartInstance?.dispose()
  chartInstance = null
})

watch(chartOption, async () => {
  await nextTick()
  renderChart()
}, { deep: true })

function ensureChart() {
  if (!chartRef.value || chartInstance) {
    return
  }
  chartInstance = init(chartRef.value, undefined, {
    renderer: 'canvas',
  })
}

function renderChart() {
  ensureChart()
  if (!chartInstance || !chartOption.value) {
    chartInstance?.clear()
    return
  }

  const options: SetOptionOpts = {
    notMerge: true,
    lazyUpdate: true,
  }
  chartInstance.setOption(chartOption.value, options)
  chartInstance.resize()
}

function observeResize() {
  if (!chartRef.value || typeof ResizeObserver === 'undefined') {
    return
  }

  resizeObserver = new ResizeObserver(() => {
    chartInstance?.resize()
  })
  resizeObserver.observe(chartRef.value)
}

function truncateLabel(value: string) {
  return value.length > 6 ? `${value.slice(0, 6)}…` : value
}

function formatAxisLabel(value: string, preferDate: boolean) {
  if (preferDate) {
    const dateMatch = /^(\d{4})-(\d{2})-(\d{2})/u.exec(value)
    if (dateMatch) {
      return `${dateMatch[2]}-${dateMatch[3]}`
    }
  }
  return truncateLabel(value)
}

function compactNumber(value: number) {
  if (Math.abs(value) >= 10000) {
    return `${(value / 10000).toFixed(1).replace(/\.0$/u, '')}万`
  }
  return String(value)
}
</script>

<style scoped>
.query-chart-card {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e6eef5;
  border-radius: 8px;
  background: #ffffff;
}

.query-chart-card__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.query-chart-card__header strong {
  font-size: 14px;
  color: #172b4d;
}

.query-chart-card__header span {
  font-size: 12px;
  color: #6b7785;
}

.query-chart-card__canvas {
  width: 100%;
  height: 280px;
}

@media (max-width: 768px) {
  .query-chart-card {
    padding: 12px;
  }

  .query-chart-card__canvas {
    height: 240px;
  }
}
</style>
