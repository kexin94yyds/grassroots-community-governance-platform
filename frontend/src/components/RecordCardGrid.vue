<template>
  <div>
    <div v-if="items.length" class="record-card-grid">
      <article v-for="item in items" :key="item.id" class="record-card">
        <header class="record-card-header">
          <div>
            <p class="record-code">{{ display(item[eyebrowProp]) }}</p>
            <h3>{{ display(item[titleProp]) }}</h3>
          </div>
          <el-tag
            v-if="statusProp && item[statusProp]"
            size="small"
            :type="tagType(item[statusProp])"
            effect="plain"
          >
            {{ statusLabels[item[statusProp]] || item[statusProp] }}
          </el-tag>
        </header>
        <div class="record-card-body">
          <slot :item="item" />
        </div>
        <footer v-if="$scopedSlots.actions" class="record-card-actions">
          <slot name="actions" :item="item" />
        </footer>
      </article>
    </div>

    <div v-else class="resource-empty">
      <span class="empty-coordinate" aria-hidden="true">0</span>
      <strong>{{ emptyTitle }}</strong>
      <p>{{ emptyDescription }}</p>
    </div>
  </div>
</template>

<script>
import { STATUS_TAG_TYPE } from '../constants/domain'

export default {
  name: 'RecordCardGrid',
  props: {
    items: { type: Array, default: () => [] },
    titleProp: { type: String, required: true },
    eyebrowProp: { type: String, required: true },
    statusProp: { type: String, default: 'status' },
    statusLabels: { type: Object, default: () => ({}) },
    emptyTitle: { type: String, default: '暂无符合条件的数据' },
    emptyDescription: { type: String, default: '可以调整筛选条件后重新查询。' }
  },
  methods: {
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    tagType(status) {
      return STATUS_TAG_TYPE[status] || 'info'
    }
  }
}
</script>
