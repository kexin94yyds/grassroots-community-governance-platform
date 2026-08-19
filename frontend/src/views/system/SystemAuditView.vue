<template>
  <section>
    <PageHeader title="管理审计" description="查看事件、任务、敏感访问、公告和服务申请的关键流转留痕，不展示敏感查询值。" />
    <InsightOverview
      title="操作留痕概览"
      description="统计当前系统保存的关键操作类型和最近活动。"
      :loading="loading"
      :error="error"
      :metrics="metrics"
      @retry="load"
    />
    <div class="workspace-panel audit-panel">
      <div class="query-toolbar">
        <el-form class="query-bar" :inline="true" @submit.native.prevent="search">
          <el-form-item label="关键词"><el-input v-model.trim="query.keyword" clearable placeholder="操作人、对象或范围" /></el-form-item>
          <el-form-item label="模块"><el-select v-model="query.module" clearable placeholder="全部模块"><el-option v-for="item in modules" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item><el-button type="primary" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
        </el-form>
      </div>
      <el-alert v-if="error" class="state-alert" :title="error" type="error" show-icon :closable="false"><el-button slot="default" type="text" @click="load">重新加载</el-button></el-alert>
      <el-table v-loading="loading" :data="items" class="resource-table" row-key="id">
        <template slot="empty"><div class="resource-empty"><span class="empty-coordinate" aria-hidden="true">0</span><strong>暂无审计记录</strong><p>关键流转发生后会出现在这里。</p></div></template>
        <el-table-column prop="createdAt" label="时间" min-width="180"><template slot-scope="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="moduleLabel" label="模块" width="130" />
        <el-table-column prop="actionLabel" label="动作" width="150" />
        <el-table-column prop="operatorName" label="操作人" min-width="130" />
        <el-table-column prop="objectLabel" label="对象" min-width="210" />
        <el-table-column prop="scopeLabel" label="范围" min-width="170" />
        <el-table-column prop="resultLabel" label="结果" width="110" />
      </el-table>
      <div class="pagination-row"><span>共 {{ total }} 条</span><el-pagination background layout="prev, pager, next" :current-page="page" :page-size="size" :total="total" @current-change="changePage" /></div>
    </div>
  </section>
</template>

<script>
import PageHeader from '../../components/PageHeader.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import { listSystemOperations } from '../../api/systemWorkbench'
import { errorMessage } from '../../utils/data'

export default {
  name: 'SystemAuditView',
  components: { PageHeader, InsightOverview },
  data() {
    return {
      loading: false,
      error: '',
      items: [],
      total: 0,
      page: 1,
      size: 20,
      query: { keyword: '', module: '' },
      modules: [
        { value: 'EVENT', label: '事件' },
        { value: 'TASK', label: '任务' },
        { value: 'RESIDENT_SENSITIVE', label: '敏感访问' },
        { value: 'ANNOUNCEMENT', label: '公告' },
        { value: 'SERVICE_APPLICATION', label: '服务申请' }
      ]
    }
  },
  computed: {
    metrics() {
      return [
        { key: 'total', label: '记录总数', value: this.total, note: '当前查询范围' },
        { key: 'today', label: '今日操作', value: this.items.filter(item => String(item.createdAt || '').slice(0, 10) === new Date().toISOString().slice(0, 10)).length, note: '本页已加载' },
        { key: 'sensitive', label: '敏感访问', value: this.items.filter(item => item.module === 'RESIDENT_SENSITIVE').length, note: '本页审计记录', tone: 'warning' },
        { key: 'operators', label: '操作人员', value: new Set(this.items.map(item => item.operatorName).filter(Boolean)).size, note: '本页去重人数' }
      ]
    }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const result = await listSystemOperations({ page: this.page, size: this.size, keyword: this.query.keyword || undefined, module: this.query.module || undefined })
        this.items = Array.isArray(result) ? result : (result && result.items) || []
        this.total = Number(result && result.total) || this.items.length
      } catch (error) {
        this.error = errorMessage(error)
      } finally { this.loading = false }
    },
    search() { this.page = 1; return this.load() },
    reset() { this.query = { keyword: '', module: '' }; return this.search() },
    changePage(page) { this.page = page; return this.load() },
    formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
  }
}
</script>

<style scoped>
.audit-panel h2 { margin: 0; font-family: var(--font-display); font-size: 21px; }
.audit-count-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.audit-count-item { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; gap: 11px; align-items: center; padding: 14px 0; border-top: 1px solid var(--line); }
.audit-count-item:nth-child(-n + 2) { border-top: 0; }
.audit-count-icon { display: grid; width: 36px; height: 36px; place-items: center; color: var(--accent-strong); background: var(--accent-soft); border-radius: 4px; }
.audit-count-item strong, .audit-count-item small { display: block; }
.audit-count-item strong { color: var(--ink); font-size: 14px; }
.audit-count-item small { margin-top: 3px; color: var(--muted); font-size: 11px; }
.audit-count-item b { color: var(--accent); font-family: var(--font-utility); font-size: 18px; }
@media (max-width: 560px) { .audit-count-list { grid-template-columns: 1fr; } .audit-count-item:nth-child(2) { border-top: 1px solid var(--line); } }
</style>
