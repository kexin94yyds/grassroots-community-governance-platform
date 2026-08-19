<template>
  <section>
    <ResourceListView
      ref="resource"
      :title="pageTitle"
      :description="pageDescription"
      :fetcher="fetchPlans"
      :columns="columns"
      :status-options="statuses"
      search-label="巡查"
      search-placeholder="计划编号、标题或网格"
      :manage-permission="canWrite ? 'patrol:plan:write' : ''"
      action-label="新建巡查计划"
      :action-column-width="230"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="巡查计划概览"
          description="按当前社区或本人执行范围统计计划状态。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button type="text" @click="openDetail(row)">查看</el-button>
        <el-button v-if="canWrite && row.status === 'ACTIVE'" type="text" class="danger-text" @click="cancel(row)">取消计划</el-button>
        <router-link v-if="row.taskId" class="patrol-task-link" :to="taskRoute">进入任务</router-link>
      </template>
      <template #alternate="{ view, items }">
        <RecordCardGrid
          v-if="view === 'card'"
          :items="items"
          title-prop="title"
          eyebrow-prop="planNo"
          status-prop="status"
          :status-labels="statusLabels"
        >
          <template #default="{ item }">
            <dl class="record-meta">
              <div><dt>责任网格</dt><dd>{{ display(item.gridName) }}</dd></div>
              <div><dt>执行人</dt><dd>{{ display(item.assigneeName) }}</dd></div>
              <div><dt>计划时间</dt><dd>{{ formatDate(item.scheduledAt) }}</dd></div>
              <div><dt>截止时间</dt><dd>{{ formatDate(item.dueAt) }}</dd></div>
            </dl>
          </template>
          <template #actions="{ item }">
            <el-button type="text" @click="openDetail(item)">查看</el-button>
            <el-button v-if="canWrite && item.status === 'ACTIVE'" type="text" class="danger-text" @click="cancel(item)">取消计划</el-button>
          </template>
        </RecordCardGrid>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      title="新建巡查计划"
      description="创建计划会同时生成一条待接单任务，计划与任务保持同一条流转记录。"
      :value="formData"
      :fields="formFields"
      :rules="formRules"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="创建计划"
      @submit="savePlan"
    />

    <el-dialog :visible.sync="detailVisible" title="巡查计划详情" width="680px" append-to-body>
      <el-alert v-if="detailError" class="dialog-alert" :title="detailError" type="error" show-icon :closable="false" />
      <div v-loading="detailLoading" class="patrol-detail">
        <p class="patrol-detail-code">{{ detailItem.planNo || '巡查计划' }}</p>
        <h2>{{ detailItem.title || '未命名计划' }}</h2>
        <dl class="record-meta">
          <div><dt>责任网格</dt><dd>{{ display(detailItem.gridName) }}</dd></div>
          <div><dt>执行人</dt><dd>{{ display(detailItem.assigneeName) }}</dd></div>
          <div><dt>检查内容</dt><dd>{{ display(detailItem.inspectionContent) }}</dd></div>
          <div><dt>计划时间</dt><dd>{{ formatDate(detailItem.scheduledAt) }}</dd></div>
          <div><dt>当前状态</dt><dd>{{ statusLabels[detailItem.status] || detailItem.status }}</dd></div>
        </dl>
      </div>
      <div slot="footer" class="dialog-footer"><el-button @click="detailVisible = false">关闭</el-button></div>
    </el-dialog>
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import RecordCardGrid from '../../components/RecordCardGrid.vue'
import { listGrids, listWorkerOptions } from '../../api/grids'
import { listMyPatrolPlans, listPatrolPlans, createPatrolPlan, cancelPatrolPlan } from '../../api/patrols'
import { getTaskInsight } from '../../api/insights'
import { errorMessage, formatDateTime } from '../../utils/data'

const STATUS_LABELS = { ACTIVE: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

export default {
  name: 'PatrolPlanListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid },
  data() {
    return {
      listPatrolPlans,
      insight: {},
      insightLoading: false,
      insightError: '',
      gridOptions: [],
      workerOptions: [],
      formVisible: false,
      formData: {},
      submitting: false,
      dialogError: '',
      detailVisible: false,
      detailLoading: false,
      detailError: '',
      detailItem: {},
      statuses: Object.keys(STATUS_LABELS).map(value => ({ value, label: STATUS_LABELS[value] })),
      statusLabels: STATUS_LABELS,
      columns: [
        { prop: 'planNo', label: '计划编号', minWidth: 170 },
        { prop: 'title', label: '计划标题', minWidth: 220 },
        { prop: 'gridName', label: '责任网格', minWidth: 170 },
        { prop: 'assigneeName', label: '执行人', minWidth: 130 },
        { prop: 'status', label: '状态', width: 110, labels: STATUS_LABELS },
        { prop: 'scheduledAt', label: '计划时间', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    mineMode() {
      return this.$route.name === 'grid-patrol'
    },
    canWrite() {
      return this.$store.getters['session/hasPermission']('patrol:plan:write')
    },
    pageTitle() {
      return this.mineMode ? '我的巡查计划' : '社区巡查计划'
    },
    pageDescription() {
      return this.mineMode ? '查看本人执行的巡查计划，接单和提交结果在对应任务中完成。' : '创建、取消和跟踪所属社区的日常巡查计划。'
    },
    taskRoute() {
      return this.mineMode ? '/grid/tasks' : '/tasks'
    },
    formFields() {
      return [
        { prop: 'gridId', label: '责任网格', type: 'select', required: true, options: this.gridOptions, span: 12 },
        { prop: 'assigneeUserId', label: '执行网格员', type: 'select', required: true, options: this.workerOptions, span: 12 },
        { prop: 'title', label: '计划标题', required: true, maxlength: 160 },
        { prop: 'inspectionContent', label: '检查内容', type: 'textarea', required: true, rows: 5, maxlength: 10000 },
        { prop: 'scheduledAt', label: '计划时间', type: 'datetime', required: true, span: 12 },
        { prop: 'dueAt', label: '截止时间', type: 'datetime', required: true, span: 12 }
      ]
    },
    formRules() {
      return { title: [{ required: true, min: 2, message: '计划标题不能为空', trigger: 'blur' }], inspectionContent: [{ required: true, min: 2, message: '请填写检查内容', trigger: 'blur' }] }
    },
    insightMetrics() {
      return [
        { key: 'total', label: '计划总数', value: this.insight.total, note: '当前范围' },
        { key: 'active', label: '进行中', value: this.insight.active, note: '等待执行', tone: Number(this.insight.active) ? 'warning' : 'positive' },
        { key: 'completed', label: '已完成', value: this.insight.completed, note: '已形成结果', tone: 'positive' },
        { key: 'overdue', label: '逾期任务', value: this.insight.overdue, note: '需要关注', tone: Number(this.insight.overdue) ? 'danger' : 'positive' }
      ]
    }
  },
  created() {
    this.loadOptions()
    this.loadInsight()
  },
  methods: {
    fetchPlans(params) {
      const request = this.mineMode ? listMyPatrolPlans(params) : listPatrolPlans(params)
      return request.then(result => {
        if (Array.isArray(result)) return { items: result, total: result.length, page: params.page, size: params.size }
        return result
      })
    },
    async loadOptions() {
      if (!this.canWrite) return
      const [grids, workers] = await Promise.allSettled([listGrids({ areaType: 'GRID', page: 1, size: 100 }), listWorkerOptions()])
      if (grids.status === 'fulfilled') this.gridOptions = asItems(grids.value).map(item => ({ value: String(item.id), label: item.areaName || item.name || item.code }))
      if (workers.status === 'fulfilled') this.workerOptions = asItems(workers.value).map(item => ({ value: String(item.id), label: item.realName || item.username || item.name }))
    },
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        const result = await this.fetchPlans({ page: 1, size: 100 })
        const items = asItems(result)
        let overdue = items.filter(item => item.status === 'ACTIVE' && item.dueAt && new Date(item.dueAt).getTime() < Date.now()).length
        if (this.mineMode) {
          const taskInsight = await getTaskInsight().catch(() => ({}))
          overdue = Number(taskInsight.overdue || overdue)
        }
        this.insight = {
          total: Number(result && result.total) || items.length,
          active: items.filter(item => item.status === 'ACTIVE').length,
          completed: items.filter(item => item.status === 'COMPLETED').length,
          overdue
        }
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    openCreate() {
      this.formData = { gridId: '', assigneeUserId: '', title: '', inspectionContent: '', scheduledAt: '', dueAt: '' }
      this.dialogError = ''
      this.formVisible = true
    },
    async savePlan(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        await createPatrolPlan({ gridId: form.gridId, assigneeUserId: form.assigneeUserId, title: form.title, inspectionContent: form.inspectionContent, scheduledAt: form.scheduledAt, dueAt: form.dueAt, priority: 'MEDIUM' })
        this.$message.success('巡查计划已创建')
        this.formVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async openDetail(row) {
      this.detailVisible = true
      this.detailLoading = true
      this.detailError = ''
      this.detailItem = row || {}
      try {
        this.detailItem = row || {}
      } catch (error) {
        this.detailError = errorMessage(error)
      } finally {
        this.detailLoading = false
      }
    },
    async cancel(row) {
      try {
        await this.$confirm('取消计划会同时取消尚未接单的任务，确认继续？', '取消巡查计划', { type: 'warning' })
        await cancelPatrolPlan(row.id, row.version, '计划调整')
        this.$message.success('巡查计划已取消')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '未设置'
    }
  }
}
</script>
