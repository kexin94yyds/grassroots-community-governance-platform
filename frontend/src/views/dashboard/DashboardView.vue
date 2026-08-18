<template>
  <section class="visual-dashboard">
    <el-alert
      v-if="error"
      class="state-alert"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-loading="loading" class="visual-dashboard-canvas">
      <article class="community-hero">
        <img
          class="community-hero-image"
          src="/images/community/shanghai-green-hill.jpg"
          alt="上海杨浦绿丘建筑与周边社区的航拍实景"
        >
        <div class="community-hero-shade" aria-hidden="true" />
        <svg class="community-grid-overlay" viewBox="0 0 720 430" aria-hidden="true">
          <path d="M62 56 258 28 346 142 219 218 38 172Z" />
          <path d="m258 28 262 30 64 147-238-63Z" />
          <path d="m38 172 181 46 127-76 30 222-266 24Z" />
          <path d="m346 142 238 63 76 159-284 0Z" />
          <circle cx="346" cy="142" r="8" />
          <circle cx="219" cy="218" r="5" />
        </svg>

        <div class="community-hero-copy">
          <p class="hero-eyebrow"><span /> 治理态势 · 社区 / 网格 / 事项</p>
          <h1>看见社区，<br>才能治理社区</h1>
          <p class="hero-description">把责任范围、居民底册与事项进度放回真实空间中，快速判断今天最需要关注的地方。</p>
          <div class="hero-actions">
            <router-link class="hero-primary-action" to="/events">
              查看事件台账 <i class="el-icon-right" />
            </router-link>
            <span><i class="el-icon-location-outline" /> 当前权限范围</span>
          </div>
        </div>

        <div class="hero-scope-card">
          <span class="scope-pulse" aria-hidden="true" />
          <div>
            <small>在管空间</small>
            <strong>{{ displayMetric('gridCount') }} <em>个有效网格</em></strong>
          </div>
          <i class="el-icon-map-location" aria-hidden="true" />
        </div>

        <a
          class="photo-credit hero-credit"
          href="https://unsplash.com/photos/SVjB0bGZ2Ro"
          target="_blank"
          rel="noopener noreferrer"
        >上海杨浦 · 实景素材 / Unsplash</a>
      </article>

      <div class="overview-metrics" aria-label="核心治理指标">
        <article
          v-for="metric in metrics"
          :key="metric.key"
          class="overview-metric"
          :class="{ 'is-urgent': metric.tone === 'urgent' }"
        >
          <span class="metric-symbol" aria-hidden="true"><i :class="metric.icon" /></span>
          <div class="metric-copy">
            <p>{{ metric.label }}</p>
            <strong>{{ displayMetric(metric.key) }}</strong>
            <small>{{ metric.note }}</small>
          </div>
          <span class="metric-code">{{ metric.code }}</span>
        </article>
      </div>

      <div class="dashboard-visual-grid">
        <article class="workspace-panel event-pulse-panel">
          <div class="visual-panel-heading">
            <div>
              <p class="panel-kicker">LIVE FLOW</p>
              <h2>事项处置脉搏</h2>
              <p>从居民上报到办结，以当前实时数据呈现处置节奏。</p>
            </div>
            <div class="closure-stamp">
              <small>闭环率</small>
              <strong>{{ closureRate }}%</strong>
            </div>
          </div>

          <div class="flow-distribution" role="img" :aria-label="flowAriaLabel">
            <span
              v-for="item in activeEventSummary"
              :key="item.key"
              :class="`is-${item.tone}`"
              :style="{ width: `${statusShare(item.key)}%` }"
            />
          </div>

          <div class="flow-status-list" role="list" aria-label="事件处置状态">
            <div v-for="item in eventSummary" :key="item.key" role="listitem">
              <span class="status-mark" :class="`is-${item.tone}`" aria-hidden="true" />
              <p><strong>{{ item.label }}</strong><small>{{ item.note }}</small></p>
              <b>{{ displayMetric(item.key) }}</b>
            </div>
          </div>
        </article>

        <article class="field-window-panel">
          <div class="visual-panel-heading field-heading">
            <div>
              <p class="panel-kicker">FIELD NOTES</p>
              <h2>社区现场</h2>
              <p>空间底图之外，也保留街巷与人的真实尺度。</p>
            </div>
            <span class="field-date">今日观察</span>
          </div>

          <div class="field-photo-grid">
            <figure class="field-photo is-alley">
              <img src="/images/community/chinese-neighborhood-alley.jpg" alt="中国传统社区街巷实景">
              <figcaption>
                <span><i class="el-icon-place" /> 街巷巡查</span>
                <strong>公共空间与设施</strong>
                <a href="https://unsplash.com/photos/iYoGRWBVCO4" target="_blank" rel="noopener noreferrer">tommao wang / Unsplash</a>
              </figcaption>
            </figure>
            <figure class="field-photo is-neighbors">
              <img src="/images/community/neighborhood-seniors.jpg" alt="社区长者在公共交通站点交流的实景">
              <figcaption>
                <span><i class="el-icon-user" /> 邻里走访</span>
                <strong>倾听居民日常需要</strong>
                <a href="https://unsplash.com/photos/9kvipo9pi7s" target="_blank" rel="noopener noreferrer">Centre for Ageing Better / Unsplash</a>
              </figcaption>
            </figure>
          </div>
        </article>
      </div>

      <section class="dashboard-analysis-grid" aria-label="治理质量与近期事项">
        <article class="dashboard-analysis-panel">
          <div class="visual-panel-heading compact-heading">
            <div>
              <p class="panel-kicker">GRID QUALITY</p>
              <h2>网格事件与按期办结</h2>
              <p>按当前数据范围汇总每个网格的事项数量和有期限任务的办结质量。</p>
            </div>
          </div>
          <div v-if="gridEventStats.length" class="grid-quality-list">
            <article v-for="item in gridEventStats" :key="item.gridId" class="grid-quality-item">
              <div class="grid-quality-heading">
                <div>
                  <strong>{{ item.gridName || item.gridCode || '未命名网格' }}</strong>
                  <small>{{ item.gridCode || item.gridId }}</small>
                </div>
                <b>{{ number(item.eventCount) }} 件</b>
              </div>
              <div class="rate-track" :aria-label="`${item.gridName || item.gridCode} 按期办结率 ${formatPercent(item.onTimeCompletionRate)}`">
                <span :style="{ width: `${rateWidth(item.onTimeCompletionRate)}%` }" />
              </div>
              <p>
                <span>按期办结 {{ number(item.onTimeClosedCount) }}/{{ number(item.completedWithDeadlineCount) }}</span>
                <strong>{{ formatPercent(item.onTimeCompletionRate) }}</strong>
              </p>
            </article>
          </div>
          <div v-else class="dashboard-empty">暂无可统计的网格事件数据</div>
        </article>

        <article class="dashboard-analysis-panel">
          <div class="visual-panel-heading compact-heading">
            <div>
              <p class="panel-kicker">CATEGORY MIX</p>
              <h2>事件类别占比</h2>
              <p>识别当前辖区内集中出现的治理问题类型。</p>
            </div>
          </div>
          <div v-if="categoryStats.length" class="category-distribution-list">
            <article v-for="item in categoryStats" :key="item.categoryId" class="category-distribution-item">
              <div>
                <strong>{{ item.categoryName || '未分类' }}</strong>
                <span>{{ number(item.eventCount) }} 件 · {{ formatPercent(item.percentage) }}</span>
              </div>
              <div class="category-track" :aria-label="`${item.categoryName || '未分类'}占比 ${formatPercent(item.percentage)}`">
                <span :style="{ width: `${rateWidth(item.percentage)}%` }" />
              </div>
            </article>
          </div>
          <div v-else class="dashboard-empty">暂无事件类别统计数据</div>
        </article>

        <article class="dashboard-analysis-panel recent-event-panel">
          <div class="visual-panel-heading compact-heading">
            <div>
              <p class="panel-kicker">RECENT EVENTS</p>
              <h2>最近上报事项</h2>
              <p>优先关注新近进入治理流程的事项。</p>
            </div>
            <router-link to="/events">查看台账<i class="el-icon-right" /></router-link>
          </div>
          <ol v-if="recentEvents.length" class="recent-event-list">
            <li v-for="item in recentEvents" :key="item.id">
              <div>
                <span>{{ item.eventNo }}</span>
                <el-tag size="mini" effect="plain" :type="statusType(item.status)">
                  {{ eventStatus[item.status] || item.status || '未知状态' }}
                </el-tag>
              </div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.categoryName || '未分类' }} · {{ item.gridName || '未分配网格' }} · {{ formatDate(item.reportedAt) }}</p>
            </li>
          </ol>
          <div v-else class="dashboard-empty">暂无最近事件记录</div>
        </article>
      </section>

      <section class="dashboard-entry-section" aria-labelledby="entry-title">
        <div class="entry-section-heading">
          <div>
            <p class="panel-kicker">WORK ENTRIES</p>
            <h2 id="entry-title">从态势进入工作</h2>
          </div>
          <p>每个入口只展示当前账号获授权的业务区域。</p>
        </div>
        <div class="dashboard-entry-grid">
          <router-link
            v-for="action in visibleQuickActions"
            :key="action.to"
            class="dashboard-entry"
            :to="action.to"
          >
            <span class="entry-icon" aria-hidden="true"><i :class="action.icon" /></span>
            <span class="entry-copy"><strong>{{ action.label }}</strong><small>{{ action.note }}</small></span>
            <i class="el-icon-right entry-arrow" aria-hidden="true" />
          </router-link>
        </div>
      </section>
    </div>
  </section>
</template>

<script>
import { getOverview } from '../../api/dashboard'
import { EVENT_STATUS, STATUS_TAG_TYPE } from '../../constants/domain'
import { errorMessage, formatDateTime } from '../../utils/data'

export default {
  name: 'DashboardView',
  data() {
    return {
      loading: false,
      error: '',
      overview: {},
      eventStatus: EVENT_STATUS,
      metrics: [
        { key: 'gridCount', code: 'GRID', label: '有效网格', note: '当前责任范围', icon: 'el-icon-map-location' },
        { key: 'residentCount', code: 'PEOPLE', label: '居民档案', note: '在册居民', icon: 'el-icon-user' },
        { key: 'keyPopulationCount', code: 'CARE', label: '重点关怀', note: '需持续关注', icon: 'el-icon-collection-tag' },
        { key: 'pendingEventCount', code: 'ACTION', label: '待受理事件', note: '需要及时分流', icon: 'el-icon-bell', tone: 'urgent' }
      ],
      eventSummary: [
        { key: 'pendingEventCount', label: '待受理', note: '等待社区确认', tone: 'reported' },
        { key: 'processingEventCount', label: '处理中', note: '已进入责任网格', tone: 'processing' },
        { key: 'pendingReviewEventCount', label: '待复核', note: '等待结果确认', tone: 'review' },
        { key: 'closedEventCount', label: '已办结', note: '处置闭环留痕', tone: 'closed' }
      ],
      quickActions: [
        { to: '/grids', permission: 'grid:read', label: '网格区域', note: '查看空间责任与网格员', icon: 'el-icon-map-location' },
        { to: '/residents', permission: 'resident:read', label: '居民档案', note: '查看家庭与重点关怀', icon: 'el-icon-user' },
        { to: '/events', permission: 'event:read', label: '治理事件', note: '受理、派发与跟踪处置', icon: 'el-icon-warning-outline' },
        { to: '/tasks', permission: 'task:read', label: '网格任务', note: '接单、执行与结果复核', icon: 'el-icon-finished' }
      ]
    }
  },
  computed: {
    statusTotal() {
      return this.eventSummary.reduce((total, item) => total + Number(this.overview[item.key] || 0), 0)
    },
    closureRate() {
      if (!this.statusTotal) return 0
      return Math.round((Number(this.overview.closedEventCount || 0) / this.statusTotal) * 100)
    },
    activeEventSummary() {
      if (!this.statusTotal) return this.eventSummary
      return this.eventSummary.filter(item => Number(this.overview[item.key] || 0) > 0)
    },
    flowAriaLabel() {
      return this.eventSummary.map(item => `${item.label}${this.displayMetric(item.key)}件`).join('，')
    },
    visibleQuickActions() {
      return this.quickActions.filter(action => this.$store.getters['session/hasPermission'](action.permission))
    },
    gridEventStats() {
      return Array.isArray(this.overview.gridEventStats) ? this.overview.gridEventStats : []
    },
    categoryStats() {
      return Array.isArray(this.overview.categoryStats) ? this.overview.categoryStats : []
    },
    recentEvents() {
      return Array.isArray(this.overview.recentEvents) ? this.overview.recentEvents : []
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        this.overview = (await getOverview()) || {}
      } catch (error) {
        this.error = errorMessage(error)
      } finally {
        this.loading = false
      }
    },
    displayMetric(key) {
      const value = this.overview[key]
      return value === undefined || value === null ? '-' : value
    },
    statusShare(key) {
      if (!this.statusTotal) return 25
      return (Number(this.overview[key] || 0) / this.statusTotal) * 100
    },
    number(value) {
      const normalized = Number(value)
      return Number.isFinite(normalized) ? normalized : 0
    },
    rateWidth(value) {
      return Math.min(100, Math.max(0, this.number(value)))
    },
    formatPercent(value) {
      return `${Math.round(this.rateWidth(value))}%`
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '暂无时间'
    },
    statusType(status) {
      return STATUS_TAG_TYPE[status] || 'info'
    }
  }
}
</script>

<style scoped>
.dashboard-analysis-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 22px; }
.dashboard-analysis-panel { min-width: 0; padding: 24px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.compact-heading { margin-bottom: 20px; }
.compact-heading h2 { font-size: 22px; }
.compact-heading > a { display: inline-flex; align-items: center; gap: 3px; color: var(--accent); font-size: 13px; font-weight: 700; white-space: nowrap; }
.grid-quality-list, .category-distribution-list, .recent-event-list { display: grid; gap: 14px; margin: 0; padding: 0; list-style: none; }
.grid-quality-item, .category-distribution-item { padding: 14px 0 0; border-top: 1px solid var(--line); }
.grid-quality-item:first-child, .category-distribution-item:first-child { padding-top: 0; border-top: 0; }
.grid-quality-heading, .grid-quality-item > p, .category-distribution-item > div:first-child { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.grid-quality-heading strong, .category-distribution-item strong { display: block; color: var(--ink); font-size: 14px; }
.grid-quality-heading small { display: block; margin-top: 3px; color: var(--muted); font-family: var(--font-utility); font-size: 11px; }
.grid-quality-heading b { color: var(--accent); font-family: var(--font-utility); font-size: 13px; white-space: nowrap; }
.rate-track, .category-track { height: 7px; margin: 11px 0 9px; overflow: hidden; background: #e8eee9; border-radius: 999px; }
.rate-track span, .category-track span { display: block; height: 100%; min-width: 0; background: var(--accent); border-radius: inherit; transition: width 180ms ease; }
.category-track span { background: var(--signal); }
.grid-quality-item > p, .category-distribution-item span { margin: 0; color: var(--muted); font-size: 12px; }
.grid-quality-item > p strong { color: var(--accent-strong); font-family: var(--font-utility); }
.recent-event-panel { display: flex; flex-direction: column; }
.recent-event-list { max-height: 314px; overflow: auto; }
.recent-event-list li { padding: 13px 0; border-top: 1px solid var(--line); }
.recent-event-list li:first-child { padding-top: 0; border-top: 0; }
.recent-event-list li > div { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.recent-event-list li > div > span { color: var(--accent); font-family: var(--font-utility); font-size: 11px; }
.recent-event-list li > strong { display: block; margin: 8px 0 5px; font-size: 14px; line-height: 1.45; }
.recent-event-list li > p { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.55; }
.dashboard-empty { display: grid; min-height: 145px; place-items: center; color: var(--muted); font-size: 13px; text-align: center; }
@media (max-width: 1180px) { .dashboard-analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .recent-event-panel { grid-column: 1 / -1; } }
@media (max-width: 700px) { .dashboard-analysis-grid { grid-template-columns: 1fr; gap: 14px; } .dashboard-analysis-panel { padding: 19px; } .recent-event-panel { grid-column: auto; } }
@media (prefers-reduced-motion: reduce) { .rate-track span, .category-track span { transition: none; } }
</style>
