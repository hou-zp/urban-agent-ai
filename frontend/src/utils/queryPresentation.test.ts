import { describe, expect, it } from 'vitest'
import type { QueryConversationView } from '@/types/api'
import {
  buildQueryChartPayload,
  buildQueryChartPoints,
  inferQueryChartMode,
} from '@/utils/queryPresentation'

function createQueryCard(overrides: Partial<QueryConversationView> = {}): QueryConversationView {
  return {
    question: '查看各街道油烟预警数量',
    metricCode: 'warn_count',
    metricName: '油烟预警数量',
    scopeSummary: '柯桥区 最近7天',
    resultSummary: '共 23 起',
    permissionRewrite: '按柯桥区权限过滤',
    warnings: [],
    rowCount: 3,
    executedAt: '2026-05-02T20:00:00',
    rows: [
      { street_name: '柯桥街道', metric_value: 12 },
      { street_name: '钱清街道', metric_value: 8 },
      { street_name: '华舍街道', metric_value: 3 },
    ],
    ...overrides,
  }
}

describe('queryPresentation chart payload', () => {
  it('只根据后端返回行生成图表点位', () => {
    const queryCard = createQueryCard()

    const payload = buildQueryChartPayload(queryCard)

    expect(payload.mode).toBe('pie')
    expect(payload.title).toBe('油烟预警数量分布')
    expect(payload.caption).toBe('所属街道 · 共 3 项')
    expect(payload.points).toEqual([
      { label: '柯桥街道', value: 12, percent: 52.2, color: '#3aa8ff' },
      { label: '钱清街道', value: 8, percent: 34.8, color: '#9bef88' },
      { label: '华舍街道', value: 3, percent: 13, color: '#6fdad4' },
    ])
  })

  it('排行问题优先使用条形图而不是饼图', () => {
    const queryCard = createQueryCard({
      question: '请根据法规说明本周柯桥区投诉数量排行，并给出处置建议',
      metricName: '投诉数量',
    })

    const payload = buildQueryChartPayload(queryCard)

    expect(inferQueryChartMode(queryCard)).toBe('bar')
    expect(payload.title).toBe('投诉数量排行')
    expect(payload.ranked).toBe(true)
  })

  it('分布问题稳定使用分布图策略', () => {
    const queryCard = createQueryCard({
      question: '本周柯桥区投诉数量分布',
      metricName: '投诉数量',
    })

    const payload = buildQueryChartPayload(queryCard)

    expect(inferQueryChartMode(queryCard)).toBe('pie')
    expect(payload.title).toBe('投诉数量分布')
    expect(payload.ranked).toBe(false)
  })

  it('单行总数不误画分布图', () => {
    const queryCard = createQueryCard({
      question: '本周柯桥区投诉数量',
      metricName: '投诉数量',
      rowCount: 1,
      rows: [{ metric_value: 23 }],
    })

    const payload = buildQueryChartPayload(queryCard)

    expect(inferQueryChartMode(queryCard)).toBeNull()
    expect(payload.mode).toBeNull()
    expect(payload.points).toEqual([])
  })

  it('趋势图保持后端返回顺序，不在前端重排时间轴', () => {
    const queryCard = createQueryCard({
      question: '近3天油烟预警趋势',
      rows: [
        { report_date: '2026-04-30', metric_value: 7 },
        { report_date: '2026-05-01', metric_value: 5 },
        { report_date: '2026-05-02', metric_value: 9 },
      ],
    })

    expect(inferQueryChartMode(queryCard)).toBe('line')
    expect(buildQueryChartPoints(queryCard, { sort: false })).toEqual([
      { label: '2026-04-30', value: 7, percent: 33.3, color: '#3aa8ff' },
      { label: '2026-05-01', value: 5, percent: 23.8, color: '#9bef88' },
      { label: '2026-05-02', value: 9, percent: 42.9, color: '#6fdad4' },
    ])
  })

  it('后端没有有效数值时返回空图表负载，不前端补值', () => {
    const queryCard = createQueryCard({
      rows: [{ street_name: '柯桥街道', metric_value: '无效值' }],
      rowCount: 1,
    })

    expect(buildQueryChartPayload(queryCard)).toEqual({
      mode: null,
      title: '油烟预警数量对比',
      caption: '暂无可视化数据',
      dimensionLabel: '所属街道',
      points: [],
      referenceLine: undefined,
      ranked: false,
    })
  })

  it('油烟浓度趋势图增加超标阈值参考线', () => {
    const queryCard = createQueryCard({
      question: '请问柯桥区当前的油烟浓度超标阀值是多少，与以前相比有什么变化',
      metricName: '油烟平均浓度',
      rows: [
        { warning_date: '2026-04-30', metric_value: 2.12 },
        { warning_date: '2026-05-01', metric_value: 2.32 },
        { warning_date: '2026-05-02', metric_value: 2.04 },
      ],
    })

    const payload = buildQueryChartPayload(queryCard)

    expect(payload.mode).toBe('line')
    expect(payload.title).toBe('油烟浓度变化趋势')
    expect(payload.referenceLine).toEqual({
      label: '超标阈值',
      value: 2,
    })
  })
})
