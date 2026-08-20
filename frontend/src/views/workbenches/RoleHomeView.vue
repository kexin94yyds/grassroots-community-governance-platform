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
    description: '从账号、权限、网格和治理业务进入全局工作，优先处理影响业务闭环的事项。',
    kicker: 'ADMIN CONTROL ROOM',
    focusTitle: '全局风险与待办',
    recentTitle: '最近治理事件',
    emptyFocusTitle: '当前没有全局待办',
    emptyFocusDescription: '系统会在注册审核、权限变更或业务异常出现时提示。',
    emptyRecentTitle: '暂无系统活动',
    emptyRecentDescription: '新的治理事件和流转记录会在这里保留。',
    actions: [
      { to: '/system/users', label: '用户与审核', note: '账号、注册与密码生命周期', icon: 'el-icon-user' },
      { to: '/system/roles', label: '角色授权', note: '维护固定职责边界', icon: 'el-icon-s-custom' },
      { to: '/dashboard', label: '治理概览', note: '查看网格、居民和事件统计', icon: 'el-icon-data-analysis' },
      { to: '/system/operations', label: '管理审计', note: '查看关键操作留痕', icon: 'el-icon-document' }
    ]
  },
  community: {
    title: '社区工作人员治理中心',
    description: '围绕所属社区的网格、居民、治理事件和处置任务推进业务闭环。',
    kicker: 'COMMUNITY DESK',
    focusTitle: '今日待办',
    recentTitle: '社区最近动态',
    emptyFocusTitle: '今日待办已清空',
    emptyFocusDescription: '新的待受理事件或待复核任务进入社区范围后会显示。',
    emptyRecentTitle: '暂无社区动态',
    emptyRecentDescription: '社区范围内的最新流转记录会在这里出现。',
    actions: [
      { to: '/dashboard', label: '治理概览', note: '查看社区范围统计', icon: 'el-icon-data-analysis' },
      { to: '/grids', label: '网格管理', note: '维护责任区域和网格员', icon: 'el-icon-place' },
      { to: '/events', label: '治理事件', note: '受理、派发和复核事项', icon: 'el-icon-warning-outline' },
      { to: '/tasks', label: '网格任务', note: '跟踪任务处置状态', icon: 'el-icon-finished' }
    ]
  },
  grid: {
    title: '网格员现场执行台',
    description: '只看本人责任范围和执行记录，现场上报、任务接单和提交结果都从这里开始。',
    kicker: 'FIELD WORKBENCH',
    focusTitle: '我的执行队列',
    recentTitle: '我的工作记录',
    emptyFocusTitle: '当前没有待执行任务',
    emptyFocusDescription: '新的事件处置任务派发后会显示。',
    emptyRecentTitle: '暂无工作记录',
    emptyRecentDescription: '接单、上报和提交结果后会形成可追溯记录。',
    actions: [
      { to: '/grid/event-report', label: '现场上报', note: '记录责任网格问题', icon: 'el-icon-warning-outline' },
      { to: '/grid/tasks', label: '任务执行', note: '接单并提交处置结果', icon: 'el-icon-finished' },
      { to: '/grid/map', label: '责任地图', note: '查看真实坐标与网格信息', icon: 'el-icon-place' },
      { to: '/grid/history', label: '工作记录', note: '查看事件与任务历史', icon: 'el-icon-tickets' }
    ]
  },
  resident: {
    title: '居民服务中心',
    description: '查看本人档案、上报治理事项，并跟踪受理、派发、处置和办结进度。',
    kicker: 'RESIDENT SERVICE',
    focusTitle: '我的进行中事项',
    recentTitle: '最近事项记录',
    emptyFocusTitle: '当前没有进行中事项',
    emptyFocusDescription: '提交治理事项后，处理进度会显示在这里。',
    emptyRecentTitle: '暂无事项记录',
    emptyRecentDescription: '本人上报事项及其处理状态会在这里保留。',
    actions: [
      { to: '/resident/report', label: '上报事项', note: '向所属网格提交问题', icon: 'el-icon-edit-outline' },
      { to: '/resident/events', label: '我的事项', note: '查看处理进度与附件', icon: 'el-icon-tickets' },
      { to: '/resident/profile', label: '我的档案', note: '查看并维护允许修改的信息', icon: 'el-icon-user' }
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
