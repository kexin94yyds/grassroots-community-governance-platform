<template>
  <section>
    <ResourceListView
      ref="resource"
      title="角色管理"
      description="维护四个核心角色的说明、状态与权限边界；角色编码和数据范围语义保持固定。"
      :fetcher="fetchRoles"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="角色编码或名称"
      :action-column-width="110"
    >
      <template #rowActions="{ row }">
        <el-button type="text" @click="openEdit(row)">配置权限</el-button>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      title="配置核心角色"
      description="权限调整会使该角色的旧登录会话失效；操作权限会自动补上所属菜单。"
      :value="formData"
      :fields="roleFields"
      :submitting="submitting"
      :error="dialogError"
      width="760px"
      confirm-text="保存角色配置"
      @submit="saveRole"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import { listMenus, listRoles, updateRole } from '../../api/system'
import { errorMessage } from '../../utils/data'

const GRID_WORKER_PERMISSIONS = new Set([
  'dashboard:read', 'grid:read', 'resident:read', 'event:read', 'event:report',
  'task:read', 'task:accept', 'task:handle', 'file:read', 'file:upload',
  'file:delete', 'workbench:grid:read', 'patrol:read', 'announcement:read',
  'service:application:read'
])

export default {
  name: 'RoleListView',
  components: { ResourceListView, FormDialog },
  data() {
    return {
      menus: [],
      activeRole: null,
      formVisible: false,
      formData: {},
      submitting: false,
      dialogError: '',
      statuses: [
        { label: '启用', value: 'ENABLED' },
        { label: '停用', value: 'DISABLED' }
      ],
      columns: [
        { prop: 'code', label: '角色编码', minWidth: 180 },
        { prop: 'name', label: '角色名称', minWidth: 150 },
        { prop: 'description', label: '职责说明', minWidth: 260 },
        { prop: 'permissionSummary', label: '权限配置', minWidth: 160 },
        { prop: 'status', label: '状态', width: 110, labels: { ENABLED: '启用', DISABLED: '停用' } }
      ]
    }
  },
  computed: {
    compatibleMenus() {
      if (!this.activeRole) return []
      return this.menus.filter(menu => this.isCompatible(this.activeRole.code, menu.permissionCode))
    },
    roleFields() {
      return [
        { prop: 'code', label: '角色编码', disabled: true, span: 12 },
        { prop: 'name', label: '角色名称', required: true, maxlength: 80, span: 12 },
        { prop: 'description', label: '职责说明', type: 'textarea', maxlength: 255, rows: 3 },
        {
          prop: 'status',
          label: '角色状态',
          type: 'select',
          required: true,
          disabled: this.activeRole && this.activeRole.code === 'SYSTEM_ADMIN',
          options: this.statuses,
          help: '角色仍分配给启用账号时不能停用。'
        },
        {
          prop: 'menuIds',
          label: '菜单与操作权限',
          type: 'select',
          multiple: true,
          required: true,
          options: this.compatibleMenus.map(menu => ({
            value: String(menu.id),
            label: `${menu.type === 'ACTION' ? '操作' : '菜单'} · ${menu.name} · ${menu.permissionCode || '无权限码'}`
          })),
          help: '核心角色只能在既定职责范围内调整，不能新增未知权限码。'
        }
      ]
    }
  },
  methods: {
    async ensureMenus() {
      if (!this.menus.length) this.menus = await listMenus()
    },
    async fetchRoles(params) {
      await this.ensureMenus()
      const roles = await listRoles()
      const keyword = String(params.keyword || '').toLowerCase()
      const filtered = roles
        .filter(role => !params.status || role.status === params.status)
        .filter(role => !keyword || [role.code, role.name, role.description]
          .some(value => String(value || '').toLowerCase().includes(keyword)))
        .map(role => ({
          ...role,
          id: role.code,
          permissionSummary: `${(role.menuIds || []).length} 项`
        }))
      const start = (params.page - 1) * params.size
      return { items: filtered.slice(start, start + params.size), total: filtered.length, page: params.page, size: params.size }
    },
    isCompatible(roleCode, permission) {
      if (!permission) return true
      if (roleCode === 'SYSTEM_ADMIN') {
        return !['task:accept', 'task:handle', 'resident:portal'].includes(permission)
      }
      if (roleCode === 'COMMUNITY_STAFF') {
        return !permission.startsWith('system:') && ![
          'task:accept',
          'task:handle',
          'resident:portal',
          'service:catalog:manage',
          'system:audit:read',
          'system:health:read',
          'announcement:global:write'
        ].includes(permission)
      }
      if (roleCode === 'GRID_WORKER') return GRID_WORKER_PERMISSIONS.has(permission)
      return roleCode === 'RESIDENT' && [
        'resident:portal',
        'workbench:resident:read',
        'announcement:read',
        'service:catalog:read',
        'service:application:read',
        'service:application:apply',
        'service:application:cancel',
        'service:application:rate'
      ].includes(permission)
    },
    async openEdit(row) {
      await this.ensureMenus()
      this.activeRole = row
      this.formData = {
        code: row.code,
        name: row.name,
        description: row.description || '',
        status: row.status,
        menuIds: (row.menuIds || []).map(String),
        version: row.version
      }
      this.dialogError = ''
      this.formVisible = true
    },
    async saveRole(form) {
      if (this.submitting || !this.activeRole) return
      this.submitting = true
      this.dialogError = ''
      try {
        const selected = new Set((form.menuIds || []).map(String))
        this.menus.forEach(menu => {
          if (selected.has(String(menu.id)) && menu.parentId) selected.add(String(menu.parentId))
        })
        await updateRole(this.activeRole.code, {
          name: form.name,
          description: form.description || null,
          status: form.status,
          menuIds: [...selected],
          version: form.version
        })
        this.$message.success('角色配置已保存；受影响账号需要重新登录')
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
