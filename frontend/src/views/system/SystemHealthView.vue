<template>
  <section>
    <PageHeader title="系统健康" description="检查数据库时间、迁移版本、核心账号和业务数据一致性。" />
    <el-alert v-if="error" class="state-alert" :title="error" type="error" show-icon :closable="false"><el-button slot="default" type="text" @click="load">重新加载</el-button></el-alert>
    <div v-loading="loading" class="health-grid">
      <article v-for="item in checks" :key="item.key" class="health-card" :class="`is-${item.tone || 'neutral'}`">
        <div class="health-card-heading"><span>{{ item.label }}</span><el-tag size="small" effect="plain" :type="item.tone === 'healthy' ? 'success' : item.tone === 'warning' ? 'warning' : 'info'">{{ item.statusLabel }}</el-tag></div>
        <strong>{{ item.value }}</strong>
        <p>{{ item.note }}</p>
      </article>
      <div v-if="!loading && !checks.length" class="resource-empty"><span class="empty-coordinate" aria-hidden="true">—</span><strong>暂无健康数据</strong><p>服务端返回健康检查后会显示。</p></div>
    </div>
    <div class="workspace-panel health-details" v-if="raw">
      <div class="workbench-panel-heading"><div><p class="panel-kicker">CONSISTENCY CHECKS</p><h2>一致性明细</h2></div><span>{{ raw.generatedAt || '服务端实时检查' }}</span></div>
      <dl class="health-detail-list"><div v-for="(value, key) in detailEntries" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div></dl>
    </div>
  </section>
</template>

<script>
import PageHeader from '../../components/PageHeader.vue'
import { getSystemHealth } from '../../api/systemWorkbench'
import { errorMessage } from '../../utils/data'

export default {
  name: 'SystemHealthView',
  components: { PageHeader },
  data() { return { loading: false, error: '', raw: null } },
  computed: {
    checks() {
      const value = this.raw || {}
      const rows = [
        { key: 'databaseTime', label: '数据库时间', value: value.databaseTime, note: '数据库连接返回时间', healthy: Boolean(value.databaseTime) },
        { key: 'flywayVersion', label: '迁移版本', value: value.flywayVersion || value.schemaVersion, note: '当前数据库结构版本', healthy: Boolean(value.flywayVersion || value.schemaVersion) },
        { key: 'accountCount', label: '启用账号', value: value.accountCount, note: '系统账号统计', healthy: true },
        { key: 'businessConsistency', label: '业务一致性', value: value.businessConsistency || value.consistencyLabel, note: '事件、任务、服务申请关联检查', healthy: value.businessConsistency !== 'FAILED' }
      ]
      return rows.filter(item => item.value !== undefined && item.value !== null).map(item => ({ ...item, tone: item.healthy ? 'healthy' : 'warning', statusLabel: item.healthy ? '正常' : '需检查' }))
    },
    detailEntries() {
      const value = this.raw || {}
      return Object.keys(value).filter(key => !['databaseTime', 'flywayVersion', 'schemaVersion', 'accountCount', 'businessConsistency', 'consistencyLabel', 'generatedAt'].includes(key)).reduce((result, key) => { result[key] = typeof value[key] === 'object' ? JSON.stringify(value[key]) : value[key]; return result }, {})
    }
  },
  created() { this.load() },
  methods: {
    async load() { this.loading = true; this.error = ''; try { this.raw = await getSystemHealth() || {} } catch (error) { this.error = errorMessage(error) } finally { this.loading = false } }
  }
}
</script>

<style scoped>
.health-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.health-card { min-height: 140px; padding: 18px; background: var(--surface); border: 1px solid var(--line); border-top: 3px solid var(--line-strong); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.health-card.is-healthy { border-top-color: var(--accent); }
.health-card.is-warning { border-top-color: var(--signal); }
.health-card-heading { display: flex; justify-content: space-between; gap: 8px; color: var(--muted-strong); font-size: 12px; font-weight: 800; }
.health-card > strong { display: block; margin: 18px 0 8px; color: var(--ink); font-family: var(--font-utility); font-size: 24px; }
.health-card p { margin: 0; color: var(--muted); font-size: 12px; }
.health-details h2 { margin: 0; font-family: var(--font-display); font-size: 21px; }
.health-detail-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 22px; margin: 0; }
.health-detail-list > div { display: grid; grid-template-columns: 180px minmax(0, 1fr); gap: 12px; padding: 11px 0; border-top: 1px solid var(--line); }
.health-detail-list dt { color: var(--muted); }
.health-detail-list dd { margin: 0; overflow-wrap: anywhere; color: var(--ink); }
@media (max-width: 900px) { .health-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .health-grid, .health-detail-list { grid-template-columns: 1fr; } .health-detail-list > div { grid-template-columns: 1fr; gap: 3px; } }
</style>
