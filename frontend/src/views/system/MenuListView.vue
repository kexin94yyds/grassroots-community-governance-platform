<template>
  <section>
    <ResourceListView
      ref="resource"
      title="菜单权限"
      description="维护既有菜单和操作项的展示信息与启停状态；编码、路由和权限码不可修改。"
      :fetcher="fetchMenus"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="菜单编码、名称或权限码"
      :action-column-width="90"
    >
      <template #rowActions="{ row }">
        <el-button type="text" @click="openEdit(row)">编辑</el-button>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      title="编辑菜单或操作项"
      description="本批次采用固定目录模型，只允许调整名称、图标、排序和状态。"
      :value="formData"
      :fields="menuFields"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="保存菜单配置"
      @submit="saveMenu"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import { listMenus, updateMenu } from '../../api/system'
import { errorMessage } from '../../utils/data'

const PROTECTED_CODES = new Set([
  'SYSTEM_USER',
  'SYSTEM_ROLE',
  'SYSTEM_MENU',
  'EVENT_CATEGORY',
  'RESIDENT_PORTAL'
])

export default {
  name: 'MenuListView',
  components: { ResourceListView, FormDialog },
  data() {
    return {
      activeMenu: null,
      formVisible: false,
      formData: {},
      submitting: false,
      dialogError: '',
      statuses: [
        { label: '启用', value: 'ENABLED' },
        { label: '停用', value: 'DISABLED' }
      ],
      columns: [
        { prop: 'code', label: '菜单编码', minWidth: 180 },
        { prop: 'name', label: '名称', minWidth: 160 },
        { prop: 'type', label: '类型', width: 100, labels: { MENU: '菜单', ACTION: '操作' } },
        { prop: 'permissionCode', label: '权限码', minWidth: 190 },
        { prop: 'routePath', label: '路由', minWidth: 170 },
        { prop: 'sortNo', label: '排序', width: 90 },
        { prop: 'status', label: '状态', width: 110, labels: { ENABLED: '启用', DISABLED: '停用' } }
      ]
    }
  },
  computed: {
    menuFields() {
      return [
        { prop: 'code', label: '菜单编码', disabled: true, span: 12 },
        { prop: 'permissionCode', label: '权限码', disabled: true, span: 12 },
        { prop: 'name', label: '显示名称', required: true, maxlength: 120, span: 12 },
        { prop: 'icon', label: '图标类名', maxlength: 80, span: 12 },
        { prop: 'sortNo', label: '排序号', required: true, span: 12 },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          required: true,
          disabled: this.activeMenu && PROTECTED_CODES.has(this.activeMenu.code),
          options: this.statuses,
          help: '停用父菜单前必须先停用其操作项；核心系统入口、事件类别和居民服务台不能停用。'
        }
      ]
    }
  },
  methods: {
    async fetchMenus(params) {
      const menus = await listMenus()
      const keyword = String(params.keyword || '').toLowerCase()
      const filtered = menus
        .filter(menu => !params.status || menu.status === params.status)
        .filter(menu => !keyword || [menu.code, menu.name, menu.permissionCode, menu.routePath]
          .some(value => String(value || '').toLowerCase().includes(keyword)))
      const start = (params.page - 1) * params.size
      return { items: filtered.slice(start, start + params.size), total: filtered.length, page: params.page, size: params.size }
    },
    openEdit(row) {
      this.activeMenu = row
      this.formData = {
        id: String(row.id),
        code: row.code,
        permissionCode: row.permissionCode || '',
        name: row.name,
        icon: row.icon || '',
        sortNo: row.sortNo,
        status: row.status,
        version: row.version
      }
      this.dialogError = ''
      this.formVisible = true
    },
    async saveMenu(form) {
      if (this.submitting || !this.activeMenu) return
      this.submitting = true
      this.dialogError = ''
      try {
        await updateMenu(this.activeMenu.id, {
          name: form.name,
          icon: form.icon || null,
          sortNo: Number(form.sortNo),
          status: form.status,
          version: form.version
        })
        // The sidebar consumes this shared navigation store, so the current
        // administrator sees the saved display settings without reloading.
        await this.$store.dispatch('navigation/refresh').catch(() => null)
        this.$message.success('菜单配置已保存')
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
