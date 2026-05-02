import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DataLineageCard from '@/components/DataLineageCard.vue'
import CaliberExplanation from '@/components/CaliberExplanation.vue'
import RiskWarning from '@/components/RiskWarning.vue'
import type { DataLineage, RiskLevel } from '@/types/api'

describe('DataLineageCard', () => {
  it('renders data lineage information', () => {
    const lineage: DataLineage = {
      queryId: 'Q-202605030001',
      dataSourceName: '12345投诉工单库',
      caliber: '按受理时间统计，剔除重复件',
      dataUpdatedAt: '2026-05-03T08:00:00Z',
      permissionStatus: '授权',
      rowCount: 87
    }
    const wrapper = mount(DataLineageCard, { props: { lineage } })

    expect(wrapper.find('.data-lineage-card__title').text()).toBe('数据溯源')
    expect(wrapper.find('.data-lineage-card__query-id').text()).toContain('Q-202605030001')
    expect(wrapper.find('.data-lineage-card__value').first().text()).toBe('12345投诉工单库')
  })

  it('formats date time correctly', () => {
    const lineage: DataLineage = {
      queryId: 'Q-001',
      dataSourceName: '测试数据源',
      caliber: '测试口径',
      dataUpdatedAt: '2026-05-03T08:00:00Z',
      permissionStatus: '授权'
    }
    const wrapper = mount(DataLineageCard, { props: { lineage } })
    expect(wrapper.html()).toContain('2026年')
  })

  it('displays row count when provided', () => {
    const lineage: DataLineage = {
      queryId: 'Q-001',
      dataSourceName: '测试',
      caliber: '测试',
      dataUpdatedAt: '2026-05-03',
      rowCount: 100
    }
    const wrapper = mount(DataLineageCard, { props: { lineage } })
    expect(wrapper.text()).toContain('100 条')
  })
})

describe('CaliberExplanation', () => {
  it('renders text content', () => {
    const wrapper = mount(CaliberExplanation, {
      props: {
        text: '按案件受理时间统计，剔除系统标记重复件'
      }
    })
    expect(wrapper.find('.caliber-explanation__text').text()).toBe('按案件受理时间统计，剔除系统标记重复件')
  })

  it('renders slot content', () => {
    const wrapper = mount(CaliberExplanation, {
      slots: { default: '<p>自定义口径内容</p>' }
    })
    expect(wrapper.find('.caliber-explanation__content').html()).toContain('自定义口径内容')
  })

  it('displays meta information', () => {
    const wrapper = mount(CaliberExplanation, {
      props: {
        dataSource: '12345投诉库',
        updatedAt: '2026-05-03',
        scope: '柯桥区'
      }
    })
    expect(wrapper.find('.caliber-explanation__meta').first().text()).toContain('12345投诉库')
  })
})

describe('RiskWarning', () => {
  it('renders LOW risk level', () => {
    const wrapper = mount(RiskWarning, {
      props: {
        riskLevel: 'LOW' as RiskLevel,
        message: '低风险提示'
      }
    })
    expect(wrapper.find('.risk-warning__title').text()).toBe('低风险')
    expect(wrapper.find('.risk-warning--LOW').exists()).toBe(true)
  })

  it('renders HIGH risk level', () => {
    const wrapper = mount(RiskWarning, {
      props: {
        riskLevel: 'HIGH' as RiskLevel,
        message: '高风险提示',
        riskCategories: ['执法认定', '行政处罚']
      }
    })
    expect(wrapper.find('.risk-warning__title').text()).toBe('高风险')
    expect(wrapper.find('.risk-warning--HIGH').exists()).toBe(true)
    expect(wrapper.findAll('.risk-warning__category').length).toBe(2)
  })

  it('shows review badge when requiresReview is true', () => {
    const wrapper = mount(RiskWarning, {
      props: {
        riskLevel: 'HIGH' as RiskLevel,
        requiresReview: true
      }
    })
    expect(wrapper.find('.risk-warning__badge').text()).toBe('待审核')
  })

  it('displays review status', () => {
    const wrapper = mount(RiskWarning, {
      props: {
        riskLevel: 'MEDIUM' as RiskLevel,
        reviewStatus: 'APPROVED'
      }
    })
    expect(wrapper.find('.risk-warning__review-status').text()).toContain('已通过')
  })
})