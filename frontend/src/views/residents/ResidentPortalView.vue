<template>
  <section class="resident-portal">
    <PageHeader
      title="居民服务台"
      description="查看本人社区档案，提交民生事项，并跟踪每一次回应。"
    />

    <el-alert v-if="error" class="state-alert" :title="error" type="error" show-icon :closable="false">
      <el-button slot="default" type="text" @click="load">重新加载</el-button>
    </el-alert>

    <template v-else>
      <article v-loading="loading" class="resident-identity">
        <div class="identity-seal" aria-hidden="true">民</div>
        <div class="identity-copy">
          <p>已核验居民档案</p>
          <h2>{{ profile.realName || '居民' }}</h2>
          <span>{{ profile.residentNo || '档案加载中' }}</span>
        </div>
        <dl>
          <div><dt>所属网格</dt><dd>{{ display(profile.gridName) }}</dd></div>
          <div><dt>家庭户</dt><dd>{{ display(profile.householdNo) }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ display(profile.phoneMasked) }}</dd></div>
          <div><dt>联系地址</dt><dd>{{ display(profile.address) }}</dd></div>
        </dl>
      </article>

      <div class="resident-workspace">
        <article class="resident-report-card">
          <header>
            <p>NEW REQUEST</p>
            <h2>上报一件民生事项</h2>
            <span>事项自动归入你的责任网格，工作人员可在后台受理和流转。</span>
          </header>
          <el-alert v-if="formError" class="form-alert" :title="formError" type="error" show-icon :closable="false" />
          <el-form ref="reportForm" :model="form" :rules="rules" label-position="top" @submit.native.prevent="submit">
            <div class="report-grid">
              <el-form-item label="事项类别" prop="categoryId">
                <el-select v-model="form.categoryId" placeholder="请选择类别" filterable :disabled="Boolean(createdReportEvent)">
                  <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="紧急程度" prop="severity">
                <el-select v-model="form.severity" :disabled="Boolean(createdReportEvent)">
                  <el-option label="一般" value="LOW" />
                  <el-option label="中等" value="MEDIUM" />
                  <el-option label="较高" value="HIGH" />
                  <el-option label="紧急" value="URGENT" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="事项标题" prop="title">
              <el-input v-model.trim="form.title" maxlength="160" placeholder="用一句话说明需要处理的事情" :disabled="Boolean(createdReportEvent)" />
            </el-form-item>
            <el-form-item label="详细情况" prop="description">
              <el-input v-model.trim="form.description" type="textarea" :rows="5" maxlength="10000" show-word-limit placeholder="发生了什么、持续多久、希望如何处理" :disabled="Boolean(createdReportEvent)" />
            </el-form-item>
            <el-form-item label="发生地址（选填）" prop="address">
              <el-input v-model.trim="form.address" maxlength="255" :placeholder="profile.address || '不填写时使用档案地址'" :disabled="Boolean(createdReportEvent)" />
            </el-form-item>
            <section class="attachment-picker resident-report-attachments" aria-label="上报附件">
              <div class="attachment-picker-heading">
                <div>
                  <strong>现场附件（选填）</strong>
                  <p>支持 JPEG、PNG、PDF；单个文件不超过 10 MB，最多 20 个。事项生成后会自动上传。</p>
                </div>
                <span v-if="createdReportEvent" class="attachment-event-badge">已生成 {{ createdReportEvent.eventNo }}</span>
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
                :on-exceed="handleFileExceed"
              >
                <el-button size="small" icon="el-icon-paperclip" :disabled="submitting">选择附件</el-button>
              </el-upload>
              <p v-if="reportUploadSummary" class="attachment-upload-summary" role="status">{{ reportUploadSummary }}</p>
            </section>
            <el-button type="primary" :loading="submitting" @click="submit">
              {{ createdReportEvent ? '重试失败附件' : '提交事项' }}
            </el-button>
          </el-form>
        </article>

        <article class="resident-history">
          <header>
            <div>
              <p>MY LEDGER</p>
              <h2>我的事项记录</h2>
            </div>
            <span>{{ events.length }} 条</span>
          </header>
          <div v-if="!events.length" class="history-empty">
            <b aria-hidden="true">0</b>
            <strong>还没有上报记录</strong>
            <p>提交后，受理、派发、处置和办结状态会保留在这里。</p>
          </div>
          <ol v-else class="event-ledger">
            <li v-for="item in events" :key="item.id">
              <div class="ledger-line" aria-hidden="true" />
              <div class="ledger-heading">
                <span>{{ item.eventNo }}</span>
                <el-tag size="mini" effect="plain" :type="statusType(item.status)">{{ eventStatus[item.status] || item.status }}</el-tag>
              </div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.categoryName }} · {{ item.gridName }} · {{ formatDate(item.reportedAt) }}</p>
              <div class="ledger-actions">
                <small v-if="item.resultSummary">处理结果：{{ item.resultSummary }}</small>
                <el-button type="text" size="mini" @click="openAttachments(item)">附件</el-button>
              </div>
            </li>
          </ol>
        </article>
      </div>
    </template>

    <el-dialog
      :visible.sync="attachmentVisible"
      :title="attachmentDialogTitle"
      width="700px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!attachmentUploading"
    >
      <p class="dialog-description">仅展示本人上报事项的附件；删除后不会影响事项的处理记录。</p>
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
              <p>{{ formatFileSize(item.fileSize) }} · {{ formatDate(item.createdAt) }}</p>
            </div>
            <div class="attachment-item-actions">
              <el-button type="text" :loading="downloadingAttachmentId === item.id" @click="downloadAttachment(item)">下载</el-button>
              <el-button
                v-if="canDeleteResidentAttachment(item)"
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
        <div v-else-if="!attachmentLoading" class="attachment-empty">当前事项暂无附件</div>

        <section v-if="canEditResidentAttachments" class="attachment-picker attachment-detail-picker">
          <div class="attachment-picker-heading">
            <div>
              <strong>添加附件</strong>
              <p>可补充现场材料；上传失败的文件会保留，可直接重试。</p>
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
            :on-exceed="handleFileExceed"
          >
            <el-button size="small" icon="el-icon-paperclip" :disabled="attachmentUploading">选择附件</el-button>
          </el-upload>
          <p v-if="detailUploadSummary" class="attachment-upload-summary" role="status">{{ detailUploadSummary }}</p>
        </section>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button :disabled="attachmentUploading" @click="attachmentVisible = false">关闭</el-button>
        <el-button
          v-if="canEditResidentAttachments && detailUploadFiles.length"
          type="primary"
          :loading="attachmentUploading"
          @click="uploadDetailAttachments"
        >
          上传所选附件
        </el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import PageHeader from '../../components/PageHeader.vue'
import {
  deleteResidentEventAttachment,
  downloadResidentEventAttachment,
  getResidentOverview,
  listResidentEventAttachments,
  reportResidentEvent,
  uploadResidentEventAttachment
} from '../../api/residentPortal'
import { EVENT_STATUS, STATUS_TAG_TYPE } from '../../constants/domain'
import { errorMessage, formatDateTime } from '../../utils/data'

function emptyForm() {
  return { categoryId: '', severity: 'MEDIUM', title: '', description: '', address: '' }
}

export default {
  name: 'ResidentPortalView',
  components: { PageHeader },
  data() {
    return {
      loading: false,
      submitting: false,
      error: '',
      formError: '',
      profile: {},
      categories: [],
      events: [],
      eventStatus: EVENT_STATUS,
      form: emptyForm(),
      createdReportEvent: null,
      reportUploadFiles: [],
      attachmentVisible: false,
      activeAttachmentEvent: null,
      attachments: [],
      attachmentLoading: false,
      attachmentUploading: false,
      attachmentError: '',
      detailUploadFiles: [],
      downloadingAttachmentId: '',
      deletingAttachmentId: '',
      rules: {
        categoryId: [{ required: true, message: '请选择事项类别', trigger: 'change' }],
        severity: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
        title: [{ required: true, min: 2, max: 160, message: '标题需为 2-160 个字符', trigger: 'blur' }],
        description: [{ required: true, min: 5, max: 10000, message: '请填写至少 5 个字符的详细情况', trigger: 'blur' }]
      }
    }
  },
  computed: {
    reportUploadSummary() {
      return this.uploadSummary(this.reportUploadFiles, '点击“重试失败附件”可继续上传。')
    },
    detailUploadSummary() {
      return this.uploadSummary(this.detailUploadFiles, '点击“上传所选附件”可继续上传。')
    },
    attachmentDialogTitle() {
      if (!this.activeAttachmentEvent) return '事项附件'
      return `${this.activeAttachmentEvent.eventNo || '事项'} · 附件`
    },
    canEditResidentAttachments() {
      return Boolean(this.activeAttachmentEvent && this.activeAttachmentEvent.status === 'REPORTED')
    },
    currentUserId() {
      const user = this.$store.state.session.user || {}
      return user.id == null ? '' : String(user.id)
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
        const overview = await getResidentOverview()
        this.profile = overview.profile || {}
        this.categories = overview.categories || []
        this.events = overview.events || []
      } catch (error) {
        this.error = errorMessage(error)
      } finally {
        this.loading = false
      }
    },
    submit() {
      if (this.submitting) return
      this.$refs.reportForm.validate(async valid => {
        if (!valid) return
        this.submitting = true
        this.formError = ''
        try {
          if (!this.createdReportEvent) {
            this.createdReportEvent = await reportResidentEvent({ ...this.form, address: this.form.address || null })
          }
          const failed = await this.uploadFiles(this.createdReportEvent.id, this.reportUploadFiles)
          if (failed) {
            this.formError = `事项已提交，但有 ${failed} 个附件上传失败；请重试失败附件。`
            return
          }
          const attachmentCount = this.reportUploadFiles.length
          this.$message.success(attachmentCount
            ? `事项已提交，${attachmentCount} 个附件上传成功`
            : '事项已提交，社区工作人员将在后台受理')
          this.form = emptyForm()
          this.createdReportEvent = null
          this.reportUploadFiles = []
          if (this.$refs.reportUpload) this.$refs.reportUpload.clearFiles()
          this.$nextTick(() => this.$refs.reportForm && this.$refs.reportForm.clearValidate())
          await this.load()
        } catch (error) {
          this.formError = errorMessage(error)
        } finally {
          this.submitting = false
        }
      })
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    formatDate(value) {
      return formatDateTime(value)
    },
    statusType(status) {
      return STATUS_TAG_TYPE[status] || 'info'
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
    uploadSummary(files, retryText) {
      if (!files.length) return ''
      const success = files.filter(file => file.status === 'success').length
      const failed = files.filter(file => file.status === 'fail').length
      const uploading = files.filter(file => file.status === 'uploading').length
      if (uploading) return `正在上传 ${uploading} 个附件…`
      if (failed) return `${success} 个上传成功，${failed} 个失败；${retryText}`
      return success === files.length
        ? `${success} 个附件已上传成功。`
        : `已选择 ${files.length} 个附件。`
    },
    async uploadFiles(eventId, files) {
      let failed = 0
      for (const file of files) {
        if (!file.raw || file.status === 'success') continue
        this.$set(file, 'status', 'uploading')
        this.$set(file, 'percentage', 0)
        try {
          const attachment = await uploadResidentEventAttachment(eventId, file.raw, progress => {
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
        const result = await listResidentEventAttachments(this.activeAttachmentEvent.id)
        this.attachments = Array.isArray(result) ? result : (result && result.items) || []
      } catch (error) {
        this.attachmentError = errorMessage(error)
      } finally {
        this.attachmentLoading = false
      }
    },
    async uploadDetailAttachments() {
      if (!this.canEditResidentAttachments || this.attachmentUploading) return
      this.attachmentUploading = true
      this.attachmentError = ''
      try {
        const failed = await this.uploadFiles(this.activeAttachmentEvent.id, this.detailUploadFiles)
        await this.loadAttachments()
        this.detailUploadFiles = this.detailUploadFiles.filter(file => file.status !== 'success')
        if (failed) this.attachmentError = `有 ${failed} 个附件上传失败，可直接重试。`
        else this.$message.success('附件上传成功')
      } finally {
        this.attachmentUploading = false
      }
    },
    async downloadAttachment(item) {
      if (!this.activeAttachmentEvent || this.downloadingAttachmentId) return
      this.downloadingAttachmentId = item.id
      this.attachmentError = ''
      try {
        const response = await downloadResidentEventAttachment(this.activeAttachmentEvent.id, item.id)
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
        this.downloadingAttachmentId = ''
      }
    },
    async removeAttachment(item) {
      if (!this.canDeleteResidentAttachment(item) || this.deletingAttachmentId) return
      try {
        await this.$confirm(`确认删除附件“${item.originalName}”？`, '删除附件', { type: 'warning' })
      } catch (error) {
        return
      }
      this.deletingAttachmentId = item.id
      this.attachmentError = ''
      try {
        await deleteResidentEventAttachment(this.activeAttachmentEvent.id, item.id)
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
    canDeleteResidentAttachment(item) {
      return Boolean(
        this.canEditResidentAttachments &&
        item &&
        item.uploadedBy != null &&
        String(item.uploadedBy) === this.currentUserId
      )
    }
  }
}
</script>

<style scoped>
.resident-portal { max-width: 1500px; margin: 0 auto; }
.resident-identity { display: grid; grid-template-columns: 76px minmax(180px, .65fr) 1.6fr; gap: 24px; align-items: center; margin-bottom: 22px; padding: 24px 28px; color: #f3f8f5; background: linear-gradient(120deg, var(--sidebar), #205c50); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.identity-seal { display: grid; width: 64px; height: 64px; place-items: center; color: var(--sidebar-deep); background: #dcebe4; border: 4px double rgba(20,60,53,.45); border-radius: 50%; font-family: var(--font-display); font-size: 28px; font-weight: 700; }
.identity-copy p, .resident-report-card header p, .resident-history header p { margin: 0 0 6px; color: #a9c8bd; font-family: var(--font-utility); font-size: 11px; letter-spacing: .12em; }
.identity-copy h2 { margin: 0 0 5px; font-family: var(--font-display); font-size: 27px; }
.identity-copy span { color: #bfd2cb; font-family: var(--font-utility); font-size: 12px; }
.resident-identity dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 26px; margin: 0; }
.resident-identity dl div { padding-left: 14px; border-left: 2px solid rgba(220,235,228,.3); }
.resident-identity dt { margin-bottom: 4px; color: #a9c0b8; font-size: 12px; }
.resident-identity dd { margin: 0; line-height: 1.45; }
.resident-workspace { display: grid; grid-template-columns: minmax(0, 1.02fr) minmax(360px, .98fr); gap: 22px; }
.resident-report-card, .resident-history { padding: 28px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.resident-report-card { border-top: 4px solid var(--signal); }
.resident-report-card header h2, .resident-history header h2 { margin: 0 0 8px; font-family: var(--font-display); font-size: 25px; }
.resident-report-card header > span { display: block; margin-bottom: 24px; color: var(--muted); line-height: 1.65; }
.resident-report-card header p, .resident-history header p { color: var(--accent); }
.form-alert { margin-bottom: 18px; }
.report-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.report-grid .el-select { width: 100%; }
.resident-history header { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 18px; border-bottom: 1px solid var(--line); }
.resident-history header > span { padding: 5px 9px; color: var(--accent-strong); background: var(--accent-soft); border-radius: 3px; font-family: var(--font-utility); font-size: 12px; }
.history-empty { padding: 70px 20px; color: var(--muted); text-align: center; }
.history-empty b { display: block; color: #c9d4cf; font-family: var(--font-utility); font-size: 56px; }
.history-empty strong { display: block; margin: 10px 0; color: var(--ink); }
.event-ledger { margin: 0; padding: 0; list-style: none; }
.event-ledger li { position: relative; padding: 22px 0 22px 24px; border-bottom: 1px solid var(--line); }
.event-ledger li:last-child { border-bottom: 0; }
.ledger-line { position: absolute; top: 29px; left: 0; width: 9px; height: 9px; background: var(--accent); border: 2px solid var(--surface); border-radius: 50%; box-shadow: 0 0 0 1px var(--accent); }
.ledger-heading { display: flex; justify-content: space-between; gap: 12px; }
.ledger-heading > span { color: var(--accent); font-family: var(--font-utility); font-size: 12px; }
.event-ledger h3 { margin: 8px 0; font-size: 16px; }
.event-ledger p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.6; }
.event-ledger small { display: block; margin-top: 10px; color: var(--muted-strong); }
.ledger-actions { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; }
.ledger-actions .el-button { flex: none; margin-top: 7px; }
@media (max-width: 560px) { .ledger-actions { align-items: flex-start; flex-direction: column; gap: 2px; } .ledger-actions .el-button { margin-top: 0; } }
@media (max-width: 1080px) { .resident-workspace { grid-template-columns: 1fr; } }
@media (max-width: 760px) { .resident-identity { grid-template-columns: 58px 1fr; padding: 22px 18px; } .identity-seal { width: 50px; height: 50px; font-size: 22px; } .resident-identity dl { grid-column: 1 / -1; grid-template-columns: 1fr; } .report-grid { grid-template-columns: 1fr; gap: 0; } .resident-report-card, .resident-history { padding: 22px 18px; } }
</style>
