<template>
  <section>
    <ResourceListView
      ref="resource"
      :title="pageTitle"
      :description="pageDescription"
      :fetcher="fetchApplications"
      :columns="columns"
      :status-options="statuses"
      search-label="申请"
      search-placeholder="申请编号、服务名称或居民"
      :manage-permission="managePermission"
      action-label="申请服务"
      :action-column-width="330"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          :title="insightTitle"
          :description="insightDescription"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button v-if="!residentMode" type="text" @click="openFlows(row)">流转</el-button>
        <el-button v-if="residentMode && canCancel(row)" type="text" class="danger-text" @click="cancel(row)">撤回</el-button>
        <el-button v-if="residentMode && canRate(row)" type="text" @click="openRate(row)">评价</el-button>
        <el-button v-if="!residentMode && row.status === 'SUBMITTED' && can('service:application:handle')" type="text" @click="act('accept', row)">受理</el-button>
        <el-button v-if="!residentMode && row.status === 'ACCEPTED' && can('service:application:handle')" type="text" @click="act('start', row)">开始处理</el-button>
        <el-button v-if="!residentMode && row.status === 'PROCESSING' && can('service:application:handle')" type="text" @click="openComplete(row)">办结</el-button>
        <el-dropdown v-if="!residentMode && row.status === 'SUBMITTED' && can('service:application:handle')" trigger="click" @command="command => reject(row, command)">
          <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="reject">驳回申请</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </template>
    </ResourceListView>

    <FormDialog
      v-if="residentMode"
      :visible.sync="createVisible"
      title="申请社区服务"
      description="提交后申请会进入所属社区的服务队列；预约时间和说明将作为处理依据。"
      :value="createForm"
      :fields="createFields"
      :rules="createRules"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="提交申请"
      @submit="saveCreate"
    />

    <FormDialog
      :visible.sync="actionVisible"
      :title="actionTitle"
      :description="actionDescription"
      :value="actionForm"
      :fields="actionFields"
      :rules="actionRules"
      :submitting="submitting"
      :error="dialogError"
      :confirm-text="actionConfirmText"
      @submit="saveAction"
    />

    <el-dialog :visible.sync="flowVisible" :title="flowTitle" width="680px" append-to-body>
      <el-alert v-if="flowError" class="dialog-alert" :title="flowError" type="error" show-icon :closable="false" />
      <div v-loading="flowLoading" class="flow-history">
        <ol v-if="flows.length" class="flow-history-list">
          <li v-for="(item, index) in flows" :key="item.id || `${item.createdAt}-${index}`">
            <span class="flow-history-dot" aria-hidden="true" />
            <div><strong>{{ item.actionLabel || item.action || '流转' }}</strong><p>{{ item.operatorName || item.operator || '系统' }} · {{ formatDate(item.createdAt) }}</p><small v-if="item.remark">{{ item.remark }}</small></div>
          </li>
        </ol>
        <div v-else-if="!flowLoading" class="attachment-empty">当前申请暂无流转记录</div>
      </div>
      <div slot="footer" class="dialog-footer"><el-button @click="flowVisible = false">关闭</el-button></div>
    </el-dialog>

    <FormDialog
      :visible.sync="rateVisible"
      title="评价服务"
      description="每个已办结服务只能评价一次，评价会保留在服务流转记录中。"
      :value="rateForm"
      :fields="rateFields"
      :rules="rateRules"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="提交评价"
      @submit="saveRate"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import {
  acceptServiceApplication,
  applyResidentService,
  cancelResidentServiceApplication,
  completeServiceApplication,
  listResidentServiceApplications,
  listServiceApplicationFlows,
  listServiceApplications,
  listServiceCatalogs,
  rateResidentServiceApplication,
  rejectServiceApplication,
  startServiceApplication
} from '../../api/services'
import { errorMessage, formatDateTime } from '../../utils/data'

const STATUS_LABELS = {
  SUBMITTED: '待受理',
  ACCEPTED: '已受理',
  PROCESSING: '处理中',
  COMPLETED: '已办结',
  REJECTED: '已驳回',
  CANCELLED: '已撤回'
}

const ACTIVE_STATUSES = ['SUBMITTED', 'ACCEPTED', 'PROCESSING']

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

function pageFromItems(items, params) {
  const page = Number(params.page) || 1
  const size = Number(params.size) || 20
  const start = (page - 1) * size
  return { items: items.slice(start, start + size), total: items.length, page, size }
}

function matchesKeyword(item, keyword) {
  if (!keyword) return true
  const value = String(keyword).trim().toLocaleLowerCase('zh-CN')
  return [item.applicationNo, item.serviceCatalogName, item.residentName, item.gridName, item.requestContent]
    .some(field => String(field || '').toLocaleLowerCase('zh-CN').includes(value))
}

export default {
  name: 'ServiceApplicationListView',
  components: { ResourceListView, FormDialog, InsightOverview },
  data() {
    return {
      listServiceApplications,
      listResidentServiceApplications,
      insight: {},
      insightLoading: false,
      insightError: '',
      createVisible: false,
      createForm: {},
      actionVisible: false,
      actionType: '',
      activeApplication: null,
      actionForm: {},
      submitting: false,
      dialogError: '',
      catalogs: [],
      flowVisible: false,
      flowLoading: false,
      flowError: '',
      flows: [],
      activeFlowApplication: null,
      rateVisible: false,
      rateForm: {},
      activeRateApplication: null,
      statuses: Object.keys(STATUS_LABELS).map(value => ({ value, label: STATUS_LABELS[value] })),
      columns: [
        { prop: 'applicationNo', label: '申请编号', minWidth: 170 },
        { prop: 'serviceCatalogName', label: '服务项目', minWidth: 190 },
        { prop: 'residentName', label: '申请人', minWidth: 120 },
        { prop: 'gridName', label: '所属网格', minWidth: 170 },
        { prop: 'status', label: '状态', width: 110, labels: STATUS_LABELS },
        { prop: 'appointmentAt', label: '预约时间', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    residentMode() {
      return ['resident-service', 'resident-rating'].includes(this.$route.name)
    },
    ratingMode() {
      return this.$route.name === 'resident-rating'
    },
    managePermission() {
      if (this.ratingMode) return ''
      return this.residentMode ? 'service:application:apply' : ''
    },
    pageTitle() {
      if (this.ratingMode) return '服务评价'
      return this.residentMode ? '社区服务申请' : (this.$route.name === 'community-todo' ? '社区待办' : '社区服务申请')
    },
    pageDescription() {
      if (this.ratingMode) return '查看已办结的本人服务，并为实际体验留下评价。'
      return this.residentMode ? '选择服务目录并提交本人申请，社区工作人员会在所属范围内处理。' : '按所属社区范围受理、处理和办结居民服务申请。'
    },
    insightTitle() {
      return this.residentMode ? '我的服务状态' : '服务申请队列'
    },
    insightDescription() {
      return this.residentMode ? '只汇总当前居民本人的服务申请。' : '只汇总当前社区权限范围内的服务申请。'
    },
    insightMetrics() {
      return [
        { key: 'total', label: '申请总量', value: this.insight.total, note: '当前权限范围' },
        { key: 'active', label: '处理中', value: this.insight.active, note: '等待后续动作', tone: Number(this.insight.active) ? 'warning' : 'positive' },
        { key: 'completed', label: '已办结', value: this.insight.completed, note: '形成服务结果', tone: 'positive' },
        { key: 'rating', label: '待评价', value: this.insight.rating, note: '完成后可评价', tone: Number(this.insight.rating) ? 'warning' : 'positive' }
      ]
    },
    createFields() {
      return [
        { prop: 'catalogId', label: '服务项目', type: 'select', required: true, options: this.catalogs, span: 12 },
        { prop: 'appointmentAt', label: '期望预约时间', type: 'datetime', span: 12 },
        { prop: 'requestContent', label: '申请说明', type: 'textarea', required: true, rows: 6, maxlength: 10000 }
      ]
    },
    createRules() {
      return { requestContent: [{ required: true, min: 2, max: 10000, message: '请填写申请说明', trigger: 'blur' }] }
    },
    actionTitle() {
      return { complete: '办结服务申请', reject: '驳回服务申请' }[this.actionType] || '处理服务申请'
    },
    actionDescription() {
      if (!this.activeApplication) return ''
      return `${this.activeApplication.applicationNo || '当前申请'} · ${this.activeApplication.serviceCatalogName || ''}。系统会校验当前状态和最新版本。`
    },
    actionConfirmText() {
      return { complete: '确认办结', reject: '确认驳回' }[this.actionType] || '确认'
    },
    actionFields() {
      if (this.actionType === 'reject') return [{ prop: 'reason', label: '驳回原因', type: 'textarea', required: true, rows: 4, maxlength: 1000 }]
      if (this.actionType === 'complete') return [{ prop: 'result', label: '服务结果', type: 'textarea', required: true, rows: 5, maxlength: 2000 }]
      return []
    },
    actionRules() {
      return { reason: [{ required: this.actionType === 'reject', min: 2, message: '请填写驳回原因', trigger: 'blur' }], result: [{ required: this.actionType === 'complete', min: 2, message: '请填写服务结果', trigger: 'blur' }] }
    },
    flowTitle() {
      return this.activeFlowApplication ? `${this.activeFlowApplication.applicationNo || '申请'} · 流转` : '申请流转'
    },
    rateFields() {
      return [
        { prop: 'rating', label: '服务评分', type: 'select', required: true, options: [1, 2, 3, 4, 5].map(value => ({ value, label: `${value} 星` })), span: 12 },
        { prop: 'remark', label: '评价内容', type: 'textarea', rows: 4, maxlength: 500 }
      ]
    },
    rateRules() {
      return { rating: [{ required: true, message: '请选择评分', trigger: 'change' }] }
    }
  },
  created() {
    this.loadCatalogs()
    this.loadInsight()
  },
  methods: {
    can(permission) {
      return this.$store.getters['session/hasPermission'](permission)
    },
    async fetchApplications(params) {
      if (this.$route.name === 'community-todo') {
        const results = await Promise.all(ACTIVE_STATUSES.map(status => listServiceApplications({
          page: 1,
          size: 100,
          status
        })))
        const items = results
          .flatMap(result => asItems(result))
          .filter(item => ACTIVE_STATUSES.includes(item.status))
          .filter(item => matchesKeyword(item, params.keyword))
          .sort((left, right) => new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime())
        return pageFromItems(items, params)
      }

      const result = this.residentMode ? await listResidentServiceApplications(params) : await listServiceApplications(params)
      let items = asItems(result)
      if (this.ratingMode) {
        items = items.filter(item => item.status === 'COMPLETED' && !item.rating)
      }
      if (Array.isArray(result) || this.ratingMode) return pageFromItems(items, params)
      return result
    },
    async loadCatalogs() {
      if (!this.residentMode) return
      try {
        const result = await listServiceCatalogs({ status: 'ENABLED' })
        this.catalogs = asItems(result).map(item => ({ value: String(item.id), label: item.name || item.serviceCatalogName || item.code }))
      } catch (error) {
        this.$message.warning(errorMessage(error))
      }
    },
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        const result = await this.fetchApplications({ page: 1, size: 100 })
        const items = asItems(result)
        this.insight = {
          total: Number(result && result.total) || items.length,
          active: items.filter(item => ['SUBMITTED', 'ACCEPTED', 'PROCESSING'].includes(item.status)).length,
          completed: items.filter(item => item.status === 'COMPLETED').length,
          rating: items.filter(item => item.status === 'COMPLETED' && !item.rating).length
        }
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    openCreate() {
      this.createForm = { catalogId: '', appointmentAt: '', requestContent: '', requestToken: '' }
      this.dialogError = ''
      this.createVisible = true
    },
    async saveCreate(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        await applyResidentService({ serviceCatalogId: form.catalogId, appointmentAt: form.appointmentAt || null, requestContent: form.requestContent, requestToken: form.requestToken || undefined })
        this.$message.success('服务申请已提交')
        this.createVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    canCancel(row) {
      return row.status === 'SUBMITTED'
    },
    canRate(row) {
      return row.status === 'COMPLETED' && !row.rating
    },
    async cancel(row) {
      try {
        await this.$confirm('撤回后社区不会继续处理该申请，确认撤回？', '撤回服务申请', { type: 'warning' })
        await cancelResidentServiceApplication(row.id, row.version, '居民主动撤回')
        this.$message.success('服务申请已撤回')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    },
    openRate(row) {
      this.activeRateApplication = row
      this.rateForm = { rating: '', remark: '' }
      this.dialogError = ''
      this.rateVisible = true
    },
    async saveRate(form) {
      if (this.submitting || !this.activeRateApplication) return
      this.submitting = true
      this.dialogError = ''
      try {
        await rateResidentServiceApplication(this.activeRateApplication.id, { rating: Number(form.rating), remark: form.remark || null, version: this.activeRateApplication.version })
        this.$message.success('评价已提交')
        this.rateVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    act(type, row) {
      this.activeApplication = row
      if (type === 'accept') return this.saveTransition('accept')
      if (type === 'start') return this.saveTransition('start')
      this.actionType = type
      this.actionForm = { reason: '', result: '' }
      this.dialogError = ''
      this.actionVisible = true
    },
    openComplete(row) {
      this.activeApplication = row
      this.actionType = 'complete'
      this.actionForm = { result: '' }
      this.dialogError = ''
      this.actionVisible = true
    },
    async saveTransition(type) {
      if (!this.activeApplication || this.submitting) return
      this.submitting = true
      try {
        if (type === 'accept') await acceptServiceApplication(this.activeApplication.id, this.activeApplication.version, '社区工作人员受理')
        else await startServiceApplication(this.activeApplication.id, this.activeApplication.version, '进入处理')
        this.$message.success(type === 'accept' ? '申请已受理' : '申请已进入处理')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.$message.error(errorMessage(error))
      } finally {
        this.submitting = false
      }
    },
    async saveAction(form) {
      if (!this.activeApplication || this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.actionType === 'complete') await completeServiceApplication(this.activeApplication.id, { resultSummary: form.result, version: this.activeApplication.version, remark: null })
        else await rejectServiceApplication(this.activeApplication.id, this.activeApplication.version, form.reason)
        this.$message.success(this.actionType === 'complete' ? '服务申请已办结' : '服务申请已驳回')
        this.actionVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async reject(row) {
      this.activeApplication = row
      this.actionType = 'reject'
      this.actionForm = { reason: '' }
      this.dialogError = ''
      this.actionVisible = true
    },
    async openFlows(row) {
      this.activeFlowApplication = row
      this.flowVisible = true
      this.flowLoading = true
      this.flowError = ''
      this.flows = []
      try {
        this.flows = asItems(await listServiceApplicationFlows(row.id))
      } catch (error) {
        this.flowError = errorMessage(error)
      } finally {
        this.flowLoading = false
      }
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '—'
    }
  }
}
</script>
