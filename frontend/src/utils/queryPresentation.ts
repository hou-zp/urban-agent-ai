import type { QueryConversationView } from '@/types/api'

export interface QueryChartPoint {
  label: string
  value: number
  percent: number
  color: string
}

export type QueryChartMode = 'bar' | 'line' | 'pie'

export interface QueryChartPayload {
  mode: QueryChartMode | null
  title: string
  caption: string
  dimensionLabel: string
  points: QueryChartPoint[]
  referenceLine?: {
    label: string
    value: number
  }
  ranked: boolean
}

const CHART_COLORS = ['#3aa8ff', '#9bef88', '#6fdad4', '#ffc861', '#8ba7ff', '#ff9f8a']

const FIELD_LABELS: Record<string, string> = {
  avg_concentration: '平均浓度',
  case_status: '案件状态',
  complaint_count: '投诉数量',
  grid_name: '所属网格',
  inspection_date: '巡查日期',
  max_concentration: '最高浓度',
  metric_value: '统计结果',
  problem_count: '问题数量',
  region_code: '所属区域',
  report_date: '上报日期',
  street_name: '所属街道',
  unclosed_count: '未闭环数量',
  warning_count: '预警数量',
  warning_date: '预警日期',
  warning_level: '预警类型',
}

export function buildQueryAnswerText(queryCard: QueryConversationView) {
  if (!queryCard.rows.length) {
    return '当前没有查询到符合条件的数据。\n\n建议补充时间范围、所属街道或预警类型后再查询。'
  }

  const primaryValue = getPrimaryMetricValue(queryCard)
  const metricName = queryCard.metricName || '统计结果'
  if (isScalarQuery(queryCard) && primaryValue !== null) {
    const valueText = formatFriendlyCell(primaryValue, 'metric_value', queryCard)
    if (metricName.includes('未闭环') && metricName.includes('油烟')) {
      return `柯桥区当前未闭环油烟浓度超标预警为 ${valueText}。\n\n建议优先关注持续未处置点位，结合街道和预警类型继续排查。`
    }
    if (metricName.includes('油烟') && metricName.includes('预警')) {
      return `柯桥区当前油烟浓度超标预警为 ${valueText}。\n\n建议继续查看未闭环预警和高发街道，便于安排处置优先级。`
    }
    if (metricName.includes('油烟') && metricName.includes('浓度')) {
      return `柯桥区当前${metricName}为 ${valueText}。\n\n整体浓度变化建议结合历史趋势和未闭环预警一起研判。`
    }
    return `${metricName}为 ${valueText}。\n\n已按您的问题整理为可直接查看的结果。`
  }

  const chartPoints = buildQueryChartPoints(queryCard)
  if (chartPoints.length > 1) {
    const top = chartPoints[0]
    const total = chartPoints.reduce((sum, point) => sum + point.value, 0)
    const topShare = formatPercent(top.value, total)
    const second = chartPoints[1]
    const dimensionName = inferDimensionName(queryCard)
    const summary = `柯桥区本次按${dimensionName}统计${metricName}，合计 ${formatFriendlyNumber(total, queryCard)}。其中${top.label}最多，为 ${formatFriendlyNumber(top.value, queryCard)}，占 ${topShare}。`
    const comparison = second
      ? `${second.label}为 ${formatFriendlyNumber(second.value, queryCard)}，占 ${formatPercent(second.value, total)}，与${top.label}差距较明显。`
      : ''

    if (isOilFumeWarningQuery(queryCard)) {
      return [
        summary,
        ['数据分析：', `1. 主要问题集中在${top.label}，说明当前油烟预警以较高风险或重点类型为主，需要优先处置。`, comparison ? `2. ${comparison}` : '', '3. 建议先核查高占比预警涉及的餐饮点位、设备在线状态和近期处置记录，再安排街道逐项闭环。']
          .filter(Boolean)
          .join('\n'),
      ].join('\n\n')
    }

    return [
      summary,
      ['数据分析：', `1. ${top.label}是当前主要来源，占比最高。`, comparison ? `2. ${comparison}` : '', '3. 建议结合高占比类别继续下钻到街道、网格或具体点位，确定后续处置重点。']
        .filter(Boolean)
        .join('\n'),
    ].join('\n\n')
  }

  return `已为您整理${metricName}，明细如下。`
}

export function isScalarQuery(queryCard: QueryConversationView) {
  if (queryCard.rows.length !== 1) {
    return false
  }
  const keys = Object.keys(queryCard.rows[0] ?? {})
  return keys.length === 1 && normalizeFieldKey(keys[0]) === 'metric_value'
}

export function getPrimaryMetricValue(queryCard: QueryConversationView) {
  const firstRow = queryCard.rows[0]
  if (!firstRow) {
    return null
  }
  const metricKey = Object.keys(firstRow).find((key) => normalizeFieldKey(key) === 'metric_value')
  if (!metricKey) {
    return null
  }
  return firstRow[metricKey] ?? null
}

export function fieldLabel(fieldName: string, queryCard?: QueryConversationView) {
  const normalized = normalizeFieldKey(fieldName)
  if (normalized === 'metric_value' && queryCard?.metricName) {
    return queryCard.metricName
  }
  return FIELD_LABELS[normalized] ?? readableFieldName(fieldName)
}

export function formatFriendlyCell(value: unknown, fieldName: string, queryCard?: QueryConversationView) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'number') {
    const key = normalizeFieldKey(fieldName)
    const unit = inferUnit(queryCard, key)
    if (unit === 'mg/m³') {
      return `${value.toFixed(2).replace(/\.?0+$/u, '')} mg/m³`
    }
    if (Number.isInteger(value)) {
      return unit ? `${value} ${unit}` : String(value)
    }
    return unit ? `${value.toFixed(2)} ${unit}` : value.toFixed(2)
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

export function friendlyQueryColumns(queryCard: QueryConversationView) {
  const keys = Array.from(queryCard.rows.reduce((set, row) => {
    Object.keys(row).forEach((key) => set.add(key))
    return set
  }, new Set<string>()))

  return keys.map((key) => ({
    title: fieldLabel(key, queryCard),
    dataIndex: key,
    key,
    width: normalizeFieldKey(key) === 'metric_value' ? 160 : 132,
  }))
}

export function buildQueryChartPoints(
  queryCard: QueryConversationView,
  options: { sort?: boolean; limit?: number } = {},
): QueryChartPoint[] {
  const {
    sort = true,
    limit,
  } = options
  if (queryCard.rows.length < 2) {
    return []
  }

  const rows = queryCard.rows
  const firstRow = rows[0]
  const keys = Object.keys(firstRow ?? {})
  const metricKey = keys.find((key) => normalizeFieldKey(key) === 'metric_value')
    ?? keys.find((key) => typeof firstRow?.[key] === 'number')
  const labelKey = keys.find((key) => key !== metricKey && typeof firstRow?.[key] !== 'number')
    ?? keys.find((key) => key !== metricKey)

  if (!metricKey || !labelKey) {
    return []
  }

  const values = rows
    .map((row, index) => {
      const numericValue = Number(row[metricKey])
      if (!Number.isFinite(numericValue)) {
        return null
      }
      return {
        label: String(row[labelKey] ?? `第${index + 1}项`),
        value: numericValue,
      }
    })
    .filter((item): item is { label: string; value: number } => Boolean(item))

  const normalizedValues = sort
    ? [...values].sort((left, right) => right.value - left.value)
    : values

  const totalValue = normalizedValues.reduce((sum, item) => sum + item.value, 0)
  const points = normalizedValues.map((item, index) => ({
    ...item,
    percent: totalValue > 0 ? Number(((item.value / totalValue) * 100).toFixed(1)) : 0,
    color: CHART_COLORS[index % CHART_COLORS.length],
  }))

  return typeof limit === 'number' ? points.slice(0, limit) : points
}

export function buildQueryChartPayload(queryCard: QueryConversationView): QueryChartPayload {
  const mode = inferQueryChartMode(queryCard)
  const points = buildQueryChartPoints(queryCard, {
    sort: mode !== 'line',
    limit: 6,
  })
  const metricTitle = queryCard.metricName || '统计结果'
  const dimensionLabel = inferDimensionName(queryCard)
  const question = queryCard.question ?? ''
  const rankingIntent = /排行|排名/u.test(question)
  const oilFumeConcentrationTrend = mode === 'line' && metricTitle.includes('油烟') && metricTitle.includes('浓度')

  return {
    mode,
    title: rankingIntent
      ? `${metricTitle}排行`
      : mode === 'line'
        ? oilFumeConcentrationTrend ? '油烟浓度变化趋势' : `${metricTitle}趋势`
        : mode === 'pie'
          ? `${metricTitle}分布`
          : `${metricTitle}对比`,
    caption: points.length ? `${dimensionLabel} · 共 ${points.length} 项` : '暂无可视化数据',
    dimensionLabel,
    points,
    referenceLine: oilFumeConcentrationTrend
      ? {
          label: '超标阈值',
          value: 2,
        }
      : undefined,
    ranked: rankingIntent,
  }
}

export function prefersChartOnly(queryCard: QueryConversationView) {
  const question = queryCard.question ?? ''
  return /饼图|图表|画图|生成图|可视化|折线图|柱状图|趋势图/u.test(question)
}

export function inferQueryChartMode(queryCard: QueryConversationView): QueryChartMode | null {
  const points = buildQueryChartPoints(queryCard)
  if (points.length <= 1) {
    return null
  }

  const firstRow = queryCard.rows[0]
  const firstDimensionKey = Object.keys(firstRow ?? {}).find((key) => normalizeFieldKey(key) !== 'metric_value')
  const normalizedDimensionKey = normalizeFieldKey(firstDimensionKey ?? '')
  const question = queryCard.question ?? ''

  if (
    normalizedDimensionKey.includes('date')
    || normalizedDimensionKey.includes('day')
    || normalizedDimensionKey.includes('month')
    || normalizedDimensionKey.includes('week')
    || normalizedDimensionKey.includes('time')
    || /趋势|变化|按日|按月|按周|近\d+天/u.test(question)
  ) {
    return 'line'
  }

  if (/排行|排名/u.test(question)) {
    return 'bar'
  }

  if (
    normalizedDimensionKey === 'warning_level'
    || /饼图|占比|构成|分布/u.test(question)
    || points.length <= 5
  ) {
    return 'pie'
  }

  return 'bar'
}

export function formatFriendlyNumber(value: number, queryCard: QueryConversationView) {
  return formatFriendlyCell(value, 'metric_value', queryCard)
}

export function inferUnit(queryCard?: QueryConversationView, fieldName = '') {
  const key = normalizeFieldKey(fieldName)
  const metricName = queryCard?.metricName ?? ''
  if (key.includes('concentration') || metricName.includes('浓度')) {
    return 'mg/m³'
  }
  if (key.includes('count') || metricName.includes('数量') || metricName.includes('预警') || metricName.includes('投诉') || metricName.includes('案件')) {
    return metricName.includes('投诉') || metricName.includes('案件') ? '件' : '起'
  }
  return ''
}

export function inferDimensionName(queryCard: QueryConversationView) {
  const firstRow = queryCard.rows[0]
  if (!firstRow) {
    return '维度'
  }
  const dimensionKey = Object.keys(firstRow).find((key) => normalizeFieldKey(key) !== 'metric_value')
  return dimensionKey ? fieldLabel(dimensionKey, queryCard) : '维度'
}

export function businessWarnings(queryCard: QueryConversationView) {
  return queryCard.warnings
    .filter((warning) => warning && !warning.includes('SQL') && !warning.includes('授权'))
    .map((warning) => warning
      .replace('问题未明确时间范围，已默认最近7天。', '未指定时间范围，已按最近7天统计。')
      .replace('未明确变化周期，已按最近7天与更早历史留存监测数据进行比较。', '未指定对比周期，已按最近7天与历史留存均值比较。'))
}

function isOilFumeWarningQuery(queryCard: QueryConversationView) {
  const metricName = queryCard.metricName ?? ''
  return metricName.includes('油烟') && metricName.includes('预警')
}

function formatPercent(value: number, total: number) {
  if (!total) {
    return '0%'
  }
  return `${((value / total) * 100).toFixed(1).replace(/\.0$/u, '')}%`
}

function normalizeFieldKey(fieldName: string) {
  return fieldName.trim().toLowerCase()
}

function readableFieldName(fieldName: string) {
  return fieldName
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.replace(/^\w/u, (letter) => letter.toUpperCase()))
    .join(' ')
}
