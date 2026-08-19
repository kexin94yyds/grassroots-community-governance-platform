<template>
  <section>
    <RoleWorkbench
      role="community"
      title="社区治理报表"
      description="按所属社区子网格汇总事件、任务、服务申请和巡查完成质量。"
      kicker="COMMUNITY REPORT"
      :scope-label="summary.scopeLabel"
      :loading="loading"
      :error="error"
      :metrics="summary.metrics"
      :focus-items="summary.reportItems || summary.focusItems"
      :recent-items="summary.recentItems"
      :actions="actions"
      focus-title="需要解释的指标"
      recent-title="近期闭环记录"
      empty-focus-title="暂无报表异常"
      empty-focus-description="服务端聚合出异常指标后会在这里提示。"
      @retry="load"
    />
  </section>
</template>

<script>
import RoleWorkbench from '../../components/RoleWorkbench.vue'
import { getCommunitySummary } from '../../api/workbenches'
import { errorMessage } from '../../utils/data'

export default {
  name: 'CommunityReportView',
  components: { RoleWorkbench },
  data() { return { loading: false, error: '', summary: {}, actions: [{ to: '/events', label: '事件台账', note: '查看范围内事件', icon: 'el-icon-warning-outline' }, { to: '/tasks', label: '任务质量', note: '查看执行闭环', icon: 'el-icon-finished' }, { to: '/community/service', label: '服务申请', note: '查看服务队列', icon: 'el-icon-service' }] } },
  created() { this.load() },
  methods: {
    async load() { this.loading = true; this.error = ''; try { this.summary = await getCommunitySummary() || {} } catch (error) { this.error = errorMessage(error) } finally { this.loading = false } }
  }
}
</script>
