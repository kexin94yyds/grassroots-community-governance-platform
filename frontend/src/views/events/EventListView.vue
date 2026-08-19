<template>
  <section>
    <ResourceListView
      ref="resource"
      :title="pageTitle"
      :description="pageDescription"
      :fetcher="listEvents"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="事件编号、标题或地点"
      :manage-permission="routeMode === 'history' ? '' : 'event:report'"
      action-label="上报事件"
      :action-column-width="340"
      :view-options="viewOptions"
      @create="openReport"
    >
      <template #insight>
        <InsightOverview
          title="事件运行态势"
          description="聚合事件总量、待办压力、办结结果与严重程度分布。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          :groups="insightGroups"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button v-if="can('event:read')" type="text" @click="openFlows(row)">流转</el-button>
        <el-button
          v-if="can('file:read')"
          type="text"
          @click="openAttachments(row)"
        >
          附件
        </el-button>
        <el-button
          v-if="row.status === 'REPORTED' && can('event:accept')"
          type="text"
          @click="openAction('accept', row)"
        >
          受理
        </el-button>
        <el-button
          v-if="row.status === 'ACCEPTED' && can('event:assign')"
          type="text"
          @click="openAction('assign', row)"
        >
          派发
        </el-button>
        <el-dropdown
          v-if="secondaryActions(row).length"
          trigger="click"
          @command="openAction($event, row)"
        >
          <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item
              v-for="item in secondaryActions(row)"
              :key="item.command"
              :command="item.command"
            >
              {{ item.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </template>
      <template #alternate="{ view, items }">
        <RecordCardGrid
          v-if="view === 'card'"
          :items="items"
          title-prop="title"
          eyebrow-prop="eventNo"
          status-prop="status"
          :status-labels="eventStatusLabels"
        >
          <template #default="{ item }">
            <dl class="record-meta">
              <div><dt>事件类别</dt><dd>{{ display(item.categoryName) }}</dd></div>
              <div><dt>严重程度</dt><dd>{{ priorityLabel(item.severity) }}</dd></div>
              <div><dt>所属网格</dt><dd>{{ display(item.gridName) }}</dd></div>
              <div><dt>当前执行人</dt><dd>{{ display(item.assignedToName) }}</dd></div>
              <div><dt>上报时间</dt><dd>{{ formatDate(item.reportedAt) }}</dd></div>
              <div><dt>发生地址</dt><dd>{{ display(item.address) }}</dd></div>
            </dl>
          </template>
          <template #actions="{ item }">
            <el-button v-if="can('event:read')" type="text" @click="openFlows(item)">流转</el-button>
            <el-button
              v-if="can('file:read')"
              type="text"
              @click="openAttachments(item)"
            >
              附件
            </el-button>
            <el-button
              v-if="item.status === 'REPORTED' && can('event:accept')"
              type="text"
              @click="openAction('accept', item)"
            >
              受理
            </el-button>
            <el-button
              v-if="item.status === 'ACCEPTED' && can('event:assign')"
              type="text"
              @click="openAction('assign', item)"
            >
              派发
            </el-button>
            <el-dropdown
              v-if="secondaryActions(item).length"
              trigger="click"
              @command="openAction($event, item)"
            >
              <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item
                  v-for="action in secondaryActions(item)"
                  :key="action.command"
                  :command="action.command"
                >
                  {{ action.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
        </RecordCardGrid>

        <section v-else-if="view === 'trace'" class="flow-board event-flow-board" aria-label="事件流程追踪">
          <p class="flow-scope-note">仅展示本页有记录的阶段；阶段较多时可横向滚动。</p>
          <div class="flow-board-scroll">
            <section
              v-for="column in visibleEventTraceColumns(items)"
              :key="column.value"
              class="flow-column"
            >
              <header>
                <span>{{ column.label }}</span>
                <strong>{{ itemsForStatus(items, column.value).length }}</strong>
              </header>
              <div class="flow-column-body">
                <article
                  v-for="item in itemsForStatus(items, column.value)"
                  :key="item.id"
                  class="flow-card"
                >
                  <p>{{ item.eventNo }}</p>
                  <h4>{{ item.title }}</h4>
                  <dl>
                    <div><dt>程度</dt><dd>{{ priorityLabel(item.severity) }}</dd></div>
                    <div><dt>网格</dt><dd>{{ display(item.gridName) }}</dd></div>
                  </dl>
                  <div v-if="hasEventAction(item) || can('file:read') || can('event:read')" class="flow-card-actions">
                    <el-button v-if="can('event:read')" type="text" @click="openFlows(item)">流转</el-button>
                    <el-button
                      v-if="can('file:read')"
                      type="text"
                      @click="openAttachments(item)"
                    >
                      附件
                    </el-button>
                    <el-button
                      v-if="item.status === 'REPORTED' && can('event:accept')"
                      type="text"
                      @click="openAction('accept', item)"
                    >
                      受理
                    </el-button>
                    <el-button
                      v-if="item.status === 'ACCEPTED' && can('event:assign')"
                      type="text"
                      @click="openAction('assign', item)"
                    >
                      派发
                    </el-button>
                    <el-button
                      v-for="action in secondaryActions(item)"
                      :key="action.command"
                      type="text"
                      @click="openAction(action.command, item)"
                    >
                      {{ action.label }}
                    </el-button>
                  </div>
                </article>
                <p v-if="!itemsForStatus(items, column.value).length" class="flow-empty">本页无记录</p>
              </div>
            </section>
          </div>
        </section>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="reportVisible"
      title="上报治理事件"
      :description="reportDescription"
      :value="reportForm"
      :fields="reportFields"
      :rules="reportRules"
      :submitting="submitting"
      :error="dialogError"
      width="760px"
      :confirm-text="reportConfirmText"
      @close="resetReportDialog"
      @submit="saveReport"
    >
      <template #extra>
        <section class="attachment-picker" aria-label="事件附件">
          <div class="attachment-picker-heading">
            <div>
              <strong>事件附件</strong>
              <p>支持 JPEG、PNG、PDF；单个文件不超过 10 MB，最多 20 个。</p>
            </div>
            <span v-if="createdReportEvent" class="attachment-event-badge">
              已生成 {{ createdReportEvent.eventNo }}
            </span>
          </div>
          <el-upload
            ref="reportUpload"
            action=""
            :auto-upload="false"
            :multiple="true"
            :limit="20"
            accept="image/jpeg,image/png,application/pdf"
            :file-list="reportUploadFiles"
            :on-change="handleReportFileChange"
            :on-remove="handleReportFileRemove"
            :before-remove="preventUploadedRemoval"
            :on-exceed="handleFileExceed"
          >
            <el-button size="small" icon="el-icon-paperclip">选择附件</el-button>
          </el-upload>
          <p v-if="reportUploadSummary" class="attachment-upload-summary" role="status">
            {{ reportUploadSummary }}
          </p>
        </section>
      </template>
    </FormDialog>

    <FormDialog
      :visible.sync="actionVisible"
      :title="actionTitle"
      :description="actionDescription"
      :value="actionForm"
      :fields="actionFields"
      :rules="actionRules"
      :submitting="submitting"
      :error="dialogError"
      width="680px"
      :confirm-text="actionConfirmText"
      @submit="submitAction"
    />

    <el-dialog
      :visible.sync="attachmentVisible"
      :title="attachmentDialogTitle"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!attachmentUploading"
    >
      <p class="dialog-description">附件下载会再次校验当前账号权限和事件所属网格。</p>
      <el-alert
        v-if="attachmentError"
        class="dialog-alert"
        :title="attachmentError"
        type="error"
        show-icon
        :closable="false"
      />
      <div v-loading="attachmentLoading" class="attachment-dialog-body">
        <div v-if="attachments.length" class="attachment-list">
          <article v-for="item in attachments" :key="item.id" class="attachment-item">
            <span class="attachment-file-icon" aria-hidden="true"><i class="el-icon-document" /></span>
            <div>
              <strong>{{ item.originalName }}</strong>
              <p>#{{ item.id }} · {{ formatFileSize(item.fileSize) }} · {{ formatDate(item.createdAt) }}</p>
            </div>
            <div class="attachment-item-actions">
              <el-button type="text" :loading="downloadingId === item.id" @click="downloadAttachment(item)">
                下载
              </el-button>
              <el-button
                v-if="canDeleteEventAttachment(item)"
                type="text"
                class="danger-text"
                :loading="deletingAttachmentId === item.id"
                @click="removeAttachment(item)"
              >
                删除
              </el-button>
            </div>
          </article>
        </div>
        <div v-else-if="!attachmentLoading" class="attachment-empty">当前事件暂无附件</div>

        <section v-if="can('file:upload')" class="attachment-picker attachment-detail-picker">
          <div class="attachment-picker-heading">
            <div>
              <strong>添加附件</strong>
              <p>失败文件会保留在列表中，可直接重新上传。</p>
            </div>
          </div>
          <el-upload
            ref="detailUpload"
            action=""
            :auto-upload="false"
            :multiple="true"
            :limit="20"
            accept="image/jpeg,image/png,application/pdf"
            :file-list="detailUploadFiles"
            :on-change="handleDetailFileChange"
            :on-remove="handleDetailFileRemove"
            :before-remove="preventUploadedRemoval"
            :on-exceed="handleFileExceed"
          >
            <el-button size="small" icon="el-icon-paperclip" :disabled="attachmentUploading">选择附件</el-button>
          </el-upload>
        </section>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button :disabled="attachmentUploading" @click="attachmentVisible = false">关闭</el-button>
        <el-button
          v-if="can('file:upload') && detailUploadFiles.length"
          type="primary"
          :loading="attachmentUploading"
          @click="uploadDetailAttachments"
        >
          上传所选附件
        </el-button>
      </div>
    </el-dialog>

    <el-dialog
      :visible.sync="flowVisible"
      :title="flowDialogTitle"
      width="680px"
      append-to-body
      :close-on-click-modal="false"
    >
      <p class="dialog-description">按时间顺序展示该事件的真实操作留痕和状态变化。</p>
      <el-alert
        v-if="flowError"
        class="dialog-alert"
        :title="flowError"
        type="error"
        show-icon
        :closable="false"
      />
      <div v-loading="flowLoading" class="flow-history">
        <ol v-if="eventFlows.length" class="flow-history-list">
          <li v-for="(item, index) in eventFlows" :key="item.id || `${item.createdAt}-${index}`">
            <span class="flow-history-dot" aria-hidden="true" />
            <div>
              <strong>{{ flowActionLabel(item) }}</strong>
              <p>{{ flowOperator(item) }} · {{ formatDate(item.createdAt || item.operatedAt) }}</p>
              <small v-if="item.remark || item.reason">{{ item.remark || item.reason }}</small>
            </div>
          </li>
        </ol>
        <div v-else-if="!flowLoading" class="attachment-empty">当前事件暂无流转记录</div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="flowVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import RecordCardGrid from '../../components/RecordCardGrid.vue'
import { getEventInsight } from '../../api/insights'
import {
  acceptEvent,
  assignEvent,
  cancelEvent,
  listEventCategories,
  listEvents,
  listEventFlows,
  rejectEvent,
  reportEvent
} from '../../api/events'
import { listGrids, listWorkerOptions } from '../../api/grids'
import {
  deleteEventAttachment,
  downloadAuthorizedFile,
  listEventAttachments,
  uploadEventAttachment
} from '../../api/files'
import { EVENT_STATUS } from '../../constants/domain'
import { errorMessage, formatDateTime } from '../../utils/data'

const PRIORITY_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'URGENT', label: '紧急' }
]

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

export default {
  name: 'EventListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid },
  data() {
    return {
      listEvents,
      insight: {},
      insightLoading: false,
      insightError: '',
      reportVisible: false,
      createdReportEvent: null,
      reportUploadFiles: [],
      actionVisible: false,
      reportForm: {},
      actionForm: {},
      actionType: '',
      activeEvent: null,
      submitting: false,
      dialogError: '',
      attachmentVisible: false,
      activeAttachmentEvent: null,
      attachments: [],
      attachmentLoading: false,
      attachmentUploading: false,
      attachmentError: '',
      detailUploadFiles: [],
      downloadingId: '',
      deletingAttachmentId: '',
      flowVisible: false,
      flowLoading: false,
      flowError: '',
      activeFlowEvent: null,
      eventFlows: [],
      categoryOptions: [],
      gridOptions: [],
      workerOptions: [],
      viewOptions: [
        { value: 'list', label: '列表', icon: 'el-icon-tickets' },
        { value: 'card', label: '事件卡片', icon: 'el-icon-postcard' },
        { value: 'trace', label: '流程追踪', icon: 'el-icon-connection' }
      ],
      eventStatusLabels: EVENT_STATUS,
      statuses: Object.keys(EVENT_STATUS).map(value => ({ value, label: EVENT_STATUS[value] })),
      columns: [
        { prop: 'eventNo', label: '事件编号', minWidth: 170 },
        { prop: 'title', label: '事件标题', minWidth: 220 },
        { prop: 'categoryName', label: '事件类别', minWidth: 120 },
        { prop: 'severity', label: '严重程度', minWidth: 120 },
        { prop: 'gridName', label: '所属网格', minWidth: 180 },
        { prop: 'assignedToName', label: '当前执行人', minWidth: 140 },
        { prop: 'status', label: '状态', width: 120, labels: EVENT_STATUS },
        { prop: 'reportedAt', label: '上报时间', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    routeMode() {
      if (this.$route.name === 'grid-event-report') return 'report'
      if (this.$route.name === 'grid-history') return 'history'
      return 'all'
    },
    pageTitle() {
      return { report: '责任网格事件上报', history: '事件历史' }[this.routeMode] || '治理事件'
    },
    pageDescription() {
      return {
        report: '记录责任网格内发现的问题，提交后进入社区工作人员受理队列。',
        history: '查看本人责任范围内事件的上报、处置和办结留痕。'
      }[this.routeMode] || '查询事件上报、受理、派发、处置和复核进度。'
    },
    insightMetrics() {
      const closed = this.breakdownCount(this.insight.statuses, 'CLOSED')
      const rejected = this.breakdownCount(this.insight.statuses, 'REJECTED')
      const highRisk = this.breakdownCount(this.insight.severities, 'HIGH') +
        this.breakdownCount(this.insight.severities, 'URGENT')
      return [
        { key: 'total', label: '事件总量', value: this.insight.total, note: '权限范围内' },
        {
          key: 'actionable',
          label: '当前待办',
          value: this.insight.actionable,
          note: '待受理至待复核',
          tone: Number(this.insight.actionable || 0) ? 'warning' : 'positive'
        },
        { key: 'closed', label: '已办结', value: closed, note: '完成治理闭环', tone: 'positive' },
        { key: 'risk', label: '高 / 紧急', value: highRisk, note: `另有 ${rejected} 条驳回`, tone: 'warning' }
      ]
    },
    insightGroups() {
      return [
        {
          key: 'statuses',
          title: '处置阶段',
          items: (this.insight.statuses || []).map(item => ({
            ...item,
            label: EVENT_STATUS[item.key] || item.key
          }))
        },
        {
          key: 'severities',
          title: '严重程度',
          items: (this.insight.severities || []).map(item => ({
            ...item,
            label: this.priorityLabel(item.key)
          }))
        }
      ]
    },
    eventTraceColumns() {
      return Object.keys(EVENT_STATUS).map(value => ({ value, label: EVENT_STATUS[value] }))
    },
    reportFields() {
      const fields = [
        { prop: 'categoryId', label: '事件类别', type: 'select', required: true, options: this.categoryOptions, span: 12 },
        { prop: 'gridId', label: '所属网格', type: 'select', required: true, options: this.gridOptions, span: 12 },
        { prop: 'title', label: '事件标题', required: true, maxlength: 160 },
        { prop: 'description', label: '事件描述', type: 'textarea', required: true, rows: 5, maxlength: 10000 },
        {
          prop: 'reportChannel',
          label: '上报渠道',
          type: 'select',
          required: true,
          span: 12,
          options: [
            { value: 'WEB', label: '网上录入' },
            { value: 'PHONE', label: '电话反映' },
            { value: 'ONSITE', label: '现场发现' },
            { value: 'OTHER', label: '其他' }
          ]
        },
        { prop: 'severity', label: '严重程度', type: 'select', required: true, options: PRIORITY_OPTIONS, span: 12 },
        { prop: 'address', label: '发生地址', maxlength: 255, span: 12 },
        { prop: 'reporterName', label: '反映人', maxlength: 80, span: 12 }
      ]
      return fields.map(field => ({ ...field, disabled: Boolean(this.createdReportEvent) }))
    },
    reportDescription() {
      return this.createdReportEvent
        ? '事件已经生成，不会重复上报。请重试失败附件，或关闭后从事件列表的“附件”入口继续添加。'
        : '事件编号和初始状态由后端生成；可同时选择附件，提交后进入待受理。'
    },
    reportConfirmText() {
      return this.createdReportEvent ? '重试失败附件' : '提交上报'
    },
    reportUploadSummary() {
      if (!this.reportUploadFiles.length) return ''
      const success = this.reportUploadFiles.filter(file => file.status === 'success').length
      const failed = this.reportUploadFiles.filter(file => file.status === 'fail').length
      const uploading = this.reportUploadFiles.filter(file => file.status === 'uploading').length
      if (uploading) return `正在上传 ${uploading} 个附件…`
      if (failed) return `${success} 个上传成功，${failed} 个失败；可点击确认重试。`
      return success === this.reportUploadFiles.length
        ? `${success} 个附件已上传成功。`
        : `已选择 ${this.reportUploadFiles.length} 个附件。`
    },
    attachmentDialogTitle() {
      if (!this.activeAttachmentEvent) return '事件附件'
      return `${this.activeAttachmentEvent.eventNo || '事件'} · 附件`
    },
    flowDialogTitle() {
      if (!this.activeFlowEvent) return '事件流转历史'
      return `${this.activeFlowEvent.eventNo || '事件'} · 流转历史`
    },
    currentUserId() {
      const user = this.$store.state.session.user || {}
      return user.id == null ? '' : String(user.id)
    },
    reportRules() {
      return {
        title: [{ required: true, min: 2, max: 160, message: '标题需为 2-160 个字符', trigger: 'blur' }],
        description: [{ required: true, min: 5, max: 10000, message: '请填写至少 5 个字符的事件描述', trigger: 'blur' }]
      }
    },
    actionTitle() {
      return {
        accept: '受理事件',
        reject: '驳回事件',
        assign: '派发处置任务',
        cancel: '撤销事件'
      }[this.actionType] || '处理事件'
    },
    actionDescription() {
      if (!this.activeEvent) return ''
      const prefix = `${this.activeEvent.eventNo || ''} ${this.activeEvent.title || ''}`.trim()
      return `${prefix}。系统将校验当前状态和版本，冲突时不会覆盖他人操作。`
    },
    actionConfirmText() {
      return {
        accept: '确认受理',
        reject: '确认驳回',
        assign: '确认派发',
        cancel: '确认撤销'
      }[this.actionType] || '确认'
    },
    actionFields() {
      if (this.actionType === 'assign') {
        return [
          { prop: 'assigneeUserId', label: '执行网格员', type: 'select', required: true, options: this.workerOptions, span: 12 },
          { prop: 'priority', label: '任务优先级', type: 'select', required: true, options: PRIORITY_OPTIONS, span: 12 },
          { prop: 'taskTitle', label: '任务标题', required: true, maxlength: 160 },
          { prop: 'taskDescription', label: '任务说明', type: 'textarea', rows: 4, maxlength: 10000 },
          { prop: 'dueAt', label: '截止时间', type: 'datetime', span: 12 },
          { prop: 'remark', label: '派发说明', type: 'textarea', rows: 3, maxlength: 1000 }
        ]
      }
      if (this.actionType === 'reject' || this.actionType === 'cancel') {
        return [{ prop: 'reason', label: this.actionType === 'reject' ? '驳回原因' : '撤销原因', type: 'textarea', required: true, rows: 4, maxlength: 1000 }]
      }
      return [{ prop: 'remark', label: '受理说明', type: 'textarea', rows: 3, maxlength: 1000 }]
    },
    actionRules() {
      if (this.actionType === 'assign') {
        return {
          taskTitle: [{ required: true, min: 2, max: 160, message: '任务标题需为 2-160 个字符', trigger: 'blur' }]
        }
      }
      return {}
    }
  },
  created() {
    this.loadOptions()
    this.loadInsight()
  },
  methods: {
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        this.insight = await getEventInsight()
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    breakdownCount(items, key) {
      const match = (items || []).find(item => item.key === key)
      return match ? Number(match.count || 0) : 0
    },
    priorityLabel(value) {
      const match = PRIORITY_OPTIONS.find(item => item.value === value)
      return match ? match.label : value || '—'
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '—'
    },
    itemsForStatus(items, status) {
      return items.filter(item => item.status === status)
    },
    visibleEventTraceColumns(items) {
      if (!items.length) return this.eventTraceColumns
      return this.eventTraceColumns.filter(column => this.itemsForStatus(items, column.value).length)
    },
    hasEventAction(row) {
      return (row.status === 'REPORTED' && this.can('event:accept')) ||
        (row.status === 'ACCEPTED' && this.can('event:assign')) ||
        this.secondaryActions(row).length > 0
    },
    can(permission) {
      return this.$store.getters['session/hasPermission'](permission)
    },
    canDeleteEventAttachment(item) {
      if (!this.activeAttachmentEvent || !this.can('file:delete')) return false
      if (!['REPORTED', 'ACCEPTED'].includes(this.activeAttachmentEvent.status)) return false
      const roles = (this.$store.state.session.user || {}).roles || []
      const manager = roles.includes('SYSTEM_ADMIN') || roles.includes('COMMUNITY_STAFF')
      return manager || (item && item.uploadedBy != null && String(item.uploadedBy) === this.currentUserId)
    },
    async loadOptions() {
      const canAssign = this.can('event:assign')
      const [categories, grids, workers] = await Promise.allSettled([
        listEventCategories(),
        listGrids({ page: 1, size: 100 }),
        canAssign ? listWorkerOptions() : Promise.resolve([])
      ])
      if (categories.status === 'fulfilled') {
        this.categoryOptions = asItems(categories.value).map(item => ({
          value: String(item.id),
          label: item.name || item.code || String(item.id)
        }))
      }
      if (grids.status === 'fulfilled') {
        this.gridOptions = asItems(grids.value).map(item => ({
          value: String(item.id),
          label: `${item.areaName || item.areaCode} (${item.areaCode || item.id})`
        }))
      }
      if (workers.status === 'fulfilled') {
        this.workerOptions = asItems(workers.value).map(item => ({
          value: String(item.id),
          label: item.realName || item.username || String(item.id)
        }))
      }
    },
    async openFlows(event) {
      this.activeFlowEvent = event
      this.eventFlows = []
      this.flowError = ''
      this.flowVisible = true
      this.flowLoading = true
      try {
        this.eventFlows = asItems(await listEventFlows(event.id))
      } catch (error) {
        this.flowError = errorMessage(error)
      } finally {
        this.flowLoading = false
      }
    },
    flowActionLabel(item) {
      const labels = {
        REPORT: '上报事件',
        ACCEPT: '受理事件',
        REJECT: '驳回事件',
        ASSIGN: '派发任务',
        START: '开始处置',
        SUBMIT: '提交处置结果',
        SUBMIT_REVIEW: '提交处置结果',
        APPROVE: '复核通过',
        RETURN: '复核退回',
        CANCEL: '撤销事件',
        COMPLETE: '办结事件'
      }
      const action = labels[item.action] || item.action || '流程操作'
      const status = EVENT_STATUS[item.toStatus] || item.toStatus
      return status ? `${action} · ${status}` : action
    },
    flowOperator(item) {
      return item.operatorName || item.operatorUserId || '系统'
    },
    secondaryActions(row) {
      const items = []
      if (row.status === 'REPORTED' && this.can('event:reject')) items.push({ command: 'reject', label: '驳回' })
      if (['REPORTED', 'ACCEPTED'].includes(row.status) && this.can('event:cancel')) {
        items.push({ command: 'cancel', label: '撤销' })
      }
      return items
    },
    openReport() {
      this.resetReportDialog()
      this.reportForm = {
        categoryId: '',
        gridId: '',
        title: '',
        description: '',
        reportChannel: 'ONSITE',
        severity: 'MEDIUM',
        address: '',
        reporterName: ''
      }
      this.dialogError = ''
      this.reportVisible = true
      this.loadOptions()
    },
    resetReportDialog() {
      this.createdReportEvent = null
      this.reportUploadFiles = []
      this.dialogError = ''
      if (this.$refs.reportUpload) this.$refs.reportUpload.clearFiles()
    },
    handleReportFileChange(file, fileList) {
      this.reportUploadFiles = this.validateSelectedFile(file, fileList)
    },
    handleReportFileRemove(file, fileList) {
      this.reportUploadFiles = fileList
    },
    handleDetailFileChange(file, fileList) {
      this.detailUploadFiles = this.validateSelectedFile(file, fileList)
    },
    handleDetailFileRemove(file, fileList) {
      this.detailUploadFiles = fileList
    },
    handleFileExceed() {
      this.$message.warning('一次最多选择 20 个附件')
    },
    preventUploadedRemoval(file) {
      if (file.status !== 'success') return true
      this.$message.info('已上传附件不能在此删除，可关闭窗口后继续处理事件。')
      return false
    },
    validateSelectedFile(file, fileList) {
      const allowedTypes = ['image/jpeg', 'image/png', 'application/pdf']
      const raw = file && file.raw
      let message = ''
      if (!raw || !allowedTypes.includes(raw.type)) {
        message = '仅支持 JPEG、PNG 或 PDF 附件'
      } else if (!raw.size) {
        message = '不能上传空文件'
      } else if (raw.size > 10 * 1024 * 1024) {
        message = '单个附件不能超过 10 MB'
      }
      if (!message) return fileList
      this.$message.error(message)
      return fileList.filter(item => item.uid !== file.uid)
    },
    openAction(type, row) {
      this.actionType = type
      this.activeEvent = row
      this.dialogError = ''
      this.actionForm = {
        version: row.version,
        remark: '',
        reason: '',
        assigneeUserId: '',
        taskTitle: row.title ? `处置：${row.title}` : '',
        taskDescription: row.description || '',
        priority: row.severity || 'MEDIUM',
        dueAt: ''
      }
      this.actionVisible = true
      if (type === 'assign') this.loadOptions()
    },
    async saveReport(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (!this.createdReportEvent) {
          this.createdReportEvent = await reportEvent({
            categoryId: String(form.categoryId),
            gridId: String(form.gridId),
            title: form.title,
            description: form.description,
            reportChannel: form.reportChannel,
            severity: form.severity,
            address: form.address || null,
            reporterName: form.reporterName || null
          })
          await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
        }

        const failed = await this.uploadSelectedFiles(this.createdReportEvent.id, this.reportUploadFiles)
        if (failed) {
          this.dialogError = `事件已上报，但有 ${failed} 个附件上传失败；请检查文件后重试。`
          return
        }
        const attachmentCount = this.reportUploadFiles.length
        this.$message.success(attachmentCount
          ? `事件已上报，${attachmentCount} 个附件上传成功`
          : '事件已上报，等待社区工作人员受理')
        this.reportVisible = false
        this.resetReportDialog()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async uploadSelectedFiles(eventId, files) {
      let failed = 0
      for (const file of files) {
        if (!file.raw || file.status === 'success') continue
        this.$set(file, 'status', 'uploading')
        this.$set(file, 'percentage', 0)
        try {
          const attachment = await uploadEventAttachment(eventId, file.raw, progress => {
            const total = Number(progress.total || file.raw.size || 0)
            const percentage = total ? Math.round((Number(progress.loaded || 0) / total) * 100) : 0
            this.$set(file, 'percentage', percentage)
          })
          this.$set(file, 'status', 'success')
          this.$set(file, 'response', attachment)
        } catch (error) {
          failed += 1
          this.$set(file, 'status', 'fail')
          this.$set(file, 'uploadError', errorMessage(error))
        }
      }
      return failed
    },
    async openAttachments(event) {
      this.activeAttachmentEvent = event
      this.attachments = []
      this.detailUploadFiles = []
      this.attachmentError = ''
      this.attachmentVisible = true
      await this.loadAttachments()
    },
    async loadAttachments() {
      if (!this.activeAttachmentEvent) return
      this.attachmentLoading = true
      this.attachmentError = ''
      try {
        this.attachments = await listEventAttachments(this.activeAttachmentEvent.id)
      } catch (error) {
        this.attachmentError = errorMessage(error)
      } finally {
        this.attachmentLoading = false
      }
    },
    async uploadDetailAttachments() {
      if (!this.activeAttachmentEvent || this.attachmentUploading) return
      this.attachmentUploading = true
      this.attachmentError = ''
      try {
        const failed = await this.uploadSelectedFiles(this.activeAttachmentEvent.id, this.detailUploadFiles)
        await this.loadAttachments()
        this.detailUploadFiles = this.detailUploadFiles.filter(file => file.status !== 'success')
        if (failed) {
          this.attachmentError = `有 ${failed} 个附件上传失败，可直接重试。`
        } else {
          this.$message.success('附件上传成功')
        }
      } finally {
        this.attachmentUploading = false
      }
    },
    async downloadAttachment(item) {
      if (this.downloadingId) return
      this.downloadingId = item.id
      this.attachmentError = ''
      try {
        const response = await downloadAuthorizedFile(item.id)
        const url = window.URL.createObjectURL(response.data)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = item.originalName || `attachment-${item.id}`
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
        window.URL.revokeObjectURL(url)
      } catch (error) {
        this.attachmentError = errorMessage(error)
      } finally {
        this.downloadingId = ''
      }
    },
    async removeAttachment(item) {
      if (!this.canDeleteEventAttachment(item) || this.deletingAttachmentId) return
      try {
        await this.$confirm(`确认删除附件“${item.originalName}”？删除后不可在当前事件中查看。`, '删除附件', {
          type: 'warning'
        })
      } catch (error) {
        return
      }
      this.deletingAttachmentId = item.id
      this.attachmentError = ''
      try {
        await deleteEventAttachment(this.activeAttachmentEvent.id, item.id)
        this.$message.success('附件已删除')
        await this.loadAttachments()
      } catch (error) {
        this.attachmentError = errorMessage(error)
      } finally {
        this.deletingAttachmentId = ''
      }
    },
    formatFileSize(value) {
      const bytes = Number(value || 0)
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
    },
    async submitAction(form) {
      if (this.submitting || !this.activeEvent) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.actionType === 'accept') {
          await acceptEvent(this.activeEvent.id, { version: form.version, remark: form.remark || null })
        } else if (this.actionType === 'reject') {
          await rejectEvent(this.activeEvent.id, form.version, form.reason)
        } else if (this.actionType === 'cancel') {
          await cancelEvent(this.activeEvent.id, form.version, form.reason)
        } else if (this.actionType === 'assign') {
          await assignEvent(this.activeEvent.id, {
            version: form.version,
            assigneeUserId: String(form.assigneeUserId),
            taskTitle: form.taskTitle,
            taskDescription: form.taskDescription || null,
            priority: form.priority,
            dueAt: form.dueAt || null,
            remark: form.remark || null
          })
        }
        this.$message.success(`${this.actionTitle}成功`)
        this.actionVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>
