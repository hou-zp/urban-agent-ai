import type { MessageView } from '@/types/api'

export const QUESTION_TYPE_OPTIONS = [
  { label: '业务咨询', prompt: '请帮我梳理当前业务办理流程' },
  { label: '法律法规咨询', prompt: '请帮我检索相关法律法规并解释适用要点' },
  { label: '政策解读', prompt: '请帮我解读这项政策的关键影响' },
  { label: '智能问数', prompt: '请帮我统计' },
  { label: '线索处置结果查询', prompt: '请帮我查询线索处置进展' },
] as const

export type QuestionTypeLabel = typeof QUESTION_TYPE_OPTIONS[number]['label']

const QUESTION_TYPE_RULES: Array<{ label: QuestionTypeLabel; pattern: RegExp }> = [
  { label: '智能问数', pattern: /(统计|数据|分析|同比|环比|趋势|图表|排行|排名|数量|占比|预警|峰值|均值|汇总|明细|清单|列表)/ },
  { label: '法律法规咨询', pattern: /(法律|法规|条例|法条|处罚|执法|复议|诉讼|合规|裁量)/ },
  { label: '政策解读', pattern: /(政策|解读|文件|通知|意见|方案|办法|规定|细则)/ },
  { label: '线索处置结果查询', pattern: /(线索|处置|投诉|工单|进度|结果|反馈|核查|办理情况)/ },
]

export function detectQuestionTypes(content: string, options?: { fallbackBusiness?: boolean }): QuestionTypeLabel[] {
  const normalized = content.replace(/\s+/g, '')
  const labels = QUESTION_TYPE_RULES
    .filter((rule) => rule.pattern.test(normalized))
    .map((rule) => rule.label)

  if (!labels.length && options?.fallbackBusiness) {
    return ['业务咨询']
  }

  return Array.from(new Set(labels))
}

export function inferSessionQuestionTypes(messages: MessageView[]): QuestionTypeLabel[] {
  const labels = new Set<QuestionTypeLabel>()

  messages.forEach((message) => {
    if (message.questionTypes?.length) {
      message.questionTypes.forEach((type) => labels.add(type))
      return
    }

    if (message.queryCard) {
      labels.add('智能问数')
      return
    }

    if (message.role !== 'USER') {
      return
    }

    detectQuestionTypes(message.content).forEach((type) => labels.add(type))
  })

  return Array.from(labels)
}

export function normalizeQuestionTypes(content: string, preferredType?: QuestionTypeLabel | null): QuestionTypeLabel[] {
  if (preferredType) {
    return [preferredType]
  }
  return detectQuestionTypes(content, { fallbackBusiness: true })
}
