<template>
  <section>
    <ResourceListView
      ref="resource"
      title="社区公告"
      description="按当前账号范围查看公告；具备发布权限时可创建、发布或撤回公告。"
      :fetcher="fetchAnnouncements"
      :columns="columns"
      :status-options="statuses"
      search-label="公告"
      search-placeholder="公告编号、标题或正文"
      :manage-permission="managePermission"
      action-label="新建公告"
      :action-column-width="280"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="公告发布概览"
          description="统计当前权限范围内的草稿、已发布和已撤回公告。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          :groups="[]"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button type="text" @click="openDetail(row)">查看</el-button>
        <el-button v-if="canManage && row.status === 'DRAFT'" type="text" @click="openEdit(row)">编辑</el-button>
        <el-button v-if="canManage && row.status === 'DRAFT'" type="text" @click="publish(row)">发布</el-button>
        <el-button v-if="canManage && row.status === 'PUBLISHED'" type="text" class="danger-text" @click="withdraw(row)">撤回</el-button>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      :title="formMode === 'create' ? '新建公告' : '编辑公告'"
      :description="formMode === 'create' ? '公告保存为草稿，发布后才会进入对应受众的通知列表。' : '草稿可以继续编辑，已发布公告不可直接改写。'"
      :value="formData"
      :fields="formFields"
      :rules="formRules"
      :submitting="submitting"
      :error="dialogError"
      width="720px"
      :confirm-text="formMode === 'create' ? '保存草稿' : '保存修改'"
      @submit="saveAnnouncement"
    />

    <el-dialog :visible.sync="detailVisible" :title="detailTitle" width="700px" append-to-body>
      <el-alert v-if="detailError" class="dialog-alert" :title="detailError" type="error" show-icon :closable="false" />
      <div v-loading="detailLoading" class="announcement-detail">
        <p class="announcement-detail-meta">{{ detailItem.scopeLabel || detailItem.audienceScope || '当前权限范围' }} · {{ statusLabel(detailItem.status) }}</p>
        <h2>{{ detailItem.title || '公告详情' }}</h2>
        <p class="announcement-detail-time">{{ detailItem.publishedAt || detailItem.createdAt || '尚未发布' }}</p>
        <div class="announcement-detail-body">{{ detailItem.content || detailItem.body || '暂无正文' }}</div>
      </div>
      <div slot="footer" class="dialog-footer"><el-button @click="detailVisible = false">关闭</el-button></div>
    </el-dialog>
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import {
  createAnnouncement,
  listAnnouncements,
  publishAnnouncement,
  updateAnnouncement,
  withdrawAnnouncement
} from '../../api/announcements'
import { listCommunities } from '../../api/grids'
import { errorMessage, formatDateTime } from '../../utils/data'

const STATUS_LABELS = { DRAFT: '草稿', PUBLISHED: '已发布', WITHDRAWN: '已撤回' }

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

export default {
  name: 'AnnouncementListView',
  components: { ResourceListView, FormDialog, InsightOverview },
  data() {
    return {
      listAnnouncements,
      insight: {},
      insightLoading: false,
      insightError: '',
      formVisible: false,
      formMode: 'create',
      activeAnnouncement: null,
      formData: {},
      submitting: false,
      dialogError: '',
      detailVisible: false,
      detailLoading: false,
      detailError: '',
      detailItem: {},
      communityOptions: [],
      statuses: Object.keys(STATUS_LABELS).map(value => ({ value, label: STATUS_LABELS[value] })),
      columns: [
        { prop: 'announcementNo', label: '公告编号', minWidth: 160 },
        { prop: 'title', label: '标题', minWidth: 230 },
        { prop: 'audienceScope', label: '范围', minWidth: 120, labels: { GLOBAL: '全局', COMMUNITY: '社区' } },
        { prop: 'status', label: '状态', width: 110, labels: STATUS_LABELS },
        { prop: 'publishedAt', label: '发布时间', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    isAdmin() {
      return this.$store.getters['session/hasRole']('SYSTEM_ADMIN')
    },
    isCommunity() {
      return this.$store.getters['session/hasRole']('COMMUNITY_STAFF')
    },
    managePermission() {
      if (this.isAdmin) return 'announcement:global:write'
      if (this.isCommunity) return 'announcement:community:write'
      return ''
    },
    canManage() {
      return Boolean(this.managePermission && this.$store.getters['session/hasPermission'](this.managePermission))
    },
    formFields() {
      const fields = [
        { prop: 'title', label: '标题', required: true, maxlength: 160 },
        { prop: 'content', label: '正文', type: 'textarea', required: true, rows: 8, maxlength: 10000 },
        { prop: 'pinned', label: '置顶', type: 'switch', activeText: '置顶', inactiveText: '普通', span: 12 }
      ]
      if (this.isAdmin) {
        fields.push({
          prop: 'scope',
          label: '发布范围',
          type: 'select',
          required: true,
          options: [{ value: 'GLOBAL', label: '全局' }, { value: 'COMMUNITY', label: '社区范围' }],
          span: 12
        })
        fields.push({ prop: 'communityId', label: '目标社区', type: 'select', options: this.communityOptions, show: form => form.scope === 'COMMUNITY', span: 12 })
      }
      return fields
    },
    formRules() {
      return {
        title: [{ required: true, min: 2, max: 160, message: '标题需为 2—160 个字符', trigger: 'blur' }],
        content: [{ required: true, min: 2, max: 10000, message: '请填写公告正文', trigger: 'blur' }]
      }
    },
    insightMetrics() {
      return [
        { key: 'total', label: '公告总数', value: this.insight.total, note: '当前权限范围' },
        { key: 'draft', label: '待发布', value: this.insight.draft, note: '需要继续编辑', tone: Number(this.insight.draft) ? 'warning' : 'positive' },
        { key: 'published', label: '已发布', value: this.insight.published, note: '当前可见' },
        { key: 'withdrawn', label: '已撤回', value: this.insight.withdrawn, note: '保留流转记录' }
      ]
    },
    detailTitle() {
      return this.detailItem.title ? `公告 · ${this.detailItem.title}` : '公告详情'
    }
  },
  created() {
    this.loadCommunities()
    this.loadInsight()
  },
  methods: {
    async fetchAnnouncements(params) {
      const result = await listAnnouncements(params)
      if (Array.isArray(result)) return { items: result, total: result.length, page: params.page, size: params.size }
      return result
    },
    async loadCommunities() {
      if (!this.isAdmin) return
      try {
        const result = await listCommunities()
        const items = Array.isArray(result) ? result : (result && result.items) || []
        this.communityOptions = items.map(item => ({ value: String(item.id), label: item.name || item.areaName || item.code }))
      } catch (error) {
        this.communityOptions = []
      }
    },
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        const result = await listAnnouncements({ page: 1, size: 100 })
        const items = asItems(result)
        this.insight = {
          total: Number(result && result.total) || items.length,
          draft: items.filter(item => item.status === 'DRAFT').length,
          published: items.filter(item => item.status === 'PUBLISHED').length,
          withdrawn: items.filter(item => item.status === 'WITHDRAWN').length
        }
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    openCreate() {
      this.formMode = 'create'
      this.activeAnnouncement = null
      this.formData = { title: '', content: '', scope: this.isAdmin ? 'GLOBAL' : 'COMMUNITY', pinned: false }
      this.dialogError = ''
      this.formVisible = true
    },
    openEdit(row) {
      this.formMode = 'edit'
      this.activeAnnouncement = row
      this.formData = { ...row, content: row.content || row.body || '', pinned: Boolean(row.pinned), version: row.version }
      this.dialogError = ''
      this.formVisible = true
    },
    async openDetail(row) {
      this.detailVisible = true
      this.detailLoading = false
      this.detailError = ''
      this.detailItem = row || {}
    },
    async saveAnnouncement(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.formMode === 'create') {
          await createAnnouncement({ title: form.title, content: form.content, audienceScope: form.scope || 'COMMUNITY', communityId: form.communityId || null, pinned: Boolean(form.pinned) })
        } else {
          await updateAnnouncement(this.activeAnnouncement.id, {
            title: form.title,
            content: form.content,
            pinned: Boolean(form.pinned),
            version: form.version
          })
        }
        this.$message.success('公告草稿已保存')
        this.formVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async publish(row) {
      try {
        await this.$confirm(`确认发布公告“${row.title}”？`, '发布公告', { type: 'info' })
        await publishAnnouncement(row.id, row.version)
        this.$message.success('公告已发布')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    },
    async withdraw(row) {
      try {
        await this.$confirm(`确认撤回公告“${row.title}”？`, '撤回公告', { type: 'warning' })
        await withdrawAnnouncement(row.id, row.version, '公告维护撤回')
        this.$message.success('公告已撤回')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    },
    statusLabel(value) {
      return STATUS_LABELS[value] || value || '未知状态'
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '—'
    }
  }
}
</script>

<style scoped>
.announcement-detail-meta { margin: 0 0 8px; color: var(--accent); font-family: var(--font-utility); font-size: 11px; }
.announcement-detail h2 { margin: 0; color: var(--ink); font-family: var(--font-display); font-size: 25px; }
.announcement-detail-time { margin: 8px 0 18px; color: var(--muted); font-size: 12px; }
.announcement-detail-body { white-space: pre-wrap; color: var(--ink); line-height: 1.8; }
</style>
