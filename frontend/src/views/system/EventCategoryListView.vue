<template>
  <section>
    <ResourceListView
      ref="resource"
      title="事件类别"
      description="维护治理事件的分类名称、排序和启停状态；停用后不再出现在事件上报类别中。"
      :fetcher="fetchCategories"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="类别编码或名称"
      manage-permission="event:category:manage"
      action-label="新增类别"
      :action-column-width="90"
      @create="openCreate"
    >
      <template #rowActions="{ row }">
        <el-button type="text" @click="openEdit(row)">编辑</el-button>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      :title="formMode === 'create' ? '新增事件类别' : '编辑事件类别'"
      :description="formMode === 'create' ? '类别编码用于系统识别，创建后不建议变更。' : '修改会立即影响后续事件上报时的可选类别。'"
      :value="formData"
      :fields="formFields"
      :rules="formRules"
      :submitting="submitting"
      :error="dialogError"
      width="640px"
      :confirm-text="formMode === 'create' ? '创建类别' : '保存类别'"
      @submit="saveCategory"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import {
  createEventCategory,
  listSystemEventCategories,
  updateEventCategory
} from '../../api/system'
import { errorMessage } from '../../utils/data'

function asItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

export default {
  name: 'EventCategoryListView',
  components: { ResourceListView, FormDialog },
  data() {
    return {
      formVisible: false,
      formMode: 'create',
      activeCategory: null,
      formData: {},
      submitting: false,
      dialogError: '',
      statuses: [
        { label: '启用', value: 'ENABLED' },
        { label: '停用', value: 'DISABLED' }
      ],
      columns: [
        { prop: 'code', label: '类别编码', minWidth: 160 },
        { prop: 'name', label: '类别名称', minWidth: 180 },
        { prop: 'description', label: '说明', minWidth: 220 },
        { prop: 'sortNo', label: '排序', width: 90 },
        { prop: 'status', label: '状态', width: 100, labels: { ENABLED: '启用', DISABLED: '停用' } }
      ]
    }
  },
  computed: {
    formFields() {
      const fields = [
        {
          prop: 'code',
          label: '类别编码',
          required: true,
          maxlength: 50,
          disabled: this.formMode === 'edit',
          span: 12,
          help: this.formMode === 'edit' ? '类别编码创建后不可修改。' : '仅支持大写英文字母、数字和下划线，例如 PUBLIC_FACILITY。'
        },
        { prop: 'name', label: '类别名称', required: true, maxlength: 100, span: 12 },
        { prop: 'sortNo', label: '排序号', required: true, span: 12 },
        { prop: 'description', label: '类别说明', type: 'textarea', rows: 3, maxlength: 255 }
      ]
      if (this.formMode === 'edit') {
        fields.splice(3, 0, { prop: 'status', label: '状态', type: 'select', required: true, options: this.statuses, span: 12 })
      }
      return fields
    },
    formRules() {
      return {
        code: [{ pattern: /^[A-Z][A-Z0-9_]{0,49}$/, message: '类别编码须以大写英文字母开头，仅可包含大写字母、数字和下划线', trigger: 'blur' }],
        name: [{ required: true, max: 100, message: '类别名称不能为空且不超过 100 个字符', trigger: 'blur' }],
        description: [{ max: 255, message: '类别说明不能超过 255 个字符', trigger: 'blur' }]
      }
    }
  },
  methods: {
    async fetchCategories(params) {
      const keyword = String(params.keyword || '').trim().toLowerCase()
      const categories = asItems(await listSystemEventCategories())
        .filter(item => !params.status || item.status === params.status)
        .filter(item => !keyword || [item.code, item.name, item.description]
          .some(value => String(value || '').toLowerCase().includes(keyword)))
        .sort((left, right) => Number(left.sortNo || 0) - Number(right.sortNo || 0))
      const start = (params.page - 1) * params.size
      return {
        items: categories.slice(start, start + params.size),
        total: categories.length,
        page: params.page,
        size: params.size
      }
    },
    openCreate() {
      this.formMode = 'create'
      this.activeCategory = null
      this.formData = { code: '', name: '', sortNo: 0, status: 'ENABLED', description: '' }
      this.dialogError = ''
      this.formVisible = true
    },
    openEdit(row) {
      this.formMode = 'edit'
      this.activeCategory = row
      this.formData = {
        id: String(row.id),
        code: row.code,
        name: row.name,
        sortNo: Number(row.sortNo || 0),
        status: row.status || 'ENABLED',
        description: row.description || '',
        version: row.version
      }
      this.dialogError = ''
      this.formVisible = true
    },
    async saveCategory(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      const payload = {
        code: String(form.code || '').trim(),
        name: String(form.name || '').trim(),
        description: form.description || null,
        sortNo: Number(form.sortNo)
      }
      try {
        if (this.formMode === 'create') await createEventCategory(payload)
        else await updateEventCategory(this.activeCategory.id, {
          name: payload.name,
          description: payload.description,
          sortNo: payload.sortNo,
          status: form.status,
          version: form.version
        })
        this.$message.success(this.formMode === 'create' ? '事件类别已创建' : '事件类别已保存')
        this.formVisible = false
        await this.$refs.resource.reload()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>
