<template>
  <section class="insight-panel" :aria-label="title" aria-live="polite">
    <div class="insight-heading">
      <div>
        <p class="insight-kicker">REAL-TIME SCOPE</p>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
      </div>
      <span class="insight-scope">按当前账号权限汇总</span>
    </div>

    <el-alert
      v-if="error"
      class="state-alert"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    >
      <el-button slot="default" type="text" @click="$emit('retry')">重新加载</el-button>
    </el-alert>

    <div v-else v-loading="loading">
      <div class="insight-metrics">
        <article
          v-for="metric in metrics"
          :key="metric.key || metric.label"
          class="insight-metric"
          :class="metric.tone ? `is-${metric.tone}` : ''"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ displayNumber(metric.value) }}</strong>
          <small v-if="metric.note">{{ metric.note }}</small>
        </article>
      </div>

      <button
        v-if="groups.length && isCompact"
        class="insight-details-toggle"
        type="button"
        :aria-expanded="groupsExpanded ? 'true' : 'false'"
        :aria-controls="groupsId"
        @click="groupsExpanded = !groupsExpanded"
      >
        <span>{{ groupsExpanded ? '收起分布详情' : '展开分布详情' }}</span>
        <i :class="groupsExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" aria-hidden="true" />
      </button>

      <transition name="insight-details">
        <div
          v-if="groups.length && (!isCompact || groupsExpanded)"
          :id="groupsId"
          class="insight-groups"
        >
        <section v-for="group in groups" :key="group.key || group.title" class="insight-group">
          <h4>{{ group.title }}</h4>
          <div v-if="group.items && group.items.length" class="distribution-list">
            <div v-for="item in group.items" :key="item.key" class="distribution-row">
              <div class="distribution-copy">
                <span>{{ item.label || item.key }}</span>
                <strong>{{ displayNumber(item.count) }}</strong>
              </div>
              <span class="distribution-track" aria-hidden="true">
                <span :style="{ width: `${percentage(item.count, group.items)}%` }" />
              </span>
            </div>
          </div>
          <p v-else class="insight-empty">暂无可汇总数据</p>
        </section>
        </div>
      </transition>

      <slot />
    </div>
  </section>
</template>

<script>
export default {
  name: 'InsightOverview',
  props: {
    title: { type: String, required: true },
    description: { type: String, required: true },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
    metrics: { type: Array, default: () => [] },
    groups: { type: Array, default: () => [] }
  },
  data() {
    const isCompact = typeof window !== 'undefined' && window.innerWidth <= 560
    return {
      isCompact,
      groupsExpanded: !isCompact,
      groupsId: `insight-groups-${this._uid}`
    }
  },
  mounted() {
    window.addEventListener('resize', this.updateViewport)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateViewport)
  },
  methods: {
    updateViewport() {
      const isCompact = window.innerWidth <= 560
      if (isCompact === this.isCompact) return
      this.isCompact = isCompact
      this.groupsExpanded = !isCompact
    },
    displayNumber(value) {
      const numeric = Number(value)
      return Number.isFinite(numeric) ? numeric.toLocaleString('zh-CN') : '—'
    },
    percentage(value, items) {
      const count = Number(value) || 0
      if (count <= 0) return 0
      const total = (items || []).reduce((sum, item) => sum + (Number(item.count) || 0), 0)
      if (!total) return 0
      return Math.max(3, Math.round((count / total) * 100))
    }
  }
}
</script>
