<template>
  <section class="role-workbench" :class="`role-workbench--${role}`" aria-labelledby="workbench-title">
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

    <div v-loading="loading" class="workbench-surface">
      <header class="workbench-heading">
        <div>
          <p class="page-heading-meta" aria-hidden="true">
            <span class="page-kicker">{{ kicker }}</span>
            <span class="page-trace">{{ scopeLabel || '当前权限范围' }}</span>
          </p>
          <h1 id="workbench-title">{{ title }}</h1>
          <p>{{ description }}</p>
        </div>
        <div class="workbench-stamp" aria-label="当前工作范围">
          <span class="workbench-stamp-dot" aria-hidden="true" />
          <strong>{{ scopeLabel || '权限范围内' }}</strong>
          <small>{{ updatedLabel }}</small>
        </div>
      </header>

      <div class="workbench-metrics" aria-label="工作台统计">
        <article
          v-for="metric in normalizedMetrics"
          :key="metric.key || metric.label"
          class="workbench-metric"
          :class="metric.tone ? `is-${metric.tone}` : ''"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ displayMetric(metric.value) }}</strong>
          <small>{{ metric.note || '实时汇总' }}</small>
        </article>
        <div v-if="!normalizedMetrics.length" class="workbench-empty-metric">暂无可汇总指标</div>
      </div>

      <div class="workbench-columns">
        <section class="workbench-panel" aria-labelledby="workbench-focus-title">
          <div class="workbench-panel-heading">
            <div>
              <p class="panel-kicker">FOCUS QUEUE</p>
              <h2 id="workbench-focus-title">{{ focusTitle }}</h2>
            </div>
            <span>{{ normalizedFocusItems.length }} 项</span>
          </div>
          <ol v-if="normalizedFocusItems.length" class="workbench-list">
            <li v-for="(item, index) in normalizedFocusItems" :key="item.id || item.key || index">
              <span class="workbench-list-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
              <div>
                <strong>{{ item.title || item.name || item.label || '待处理事项' }}</strong>
                <small>{{ item.detail || item.description || item.statusLabel || item.status || '需要继续跟进' }}</small>
              </div>
              <router-link v-if="item.route || item.routePath" :to="item.route || item.routePath" class="workbench-list-action">
                查看
              </router-link>
            </li>
          </ol>
          <div v-else class="workbench-empty" role="status">
            <span aria-hidden="true">0</span>
            <strong>{{ emptyFocusTitle }}</strong>
            <p>{{ emptyFocusDescription }}</p>
          </div>
        </section>

        <section class="workbench-panel" aria-labelledby="workbench-recent-title">
          <div class="workbench-panel-heading">
            <div>
              <p class="panel-kicker">RECENT RECORDS</p>
              <h2 id="workbench-recent-title">{{ recentTitle }}</h2>
            </div>
            <span>{{ normalizedRecentItems.length }} 条</span>
          </div>
          <ul v-if="normalizedRecentItems.length" class="workbench-recent-list">
            <li v-for="(item, index) in normalizedRecentItems" :key="item.id || item.key || index">
              <div>
                <span>{{ item.code || item.no || item.number || '记录' }}</span>
                <el-tag v-if="item.statusLabel || item.status" size="mini" effect="plain">
                  {{ item.statusLabel || item.status }}
                </el-tag>
              </div>
              <strong>{{ item.title || item.name || item.label || '未命名记录' }}</strong>
              <p>{{ item.detail || item.description || item.timeLabel || item.occurredAt || item.createdAt || '暂无补充说明' }}</p>
            </li>
          </ul>
          <div v-else class="workbench-empty" role="status">
            <span aria-hidden="true">—</span>
            <strong>{{ emptyRecentTitle }}</strong>
            <p>{{ emptyRecentDescription }}</p>
          </div>
        </section>
      </div>

      <section v-if="actions.length" class="workbench-actions" aria-labelledby="workbench-actions-title">
        <div class="workbench-panel-heading">
          <div>
            <p class="panel-kicker">WORK ENTRIES</p>
            <h2 id="workbench-actions-title">进入工作</h2>
          </div>
        </div>
        <div class="workbench-action-grid">
          <router-link v-for="action in actions" :key="action.to" :to="action.to" class="workbench-action">
            <span class="workbench-action-icon" aria-hidden="true"><i :class="action.icon || 'el-icon-right'" /></span>
            <span><strong>{{ action.label }}</strong><small>{{ action.note }}</small></span>
            <i class="el-icon-right" aria-hidden="true" />
          </router-link>
        </div>
      </section>
    </div>
  </section>
</template>

<script>
const METRIC_LABELS = {
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  totalUsers: '账号总数',
  pendingRegistrations: '待审核注册',
  publishedAnnouncements: '已发布公告',
  openApplications: '进行中服务',
  activePatrolPlans: '进行中巡查',
  openEvents: '进行中事件',
  reportedEvents: '上报事件',
  pendingReviews: '待复核',
  activeResidents: '在册居民',
  pendingEvents: '待受理事件',
  pendingTasks: '待处理任务',
  pendingServices: '待处理服务',
  pendingRatings: '待评价服务',
  visibleAnnouncements: '可见公告',
  pendingAccept: '待接单任务',
  overdue: '逾期任务',
  reportsLast7Days: '近七日上报'
}

export default {
  name: 'RoleWorkbench',
  props: {
    role: { type: String, required: true },
    title: { type: String, required: true },
    description: { type: String, required: true },
    kicker: { type: String, default: 'ROLE WORKBENCH' },
    scopeLabel: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
    metrics: { type: [Array, Object], default: () => [] },
    focusItems: { type: Array, default: () => [] },
    recentItems: { type: Array, default: () => [] },
    actions: { type: Array, default: () => [] },
    focusTitle: { type: String, default: '待处理事项' },
    recentTitle: { type: String, default: '最近记录' },
    emptyFocusTitle: { type: String, default: '当前没有待处理事项' },
    emptyFocusDescription: { type: String, default: '新的事项进入权限范围后会显示在这里。' },
    emptyRecentTitle: { type: String, default: '暂时没有最近记录' },
    emptyRecentDescription: { type: String, default: '有真实记录后会在这里保留时间线。' },
    updatedAt: { type: [String, Number], default: '' }
  },
  computed: {
    normalizedMetrics() {
      if (Array.isArray(this.metrics)) return this.metrics
      return Object.keys(this.metrics || {}).map(key => {
        const value = this.metrics[key]
        if (value && typeof value === 'object') return { key, ...value }
        return { key, label: METRIC_LABELS[key] || key, value }
      })
    },
    normalizedFocusItems() {
      return Array.isArray(this.focusItems) ? this.focusItems : []
    },
    normalizedRecentItems() {
      return Array.isArray(this.recentItems) ? this.recentItems : []
    },
    updatedLabel() {
      if (!this.updatedAt) return '数据由服务端汇总'
      const date = new Date(this.updatedAt)
      return Number.isNaN(date.getTime()) ? String(this.updatedAt) : `更新于 ${date.toLocaleString('zh-CN', { hour12: false })}`
    }
  },
  methods: {
    displayMetric(value) {
      const numeric = Number(value)
      return Number.isFinite(numeric) ? numeric.toLocaleString('zh-CN') : (value || '—')
    }
  }
}
</script>

<style scoped>
.role-workbench { min-width: 0; }
.workbench-surface { display: grid; gap: 18px; }
.workbench-heading { display: flex; gap: 28px; align-items: flex-end; justify-content: space-between; padding: 4px 0 2px; }
.workbench-heading h1 { margin: 0 0 8px; color: var(--ink); font-family: var(--font-display); font-size: clamp(28px, 3vw, 42px); line-height: 1.16; letter-spacing: .02em; }
.workbench-heading p:not(.page-heading-meta) { max-width: 70ch; margin: 0; color: var(--muted); line-height: 1.7; }
.workbench-stamp { display: grid; min-width: 190px; gap: 5px; padding: 14px 16px; color: var(--accent-strong); background: var(--accent-soft); border: 1px solid #cfe0d8; border-radius: var(--radius-control); }
.workbench-stamp-dot { width: 8px; height: 8px; background: var(--signal); border-radius: 50%; box-shadow: 0 0 0 4px rgba(180, 83, 9, .12); }
.workbench-stamp strong { font-size: 13px; }
.workbench-stamp small { color: var(--muted); font-size: 11px; }
.workbench-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.workbench-metric { min-height: 110px; padding: 16px; background: var(--surface); border: 1px solid var(--line); border-top: 3px solid #aebdb5; border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.workbench-metric.is-positive { border-top-color: var(--accent); }
.workbench-metric.is-warning { border-top-color: var(--signal); }
.workbench-metric.is-danger { border-top-color: #a33b32; }
.workbench-metric span, .workbench-metric strong, .workbench-metric small { display: block; }
.workbench-metric span { color: var(--muted-strong); font-size: 12px; font-weight: 800; }
.workbench-metric strong { margin: 9px 0 4px; color: var(--ink); font-family: var(--font-utility); font-size: 27px; line-height: 1; }
.workbench-metric small { color: var(--muted); font-size: 11px; }
.workbench-empty-metric { display: grid; min-height: 110px; grid-column: 1 / -1; place-items: center; color: var(--muted); background: var(--surface); border: 1px dashed var(--line-strong); border-radius: var(--radius-surface); }
.workbench-columns { display: grid; grid-template-columns: minmax(0, 1.12fr) minmax(0, .88fr); gap: 14px; }
.workbench-panel, .workbench-actions { min-width: 0; padding: 22px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.workbench-panel-heading { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.workbench-panel-heading h2 { margin: 0; color: var(--ink); font-family: var(--font-display); font-size: 21px; }
.workbench-panel-heading > span { flex: none; color: var(--muted); font-family: var(--font-utility); font-size: 11px; }
.workbench-list, .workbench-recent-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.workbench-list li, .workbench-recent-list li { min-width: 0; border-top: 1px solid var(--line); }
.workbench-list li { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto; gap: 11px; align-items: center; padding: 14px 0; }
.workbench-list li:first-child, .workbench-recent-list li:first-child { border-top: 0; padding-top: 0; }
.workbench-list-index { color: var(--signal); font-family: var(--font-utility); font-size: 11px; }
.workbench-list strong, .workbench-list small, .workbench-recent-list strong, .workbench-recent-list p { display: block; }
.workbench-list strong { overflow: hidden; color: var(--ink); text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.workbench-list small { overflow: hidden; margin-top: 4px; color: var(--muted); text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.workbench-list-action { color: var(--accent); font-size: 12px; font-weight: 800; }
.workbench-recent-list li { padding: 13px 0; }
.workbench-recent-list li > div { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.workbench-recent-list li > div > span { color: var(--accent); font-family: var(--font-utility); font-size: 11px; }
.workbench-recent-list strong { margin: 7px 0 4px; font-size: 14px; line-height: 1.4; }
.workbench-recent-list p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.55; }
.workbench-empty { display: grid; min-height: 190px; place-items: center; align-content: center; color: var(--muted); text-align: center; }
.workbench-empty > span { display: grid; width: 44px; height: 44px; margin-bottom: 10px; place-items: center; color: var(--accent); background: var(--accent-soft); border: 1px solid #cfe0d8; border-radius: 4px; font-family: var(--font-utility); }
.workbench-empty strong { color: var(--ink); font-size: 14px; }
.workbench-empty p { margin: 5px 0 0; font-size: 12px; }
.workbench-actions { padding-bottom: 16px; }
.workbench-action-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.workbench-action { display: grid; grid-template-columns: 35px minmax(0, 1fr) 15px; gap: 10px; align-items: center; min-width: 0; padding: 13px; color: var(--ink); background: var(--paper); border: 1px solid var(--line); border-radius: 5px; transition: border-color 160ms ease, transform 160ms ease; }
.workbench-action:hover { color: var(--ink); border-color: #a8c5b8; transform: translateY(-1px); }
.workbench-action-icon { display: grid; width: 35px; height: 35px; place-items: center; color: var(--accent-strong); background: var(--accent-soft); border-radius: 4px; }
.workbench-action strong, .workbench-action small { display: block; }
.workbench-action strong { font-size: 13px; }
.workbench-action small { overflow: hidden; margin-top: 3px; color: var(--muted); text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.workbench-action > i { color: var(--accent); }
.role-workbench--resident .workbench-stamp { color: #7c4615; background: var(--signal-soft); border-color: #e7cda8; }
.role-workbench--grid_worker .workbench-stamp { color: var(--accent-strong); background: #e8f1ed; }
@media (max-width: 900px) { .workbench-columns { grid-template-columns: 1fr; } }
@media (max-width: 820px) { .workbench-heading { display: block; } .workbench-stamp { margin-top: 16px; } .workbench-action-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .workbench-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .workbench-panel, .workbench-actions { padding: 17px; } .workbench-action-grid { grid-template-columns: 1fr; } .workbench-list li { grid-template-columns: 25px minmax(0, 1fr); } .workbench-list-action { grid-column: 2; } }
</style>
