<template>
  <section>
    <ResourceListView
      ref="resource"
      title="社区服务目录"
      description="维护居民可申请的社区服务项目、说明、排序和启停状态。"
      :fetcher="fetchCatalogs"
      :columns="columns"
      :status-options="statuses"
      search-label="服务"
      search-placeholder="服务编码、名称或说明"
      manage-permission="service:catalog:manage"
      action-label="新增服务"
      :action-column-width="110"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="服务目录概览"
          description="保证居民申请入口只呈现当前启用且可解释的服务。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button type="text" @click="openEdit(row)">编辑</el-button>
        <el-button type="text" :class="{ 'danger-text': row.status === 'ENABLED' }" @click="toggleStatus(row)">
          {{ row.status === 'ENABLED' ? '停用' : '启用' }}
        </el-button>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      :title="formMode === 'create' ? '新增服务项目' : '编辑服务项目'"
      description="服务编码用于系统识别，创建后不可修改。"
      :value="formData"
      :fields="formFields"
      :rules="formRules"
      :submitting="submitting"
      :error="dialogError"
      @submit="saveCatalog"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import {
  createServiceCatalog,
  listSystemServiceCatalogs,
  updateServiceCatalog
} from '../../api/services'
import { errorMessage } from '../../utils/data'

const STATUS_LABELS = { ENABLED: '启用', DISABLED: '停用' }

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

export default {
  name: 'ServiceCatalogListView',
  components: { ResourceListView, FormDialog, InsightOverview },
  data() {
    return {
      listSystemServiceCatalogs,
      insight: {},
      insightLoading: false,
      insightError: '',
      formVisible: false,
      formMode: 'create',
      activeCatalog: null,
      formData: {},
      submitting: false,
      dialogError: '',
      statuses: Object.keys(STATUS_LABELS).map(value => ({ value, label: STATUS_LABELS[value] })),
      columns: [
        { prop: 'code', label: '服务编码', minWidth: 170 },
        { prop: 'name', label: '服务名称', minWidth: 180 },
        { prop: 'description', label: '服务说明', minWidth: 260 },
        { prop: 'sortNo', label: '排序', width: 90 },
        { prop: 'status', label: '状态', width: 100, labels: STATUS_LABELS }
      ]
    }
  },
  computed: {
    formFields() {
      return [
        { prop: 'code', label: '服务编码', required: true, maxlength: 50, disabled: this.formMode === 'edit', span: 12 },
        { prop: 'name', label: '服务名称', required: true, maxlength: 100, span: 12 },
        { prop: 'sortNo', label: '排序号', required: true, span: 12 },
        { prop: 'description', label: '服务说明', type: 'textarea', rows: 4, maxlength: 500 },
        { prop: 'status', label: '状态', type: 'select', required: true, options: this.statuses, visible: this.formMode === 'edit', span: 12 }
      ]
    },
    formRules() {
      return {
        code: [{ pattern: /^[A-Z][A-Z0-9_]{0,49}$/, message: '编码须为大写字母、数字或下划线', trigger: 'blur' }],
        name: [{ required: true, max: 100, message: '服务名称不能为空', trigger: 'blur' }]
      }
    },
    insightMetrics() {
      return [
        { key: 'total', label: '服务总数', value: this.insight.total, note: '目录项目' },
        { key: 'enabled', label: '当前启用', value: this.insight.enabled, note: '居民可申请', tone: 'positive' },
        { key: 'disabled', label: '已停用', value: this.insight.disabled, note: '不再接受新申请', tone: 'warning' },
        { key: 'pending', label: '待处理申请', value: this.insight.pending, note: '需进入服务队列', tone: Number(this.insight.pending) ? 'warning' : 'positive' }
      ]
    }
  },
  created() {
    this.loadInsight()
  },
  methods: {
    fetchCatalogs(params) {
      return listSystemServiceCatalogs(params).then(result => {
        if (Array.isArray(result)) return { items: result, total: result.length, page: params.page, size: params.size }
        return result
      })
    },
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        const result = await listSystemServiceCatalogs({ page: 1, size: 100 })
        const items = asItems(result)
        this.insight = {
          total: Number(result && result.total) || items.length,
          enabled: items.filter(item => item.status === 'ENABLED').length,
          disabled: items.filter(item => item.status === 'DISABLED').length,
          pending: Number(result && result.pendingApplications) || 0
        }
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    openCreate() {
      this.formMode = 'create'
      this.activeCatalog = null
      this.formData = { code: '', name: '', description: '', sortNo: 0, status: 'ENABLED' }
      this.dialogError = ''
      this.formVisible = true
    },
    openEdit(row) {
      this.formMode = 'edit'
      this.activeCatalog = row
      this.formData = { ...row, code: row.code || row.serviceCode, version: row.version }
      this.dialogError = ''
      this.formVisible = true
    },
    async saveCatalog(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.formMode === 'create') {
          await createServiceCatalog({ code: form.code, name: form.name, description: form.description || null, sortNo: Number(form.sortNo), status: form.status || 'ENABLED' })
        } else {
          await updateServiceCatalog(this.activeCatalog.id, {
            name: form.name,
            description: form.description || null,
            sortNo: Number(form.sortNo),
            status: form.status,
            version: form.version
          })
        }
        this.$message.success('服务目录已保存')
        this.formVisible = false
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async toggleStatus(row) {
      const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      try {
        await this.$confirm(next === 'DISABLED' ? '停用后居民不能提交新的该类申请，确认继续？' : '确认重新启用该服务？', next === 'DISABLED' ? '停用服务' : '启用服务', { type: next === 'DISABLED' ? 'warning' : 'info' })
        await updateServiceCatalog(row.id, {
          name: row.name,
          description: row.description || null,
          sortNo: Number(row.sortNo || 0),
          status: next,
          version: row.version
        })
        this.$message.success(next === 'DISABLED' ? '服务已停用' : '服务已启用')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    }
  }
}
</script>
