<template>
  <section class="point-map-card">
    <div class="point-map-card__header">
      <div>
        <strong>点位地图预留</strong>
        <span>当前演示环境未接入 GIS 底图，先用点位画布承载结果来源和落点关系。</span>
      </div>
      <a-tag color="blue">Record {{ shortId(recordQueryId) }}</a-tag>
    </div>

    <div class="point-map-card__canvas">
      <div class="point-map-card__grid" />
      <button
        v-for="point in points"
        :key="point.id"
        type="button"
        class="point-map-card__marker"
        :style="{ left: `${point.x}%`, top: `${point.y}%` }"
        :title="`${point.title}｜${point.subtitle}`"
        :data-source-ref="point.sourceRef"
      >
        <span />
      </button>
      <div class="point-map-card__hint">
        <span>所有点位均绑定来源</span>
        <strong>{{ recordQueryId }}</strong>
      </div>
    </div>

    <div class="point-map-card__legend">
      <article
        v-for="point in points"
        :key="`${point.id}-legend`"
        class="point-map-card__legend-item"
      >
        <div class="point-map-card__legend-dot" />
        <div class="point-map-card__legend-copy">
          <strong>{{ point.title }}</strong>
          <span>{{ point.subtitle }}</span>
          <em>{{ point.sourceRefType }} · {{ shortId(point.sourceRef) }}</em>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface PointPreviewItem {
  id: string
  title: string
  subtitle: string
  sourceRefType: 'recordQueryId' | 'queryId'
  sourceRef: string
  x: number
  y: number
}

const props = defineProps<{
  recordQueryId: string
  rows: Array<Record<string, unknown>>
}>()

const points = computed<PointPreviewItem[]>(() => {
  return props.rows
    .slice(0, 12)
    .map((row, index) => toPointPreview(row, index, props.recordQueryId))
})

function toPointPreview(row: Record<string, unknown>, index: number, recordQueryId: string): PointPreviewItem {
  const title = readValue(row, ['UNIT_CODE', 'POINT_NAME', 'UNIT_NAME', 'STREET_NAME']) || `点位 ${index + 1}`
  const street = readValue(row, ['STREET_NAME', 'REGION_CODE']) || '未标注区域'
  const status = readValue(row, ['ONLINE_STATUS', 'ISSUE_FOUND', 'LATEST_STATUS']) || '状态待补充'
  const key = `${title}-${street}-${status}-${index}`
  const hash = hashCode(key)
  return {
    id: key,
    title,
    subtitle: `${street}｜${normalizeStatus(status)}`,
    sourceRefType: 'recordQueryId',
    sourceRef: recordQueryId,
    x: 12 + (hash % 76),
    y: 18 + (Math.floor(hash / 97) % 62),
  }
}

function readValue(row: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = row[key]
    if (value !== null && value !== undefined && String(value).trim()) {
      return String(value)
    }
  }
  return ''
}

function normalizeStatus(value: string) {
  if (value === 'true') {
    return '发现异常'
  }
  if (value === 'false') {
    return '巡检正常'
  }
  return value
}

function hashCode(value: string) {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  }
  return hash
}

function shortId(value: string) {
  return value.length > 8 ? value.slice(0, 8) : value
}
</script>

<style scoped>
.point-map-card {
  display: grid;
  gap: 12px;
}

.point-map-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.point-map-card__header strong {
  display: block;
  font-size: 14px;
  color: #172b4d;
}

.point-map-card__header span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #6b7785;
}

.point-map-card__canvas {
  position: relative;
  min-height: 280px;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(239, 246, 255, 0.92) 0%, rgba(248, 250, 252, 0.94) 100%);
  overflow: hidden;
}

.point-map-card__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(148, 163, 184, 0.14) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(148, 163, 184, 0.14) 1px, transparent 1px);
  background-size: 48px 48px;
}

.point-map-card__marker {
  position: absolute;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  transform: translate(-50%, -50%);
  cursor: pointer;
}

.point-map-card__marker span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  background: radial-gradient(circle at 35% 35%, #93c5fd 0%, #2563eb 72%);
  box-shadow: 0 0 0 6px rgba(37, 99, 235, 0.12);
}

.point-map-card__hint {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  color: #526176;
}

.point-map-card__hint strong {
  color: #1d4ed8;
  word-break: break-all;
}

.point-map-card__legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.point-map-card__legend-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e7edf4;
  border-radius: 8px;
  background: #ffffff;
}

.point-map-card__legend-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 999px;
  background: #2563eb;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
}

.point-map-card__legend-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.point-map-card__legend-copy strong,
.point-map-card__legend-copy span,
.point-map-card__legend-copy em {
  word-break: break-word;
}

.point-map-card__legend-copy strong {
  font-size: 13px;
  color: #172b4d;
}

.point-map-card__legend-copy span,
.point-map-card__legend-copy em {
  font-size: 12px;
  line-height: 1.5;
  color: #6b7785;
  font-style: normal;
}

@media (max-width: 768px) {
  .point-map-card__legend {
    grid-template-columns: minmax(0, 1fr);
  }

  .point-map-card__canvas {
    min-height: 240px;
  }

  .point-map-card__hint {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
