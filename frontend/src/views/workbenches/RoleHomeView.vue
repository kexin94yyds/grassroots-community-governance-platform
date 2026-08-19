<template>
  <RoleWorkbench
    :role="roleKey"
    :title="config.title"
    :description="config.description"
    :kicker="config.kicker"
    :scope-label="summary.scopeLabel"
    :loading="loading"
    :error="error"
    :metrics="summary.metrics"
    :focus-items="summary.focusItems"
    :recent-items="summary.recentItems"
    :actions="config.actions"
    :focus-title="config.focusTitle"
    :recent-title="config.recentTitle"
    :empty-focus-title="config.emptyFocusTitle"
    :empty-focus-description="config.emptyFocusDescription"
    :empty-recent-title="config.emptyRecentTitle"
    :empty-recent-description="config.emptyRecentDescription"
    :updated-at="summary.updatedAt || summary.generatedAt"
    @retry="load"
  />
</template>

<script>
import RoleWorkbench from '../../components/RoleWorkbench.vue'
import {
  getAdminSummary,
  getCommunitySummary,
  getGridSummary,
  getResidentSummary
} from '../../api/workbenches'
import { errorMessage } from '../../utils/data'

const CONFIGS = {
  admin: {
    title: '系统管理员驾驶舱',
    description: '从账号、权限和全局业务健康度进入治理工作，先处理影响全局的风险。',
    kicker: 'ADMIN CONTROL ROOM',
    focusTitle: '全局风险与待办',
    recentTitle: '最近系统活动',
    emptyFocusTitle: '当前没有全局待办',
    emptyFocusDescription: '系统会在注册审核、权限变更或业务异常出现时提示。',
    emptyRecentTitle: '暂无系统活动',
    emptyRecentDescription: '新的审计与配置记录会在这里保留。',
    actions: [
      { to: '/system/users', label: '用户与审核', note: '账号、注册与密码生命周期', icon: 'el-icon-user' },
      { to: '/system/roles', label: '角色授权', note: '维护固定职责边界', icon: 'el-icon-s-custom' },
      { to: '/system/health', label: '系统健康', note: '数据库与业务一致性', icon: 'el-icon-odometer' },
      { to: '/system/operations', label: '管理审计', note: '查看关键操作留痕', icon: 'el-icon-document' }
    ]
  },
  community: {
    title: '社区工作人员治理中心',
    description: '把所属社区的事件、服务申请和巡查计划排成今天可执行的顺序。',
    kicker: 'COMMUNITY DESK',
    focusTitle: '今日待办',
    recentTitle: '社区最近动态',
    emptyFocusTitle: '今日待办已清空',
    emptyFocusDescription: '新的事件、申请或复核事项进入社区范围后会显示。',
    emptyRecentTitle: '暂无社区动态',
    emptyRecentDescription: '社区范围内的最新流转记录会在这里出现。',
    actions: [
      { to: '/community/todo', label: '待办队列', note: '按优先级处理今日事项', icon: 'el-icon-s-order' },
      { to: '/community/service', label: '社区服务', note: '受理与办结居民申请', icon: 'el-icon-service' },
      { to: '/community/patrol', label: '巡查计划', note: '安排责任网格巡查', icon: 'el-icon-map-location' },
      { to: '/community/report', label: '社区报表', note: '查看范围内质量指标', icon: 'el-icon-data-analysis' }
    ]
  },
  grid: {
    title: '网格员现场执行台',
    description: '只看本人责任范围和执行记录，接单、巡查、上报和提交结果都从这里开始。',
    kicker: 'FIELD WORKBENCH',
    focusTitle: '我的执行队列',
    recentTitle: '我的工作记录',
    emptyFocusTitle: '当前没有待执行任务',
    emptyFocusDescription: '新的巡查计划或事件处置任务派发后会显示。',
    emptyRecentTitle: '暂无工作记录',
    emptyRecentDescription: '接单、上报和提交结果后会形成可追溯记录。',
    actions: [
      { to: '/grid/patrol', label: '我的巡查', note: '查看计划与执行状态', icon: 'el-icon-map-location' },
      { to: '/grid/event-report', label: '现场上报', note: '记录责任网格问题', icon: 'el-icon-warning-outline' },
      { to: '/grid/tasks', label: '任务执行', note: '接单并提交处置结果', icon: 'el-icon-finished' },
      { to: '/grid/map', label: '责任地图', note: '查看真实坐标与网格信息', icon: 'el-icon-place' }
    ]
  },
  resident: {
    title: '居民服务中心',
    description: '查看本人档案、提交服务申请、跟踪事项进度，并及时回应社区通知。',
    kicker: 'RESIDENT SERVICE',
    focusTitle: '我的进行中事项',
    recentTitle: '最近服务记录',
    emptyFocusTitle: '当前没有进行中事项',
    emptyFocusDescription: '提交事项或服务申请后，处理进度会显示在这里。',
    emptyRecentTitle: '暂无服务记录',
    emptyRecentDescription: '完成的服务和本人评价会在这里保留。',
    actions: [
      { to: '/resident/report', label: '上报事项', note: '向所属网格提交问题', icon: 'el-icon-edit-outline' },
      { to: '/resident/service', label: '申请服务', note: '选择社区服务目录', icon: 'el-icon-service' },
      { to: '/resident/events', label: '我的事项', note: '查看处理进度与附件', icon: 'el-icon-tickets' },
      { to: '/resident/profile', label: '我的档案', note: '查看已核验身份信息', icon: 'el-icon-user' },
      { to: '/resident/ratings', label: '服务评价', note: '为已办结服务留下反馈', icon: 'el-icon-star-on' }
    ]
  }
}

const SUMMARY_LOADERS = {
  admin: getAdminSummary,
  community: getCommunitySummary,
  grid: getGridSummary,
  resident: getResidentSummary
}

function normalizeSummary(summary) {
  const value = summary || {}
  return {
    ...value,
    metrics: value.metrics && typeof value.metrics === 'object' ? value.metrics : [],
    focusItems: Array.isArray(value.focusItems) ? value.focusItems : [],
    recentItems: Array.isArray(value.recentItems) ? value.recentItems : []
  }
}

export default {
  name: 'RoleHomeView',
  components: { RoleWorkbench },
  data() {
    return { loading: false, error: '', summary: {} }
  },
  computed: {
    roleKey() {
      const name = this.$route.name || ''
      if (name === 'community-home') return 'community'
      if (name === 'grid-home') return 'grid'
      if (name === 'resident-home') return 'resident'
      return 'admin'
    },
    config() {
      return CONFIGS[this.roleKey]
    }
  },
  watch: {
    roleKey() {
      this.load()
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
        const loader = SUMMARY_LOADERS[this.roleKey]
        this.summary = normalizeSummary(await loader())
      } catch (error) {
        this.summary = normalizeSummary({})
        this.error = errorMessage(error)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
