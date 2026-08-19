<template>
  <section>
    <ResourceListView
      ref="resource"
      :title="pageTitle"
      :description="pageDescription"
      :fetcher="listTasks"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="任务编号或标题"
      manage-permission="task:create"
      action-label="新建巡查任务"
      :action-column-width="290"
      :view-options="viewOptions"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="任务执行概览"
          description="观察任务负荷、逾期风险、执行阶段与优先级结构。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          :groups="insightGroups"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button v-if="can('task:read')" type="text" @click="openFlows(row)">流转</el-button>
        <el-button v-if="can('task:read')" type="text" @click="openTaskAttachments(row)">附件</el-button>
        <el-button
          v-if="row.status === 'PENDING_ACCEPT' && can('task:accept')"
          type="text"
          @click="openAction('accept', row)"
        >
          接单
        </el-button>
        <el-button
          v-if="row.status === 'PROCESSING' && can('task:handle')"
          type="text"
          @click="openAction('submit', row)"
        >
          提交处置
        </el-button>
        <el-button
          v-if="row.status === 'PENDING_REVIEW' && can('task:review')"
          type="text"
          @click="openAction('review', row)"
        >
          复核
        </el-button>
        <el-button
          v-if="row.status === 'PENDING_ACCEPT' && !row.sourceEventId && can('task:cancel')"
          type="text"
          class="danger-text"
          @click="openAction('cancel', row)"
        >
          取消
        </el-button>
      </template>
      <template #alternate="{ view, items }">
        <RecordCardGrid
          v-if="view === 'card'"
          :items="items"
          title-prop="title"
          eyebrow-prop="taskNo"
          status-prop="status"
          :status-labels="taskStatusLabels"
        >
          <template #default="{ item }">
            <dl class="record-meta">
              <div><dt>优先级</dt><dd>{{ priorityLabel(item.priority) }}</dd></div>
              <div><dt>责任网格</dt><dd>{{ display(item.gridName) }}</dd></div>
              <div><dt>执行人</dt><dd>{{ display(item.assigneeName) }}</dd></div>
              <div><dt>派发人</dt><dd>{{ display(item.dispatcherName) }}</dd></div>
              <div><dt>来源事件</dt><dd>{{ display(item.sourceEventNo) }}</dd></div>
              <div><dt>截止时间</dt><dd :class="{ 'overdue-text': isOverdue(item) }">{{ formatDate(item.dueAt) }}</dd></div>
            </dl>
          </template>
          <template #actions="{ item }">
            <el-button v-if="can('task:read')" type="text" @click="openFlows(item)">流转</el-button>
            <el-button v-if="can('task:read')" type="text" @click="openTaskAttachments(item)">附件</el-button>
            <el-button
              v-if="item.status === 'PENDING_ACCEPT' && can('task:accept')"
              type="text"
              @click="openAction('accept', item)"
            >
              接单
            </el-button>
            <el-button
              v-if="item.status === 'PROCESSING' && can('task:handle')"
              type="text"
              @click="openAction('submit', item)"
            >
              提交处置
            </el-button>
            <el-button
              v-if="item.status === 'PENDING_REVIEW' && can('task:review')"
              type="text"
              @click="openAction('review', item)"
            >
              复核
            </el-button>
            <el-button
              v-if="item.status === 'PENDING_ACCEPT' && !item.sourceEventId && can('task:cancel')"
              type="text"
              class="danger-text"
              @click="openAction('cancel', item)"
            >
              取消
            </el-button>
          </template>
        </RecordCardGrid>

        <section v-else-if="view === 'board'" class="flow-board task-flow-board" aria-label="任务看板">
          <p class="flow-scope-note">仅展示本页有记录的阶段；阶段较多时可横向滚动。</p>
          <div class="flow-board-scroll">
            <section
              v-for="column in visibleTaskBoardColumns(items)"
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
                  :class="{ 'is-overdue': isOverdue(item) }"
                >
                  <p>{{ item.taskNo }}</p>
                  <h4>{{ item.title }}</h4>
                  <dl>
                    <div><dt>优先级</dt><dd>{{ priorityLabel(item.priority) }}</dd></div>
                    <div><dt>执行人</dt><dd>{{ display(item.assigneeName) }}</dd></div>
                    <div><dt>截止</dt><dd>{{ formatDate(item.dueAt) }}</dd></div>
                  </dl>
                  <div v-if="hasTaskAction(item) || can('task:read')" class="flow-card-actions">
                    <el-button v-if="can('task:read')" type="text" @click="openFlows(item)">流转</el-button>
                    <el-button v-if="can('task:read')" type="text" @click="openTaskAttachments(item)">附件</el-button>
                    <el-button
                      v-if="item.status === 'PENDING_ACCEPT' && can('task:accept')"
                      type="text"
                      @click="openAction('accept', item)"
                    >
                      接单
                    </el-button>
                    <el-button
                      v-if="item.status === 'PROCESSING' && can('task:handle')"
                      type="text"
                      @click="openAction('submit', item)"
                    >
                      提交处置
                    </el-button>
                    <el-button
                      v-if="item.status === 'PENDING_REVIEW' && can('task:review')"
                      type="text"
                      @click="openAction('review', item)"
                    >
                      复核
                    </el-button>
                    <el-button
                      v-if="item.status === 'PENDING_ACCEPT' && !item.sourceEventId && can('task:cancel')"
                      type="text"
                      class="danger-text"
                      @click="openAction('cancel', item)"
                    >
                      取消
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
      :visible.sync="createVisible"
      title="新建独立巡查任务"
      description="事件派生任务应从事件派发生成；此处仅创建日常巡查或其他独立任务。"
      :value="createForm"
      :fields="createFields"
      :rules="createRules"
      :submitting="submitting"
      :error="dialogError"
      width="720px"
      confirm-text="创建任务"
      @submit="saveTask"
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
      width="680px"
      :confirm-text="actionConfirmText"
      @submit="submitAction"
    >
      <template v-if="actionType === 'submit'" #extra>
        <section class="attachment-picker" aria-label="任务处置附件">
          <div class="attachment-picker-heading">
            <div>
              <strong>处置附件</strong>
              <p>支持 JPEG、PNG、PDF；单个文件不超过 10 MB，最多 20 个。确认提交前会先上传附件。</p>
            </div>
          </div>
          <el-alert
            v-if="taskAttachmentError"
            class="dialog-alert"
            :title="taskAttachmentError"
            type="error"
            show-icon
            :closable="false"
          />
          <div v-loading="taskAttachmentLoading" class="attachment-dialog-body">
            <div v-if="taskAttachments.length" class="attachment-list">
              <article v-for="item in taskAttachments" :key="item.id" class="attachment-item">
                <span class="attachment-file-icon" aria-hidden="true"><i class="el-icon-document" /></span>
                <div>
                  <strong>{{ item.originalName }}</strong>
                  <p>{{ formatFileSize(item.fileSize) }} · {{ formatDate(item.createdAt) }}</p>
                </div>
                <div class="attachment-item-actions">
                  <el-button type="text" :loading="downloadingAttachmentId === item.id" @click="downloadTaskFile(item)">下载</el-button>
                  <el-button
                    v-if="canDeleteTaskAttachment(item)"
                    type="text"
                    class="danger-text"
                    :loading="deletingAttachmentId === item.id"
                    @click="removeTaskFile(item)"
                  >
                    删除
                  </el-button>
                </div>
              </article>
            </div>
            <div v-else-if="!taskAttachmentLoading" class="attachment-empty">尚未添加处置附件</div>
          </div>
          <el-upload
            ref="taskUpload"
            action=""
            :auto-upload="false"
            :multiple="true"
            :limit="20"
            accept="image/jpeg,image/png,application/pdf"
            :file-list="taskUploadFiles"
            :on-change="handleTaskFileChange"
            :on-remove="handleTaskFileRemove"
            :on-exceed="handleTaskFileExceed"
          >
            <el-button size="small" icon="el-icon-paperclip" :disabled="taskAttachmentUploading">选择附件</el-button>
          </el-upload>
          <p v-if="taskUploadSummary" class="attachment-upload-summary" role="status">{{ taskUploadSummary }}</p>
        </section>
      </template>
    </FormDialog>

    <el-dialog
      :visible.sync="taskAttachmentVisible"
      :title="taskAttachmentDialogTitle"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
    >
      <p class="dialog-description">附件仅对当前任务的授权访问范围开放，可作为处置和复核依据查看。</p>
      <el-alert
        v-if="taskAttachmentError"
        class="dialog-alert"
        :title="taskAttachmentError"
        type="error"
        show-icon
        :closable="false"
      />
      <div v-loading="taskAttachmentLoading" class="attachment-dialog-body">
        <div v-if="taskAttachments.length" class="attachment-list">
          <article v-for="item in taskAttachments" :key="item.id" class="attachment-item">
            <span class="attachment-file-icon" aria-hidden="true"><i class="el-icon-document" /></span>
            <div>
              <strong>{{ item.originalName }}</strong>
              <p>{{ formatFileSize(item.fileSize) }} · {{ formatDate(item.createdAt) }}</p>
            </div>
            <div class="attachment-item-actions">
              <el-button type="text" :loading="downloadingAttachmentId === item.id" @click="downloadTaskFile(item)">下载</el-button>
              <el-button
                v-if="canDeleteTaskAttachment(item)"
                type="text"
                class="danger-text"
                :loading="deletingAttachmentId === item.id"
                @click="removeTaskFile(item)"
              >
                删除
              </el-button>
            </div>
          </article>
        </div>
        <div v-else-if="!taskAttachmentLoading" class="attachment-empty">当前任务暂无附件</div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button @click="taskAttachmentVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <el-dialog
      :visible.sync="flowVisible"
      :title="flowDialogTitle"
      width="680px"
      append-to-body
      :close-on-click-modal="false"
    >
      <p class="dialog-description">按时间顺序展示该任务的真实接单、处置、复核等操作留痕。</p>
      <el-alert
        v-if="flowError"
        class="dialog-alert"
        :title="flowError"
        type="error"
        show-icon
        :closable="false"
      />
      <div v-loading="flowLoading" class="flow-history">
        <ol v-if="taskFlows.length" class="flow-history-list">
          <li v-for="(item, index) in taskFlows" :key="item.id || `${item.createdAt}-${index}`">
            <span class="flow-history-dot" aria-hidden="true" />
            <div>
              <strong>{{ flowActionLabel(item) }}</strong>
              <p>{{ flowOperator(item) }} · {{ formatDate(item.createdAt) }}</p>
              <small v-if="item.remark">{{ item.remark }}</small>
            </div>
          </li>
        </ol>
        <div v-else-if="!flowLoading" class="attachment-empty">当前任务暂无流转记录</div>
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
import { getEvent } from '../../api/events'
import { listGrids, listWorkerOptions } from '../../api/grids'
import { getTaskInsight } from '../../api/insights'
import {
  acceptTask,
  cancelTask,
  createTask,
  deleteTaskAttachment,
  downloadTaskAttachment,
  getTask,
  listTaskAttachments,
  listTaskFlows,
  listTasks,
  reviewTask,
  submitTaskForReview,
  uploadTaskAttachment
} from '../../api/tasks'
import { TASK_STATUS } from '../../constants/domain'
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
  name: 'TaskListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid },
  data() {
    return {
      listTasks,
      insight: {},
      insightLoading: false,
      insightError: '',
      createVisible: false,
      actionVisible: false,
      createForm: {},
      actionForm: {},
      actionType: '',
      activeTask: null,
      submitting: false,
      dialogError: '',
      taskAttachmentVisible: false,
      activeAttachmentTask: null,
      taskAttachments: [],
      taskAttachmentLoading: false,
      taskAttachmentUploading: false,
      taskAttachmentError: '',
      taskUploadFiles: [],
      downloadingAttachmentId: '',
      deletingAttachmentId: '',
      flowVisible: false,
      flowLoading: false,
      flowError: '',
      activeFlowTask: null,
      taskFlows: [],
      gridOptions: [],
      workerOptions: [],
      viewOptions: [
        { value: 'list', label: '列表', icon: 'el-icon-tickets' },
        { value: 'card', label: '任务卡片', icon: 'el-icon-postcard' },
        { value: 'board', label: '执行看板', icon: 'el-icon-s-grid' }
      ],
      taskStatusLabels: TASK_STATUS,
      statuses: Object.keys(TASK_STATUS).map(value => ({ value, label: TASK_STATUS[value] })),
      columns: [
        { prop: 'taskNo', label: '任务编号', minWidth: 170 },
        { prop: 'title', label: '任务标题', minWidth: 220 },
        { prop: 'sourceEventNo', label: '来源事件', minWidth: 170 },
        { prop: 'gridName', label: '责任网格', minWidth: 180 },
        { prop: 'assigneeName', label: '执行人', minWidth: 140 },
        { prop: 'status', label: '状态', width: 120, labels: TASK_STATUS },
        { prop: 'dueAt', label: '截止时间', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    routeMode() {
      if (this.$route.name === 'grid-task') return 'mine'
      if (this.$route.name === 'grid-history') return 'history'
      return 'all'
    },
    pageTitle() {
      return { mine: '我的任务', history: '工作历史' }[this.routeMode] || '网格任务'
    },
    pageDescription() {
      return {
        mine: '只查看本人责任范围内的待接单、处理中和待复核任务。',
        history: '保留本人执行任务的状态、流转和处置结果，方便复盘。'
      }[this.routeMode] || '查看本人权限范围内的待接单、处理中和待复核任务。'
    },
    insightMetrics() {
      const active = this.breakdownCount(this.insight.statuses, 'PENDING_ACCEPT') +
        this.breakdownCount(this.insight.statuses, 'PROCESSING') +
        this.breakdownCount(this.insight.statuses, 'PENDING_REVIEW')
      const completed = this.breakdownCount(this.insight.statuses, 'COMPLETED')
      const urgent = this.breakdownCount(this.insight.priorities, 'URGENT')
      return [
        { key: 'total', label: '任务总量', value: this.insight.total, note: '权限范围内' },
        {
          key: 'active',
          label: '执行中任务',
          value: active,
          note: '待接单至待复核',
          tone: active ? 'warning' : 'positive'
        },
        { key: 'completed', label: '已完成', value: completed, note: '形成处置结果', tone: 'positive' },
        {
          key: 'overdue',
          label: '已逾期',
          value: this.insight.overdue,
          note: `另有 ${urgent} 条紧急任务`,
          tone: Number(this.insight.overdue || 0) ? 'danger' : 'positive'
        }
      ]
    },
    insightGroups() {
      return [
        {
          key: 'statuses',
          title: '执行阶段',
          items: (this.insight.statuses || []).map(item => ({
            ...item,
            label: TASK_STATUS[item.key] || item.key
          }))
        },
        {
          key: 'priorities',
          title: '优先级',
          items: (this.insight.priorities || []).map(item => ({
            ...item,
            label: this.priorityLabel(item.key)
          }))
        }
      ]
    },
    taskBoardColumns() {
      return Object.keys(TASK_STATUS).map(value => ({ value, label: TASK_STATUS[value] }))
    },
    createFields() {
      return [
        { prop: 'gridId', label: '责任网格', type: 'select', required: true, options: this.gridOptions, span: 12 },
        { prop: 'assigneeUserId', label: '执行网格员', type: 'select', required: true, options: this.workerOptions, span: 12 },
        {
          prop: 'taskType',
          label: '任务类型',
          type: 'select',
          required: true,
          span: 12,
          options: [
            { value: 'ROUTINE_INSPECTION', label: '日常巡查' },
            { value: 'OTHER', label: '其他任务' }
          ]
        },
        { prop: 'priority', label: '优先级', type: 'select', required: true, options: PRIORITY_OPTIONS, span: 12 },
        { prop: 'title', label: '任务标题', required: true, maxlength: 160 },
        { prop: 'description', label: '任务说明', type: 'textarea', rows: 4, maxlength: 10000 },
        { prop: 'dueAt', label: '截止时间', type: 'datetime', span: 12 }
      ]
    },
    createRules() {
      return {
        title: [{ required: true, min: 2, max: 160, message: '任务标题需为 2-160 个字符', trigger: 'blur' }]
      }
    },
    actionTitle() {
      return {
        accept: '接受任务',
        submit: '提交处置结果',
        review: '复核处置结果',
        cancel: '取消任务'
      }[this.actionType] || '处理任务'
    },
    actionDescription() {
      if (!this.activeTask) return ''
      const prefix = `${this.activeTask.taskNo || ''} ${this.activeTask.title || ''}`.trim()
      if (this.actionType === 'review') return `${prefix}。复核通过将办结任务，事件派生任务会同步办结事件。`
      return `${prefix}。系统将校验执行人、当前状态和最新版本。`
    },
    flowDialogTitle() {
      if (!this.activeFlowTask) return '任务流转历史'
      return `${this.activeFlowTask.taskNo || '任务'} · 流转历史`
    },
    taskAttachmentDialogTitle() {
      if (!this.activeAttachmentTask) return '任务附件'
      return `${this.activeAttachmentTask.taskNo || '任务'} · 附件`
    },
    currentUserId() {
      const user = this.$store.state.session.user || {}
      return user.id == null ? '' : String(user.id)
    },
    actionConfirmText() {
      return {
        accept: '确认接单',
        submit: '提交复核',
        review: '提交复核结果',
        cancel: '确认取消'
      }[this.actionType] || '确认'
    },
    actionFields() {
      if (this.actionType === 'submit') {
        return [
          { prop: 'handlingResult', label: '处置结果', type: 'textarea', required: true, rows: 6, maxlength: 10000 },
          { prop: 'remark', label: '补充说明', type: 'textarea', rows: 3, maxlength: 1000 }
        ]
      }
      if (this.actionType === 'review') {
        return [
          { prop: 'approved', label: '复核结论', type: 'switch', activeText: '通过', inactiveText: '退回' },
          {
            prop: 'remark',
            label: '复核意见',
            type: 'textarea',
            rows: 4,
            maxlength: 1000,
            required: true,
            show: form => form.approved === false,
            help: '复核退回时必须说明整改原因。'
          }
        ]
      }
      if (this.actionType === 'cancel') {
        return [{ prop: 'reason', label: '取消原因', type: 'textarea', required: true, rows: 4, maxlength: 1000 }]
      }
      return []
    },
    actionRules() {
      return {
        handlingResult: [{ required: this.actionType === 'submit', min: 2, max: 10000, message: '请填写处置结果', trigger: 'blur' }]
      }
    },
    taskUploadSummary() {
      if (!this.taskUploadFiles.length) return ''
      const success = this.taskUploadFiles.filter(file => file.status === 'success').length
      const failed = this.taskUploadFiles.filter(file => file.status === 'fail').length
      const uploading = this.taskUploadFiles.filter(file => file.status === 'uploading').length
      if (uploading) return `正在上传 ${uploading} 个附件…`
      if (failed) return `${success} 个上传成功，${failed} 个失败；点击确认可重试失败附件。`
      return `已选择 ${this.taskUploadFiles.length} 个附件，确认提交时自动上传。`
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
        this.insight = await getTaskInsight()
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
      return value ? formatDateTime(value) : '未设置'
    },
    isOverdue(item) {
      if (!item.dueAt || ['COMPLETED', 'CANCELLED'].includes(item.status)) return false
      return new Date(item.dueAt).getTime() < Date.now()
    },
    itemsForStatus(items, status) {
      return items.filter(item => item.status === status)
    },
    visibleTaskBoardColumns(items) {
      if (!items.length) return this.taskBoardColumns
      return this.taskBoardColumns.filter(column => this.itemsForStatus(items, column.value).length)
    },
    hasTaskAction(row) {
      return (row.status === 'PENDING_ACCEPT' && this.can('task:accept')) ||
        (row.status === 'PROCESSING' && this.can('task:handle')) ||
        (row.status === 'PENDING_REVIEW' && this.can('task:review')) ||
        (row.status === 'PENDING_ACCEPT' && !row.sourceEventId && this.can('task:cancel'))
    },
    can(permission) {
      return this.$store.getters['session/hasPermission'](permission)
    },
    canDeleteTaskAttachment(item) {
      if (!this.activeAttachmentTask || !this.can('file:delete')) return false
      if (this.activeAttachmentTask.status !== 'PROCESSING') return false
      const roles = (this.$store.state.session.user || {}).roles || []
      const manager = roles.includes('SYSTEM_ADMIN') || roles.includes('COMMUNITY_STAFF')
      return manager || (item && item.uploadedBy != null && String(item.uploadedBy) === this.currentUserId)
    },
    formatFileSize(value) {
      const bytes = Number(value || 0)
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
    },
    resetTaskAttachmentState() {
      this.activeAttachmentTask = null
      this.taskAttachments = []
      this.taskAttachmentError = ''
      this.taskUploadFiles = []
      this.downloadingAttachmentId = ''
      this.deletingAttachmentId = ''
      if (this.$refs.taskUpload) this.$refs.taskUpload.clearFiles()
    },
    async openTaskAttachments(task) {
      this.taskAttachmentVisible = true
      this.activeAttachmentTask = task
      this.taskAttachments = []
      this.taskAttachmentError = ''
      await this.loadTaskAttachments()
    },
    async loadTaskAttachments() {
      if (!this.activeAttachmentTask) return
      this.taskAttachmentLoading = true
      this.taskAttachmentError = ''
      try {
        this.taskAttachments = asItems(await listTaskAttachments(this.activeAttachmentTask.id))
      } catch (error) {
        this.taskAttachmentError = errorMessage(error)
      } finally {
        this.taskAttachmentLoading = false
      }
    },
    handleTaskFileChange(file, fileList) {
      this.taskUploadFiles = this.validateTaskFile(file, fileList)
    },
    handleTaskFileRemove(file, fileList) {
      this.taskUploadFiles = fileList
    },
    handleTaskFileExceed() {
      this.$message.warning('一次最多选择 20 个附件')
    },
    validateTaskFile(file, fileList) {
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
    async uploadTaskFiles() {
      if (!this.activeAttachmentTask || this.taskAttachmentUploading) return 0
      this.taskAttachmentUploading = true
      this.taskAttachmentError = ''
      let failed = 0
      try {
        for (const file of this.taskUploadFiles) {
          if (!file.raw || file.status === 'success') continue
          this.$set(file, 'status', 'uploading')
          this.$set(file, 'percentage', 0)
          try {
            await uploadTaskAttachment(this.activeAttachmentTask.id, file.raw, progress => {
              const total = Number(progress.total || file.raw.size || 0)
              const percentage = total ? Math.round((Number(progress.loaded || 0) / total) * 100) : 0
              this.$set(file, 'percentage', percentage)
            })
            this.$set(file, 'status', 'success')
          } catch (error) {
            failed += 1
            this.$set(file, 'status', 'fail')
            this.$set(file, 'uploadError', errorMessage(error))
          }
        }
        await this.loadTaskAttachments()
        this.taskUploadFiles = this.taskUploadFiles.filter(file => file.status !== 'success')
        if (failed) this.taskAttachmentError = `有 ${failed} 个附件上传失败，可点击确认重试。`
      } finally {
        this.taskAttachmentUploading = false
      }
      return failed
    },
    async downloadTaskFile(item) {
      if (!this.activeAttachmentTask || this.downloadingAttachmentId) return
      this.downloadingAttachmentId = item.id
      this.taskAttachmentError = ''
      try {
        const response = await downloadTaskAttachment(this.activeAttachmentTask.id, item.id)
        const url = window.URL.createObjectURL(response.data)
        const anchor = document.createElement('a')
        anchor.href = url
        anchor.download = item.originalName || `attachment-${item.id}`
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
        window.URL.revokeObjectURL(url)
      } catch (error) {
        this.taskAttachmentError = errorMessage(error)
      } finally {
        this.downloadingAttachmentId = ''
      }
    },
    async removeTaskFile(item) {
      if (!this.canDeleteTaskAttachment(item) || this.deletingAttachmentId) return
      try {
        await this.$confirm(`确认删除附件“${item.originalName}”？`, '删除附件', { type: 'warning' })
      } catch (error) {
        return
      }
      this.deletingAttachmentId = item.id
      this.taskAttachmentError = ''
      try {
        await deleteTaskAttachment(this.activeAttachmentTask.id, item.id)
        this.$message.success('附件已删除')
        await this.loadTaskAttachments()
      } catch (error) {
        this.taskAttachmentError = errorMessage(error)
      } finally {
        this.deletingAttachmentId = ''
      }
    },
    async openFlows(task) {
      this.activeFlowTask = task
      this.taskFlows = []
      this.flowError = ''
      this.flowVisible = true
      this.flowLoading = true
      try {
        this.taskFlows = asItems(await listTaskFlows(task.id))
      } catch (error) {
        this.flowError = errorMessage(error)
      } finally {
        this.flowLoading = false
      }
    },
    flowActionLabel(item) {
      const labels = {
        ASSIGN: '派发任务',
        ACCEPT: '接受任务',
        SUBMIT: '提交处置结果',
        SUBMIT_REVIEW: '提交处置结果',
        APPROVE: '复核通过',
        RETURN: '复核退回',
        CANCEL: '取消任务'
      }
      const action = labels[item.action] || item.action || '流程操作'
      const status = TASK_STATUS[item.toStatus] || item.toStatus
      return status ? `${action} · ${status}` : action
    },
    flowOperator(item) {
      return item.operatorName || item.operatorUserId || '系统'
    },
    async loadOptions() {
      const canCreate = this.can('task:create')
      const [grids, workers] = await Promise.allSettled([
        canCreate ? listGrids({ page: 1, size: 100 }) : Promise.resolve([]),
        canCreate ? listWorkerOptions() : Promise.resolve([])
      ])
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
    openCreate() {
      this.createForm = {
        gridId: '',
        taskType: 'ROUTINE_INSPECTION',
        title: '',
        description: '',
        priority: 'MEDIUM',
        assigneeUserId: '',
        dueAt: ''
      }
      this.dialogError = ''
      this.createVisible = true
      this.loadOptions()
    },
    async saveTask(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        await createTask({
          gridId: String(form.gridId),
          taskType: form.taskType,
          title: form.title,
          description: form.description || null,
          priority: form.priority,
          assigneeUserId: String(form.assigneeUserId),
          dueAt: form.dueAt || null
        })
        this.$message.success('独立任务创建成功')
        this.createVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async openAction(type, row) {
      this.dialogError = ''
      this.resetTaskAttachmentState()
      try {
        const detail = await getTask(row.id)
        let eventVersion = detail.eventVersion
        if (type === 'review' && detail.sourceEventId && eventVersion === undefined) {
          const event = await getEvent(detail.sourceEventId)
          eventVersion = event.version
        }
        this.actionType = type
        this.activeTask = detail
        this.actionForm = {
          version: detail.version,
          eventVersion,
          approved: true,
          handlingResult: detail.handlingResult || '',
          remark: '',
          reason: ''
        }
        this.actionVisible = true
        if (type === 'submit') {
          this.activeAttachmentTask = detail
          await this.loadTaskAttachments()
        }
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    async submitAction(form) {
      if (this.submitting || !this.activeTask) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.actionType === 'accept') {
          await acceptTask(this.activeTask.id, form.version)
        } else if (this.actionType === 'submit') {
          const failed = await this.uploadTaskFiles()
          if (failed || this.taskAttachmentError) {
            this.dialogError = failed
              ? `有 ${failed} 个附件上传失败，请重试失败附件后再提交处置结果。`
              : '附件列表刷新失败，请确认附件状态后再提交处置结果。'
            return
          }
          await submitTaskForReview(this.activeTask.id, {
            version: form.version,
            handlingResult: form.handlingResult,
            attachmentIds: this.taskAttachments.map(item => String(item.id)).filter(Boolean),
            remark: form.remark || null
          })
        } else if (this.actionType === 'review') {
          await reviewTask(this.activeTask.id, {
            version: form.version,
            eventVersion: this.activeTask.sourceEventId ? form.eventVersion : null,
            approved: Boolean(form.approved),
            remark: form.remark || null
          })
        } else if (this.actionType === 'cancel') {
          await cancelTask(this.activeTask.id, form.version, form.reason)
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
