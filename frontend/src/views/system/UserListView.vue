<template>
  <section>
    <ResourceListView
      ref="resource"
      title="用户权限"
      description="维护后台账号及角色关系。实际授权结果始终以后端校验为准。"
      :fetcher="listUsers"
      :columns="columns"
      search-placeholder="姓名或用户名"
      manage-permission="system:user:manage"
      action-label="新增用户"
      :action-column-width="260"
      :view-options="viewOptions"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="账号运行概览"
          description="识别账号可用性、近期活跃度与角色配置结构。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          :groups="insightGroups"
          @retry="loadInsight"
        />
      </template>
      <template #rowActions="{ row }">
        <el-button v-if="row.approvalStatus === 'PENDING'" type="text" @click="openReview(row)">审核注册</el-button>
        <template v-else>
          <el-button type="text" @click="openEdit(row)">编辑</el-button>
          <el-button type="text" @click="openPasswordReset(row)">重置密码</el-button>
          <el-button v-if="row.accountType !== 'RESIDENT'" type="text" @click="openRoles(row)">分配角色</el-button>
          <el-button
            type="text"
            :class="{ 'danger-text': row.status === 'ENABLED' }"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
          </el-button>
        </template>
      </template>
      <template #alternate="{ view, items }">
        <RecordCardGrid
          v-if="view === 'card'"
          :items="items"
          title-prop="realName"
          eyebrow-prop="username"
          status-prop="status"
          :status-labels="userStatusLabels"
        >
          <template #default="{ item }">
            <dl class="record-meta">
              <div><dt>角色</dt><dd>{{ roleText(item.roles) }}</dd></div>
              <div><dt>联系电话</dt><dd>{{ display(item.phone) }}</dd></div>
              <div><dt>最近登录</dt><dd>{{ formatDate(item.lastLoginAt) }}</dd></div>
            </dl>
          </template>
          <template #actions="{ item }">
            <el-button v-if="item.approvalStatus === 'PENDING'" type="text" @click="openReview(item)">审核注册</el-button>
            <template v-else>
              <el-button type="text" @click="openEdit(item)">编辑</el-button>
              <el-button type="text" @click="openPasswordReset(item)">重置密码</el-button>
              <el-button v-if="item.accountType !== 'RESIDENT'" type="text" @click="openRoles(item)">分配角色</el-button>
              <el-button
                type="text"
                :class="{ 'danger-text': item.status === 'ENABLED' }"
                @click="toggleStatus(item)"
              >
                {{ item.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
            </template>
          </template>
        </RecordCardGrid>
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      :title="formMode === 'create' ? '新增用户' : '编辑用户'"
      :description="formMode === 'create' ? '创建登录账号并赋予初始角色。' : '用户名不可修改，敏感权限仍由后端校验。'"
      :value="formData"
      :fields="userFields"
      :rules="userRules"
      :submitting="submitting"
      :error="dialogError"
      @submit="saveUser"
    />

    <FormDialog
      :visible.sync="rolesVisible"
      title="分配角色"
      description="角色变更将在后端重新计算权限，前端隐藏菜单不代表鉴权成功。"
      :value="rolesForm"
      :fields="roleFields"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="保存角色"
      @submit="saveRoles"
    />

    <FormDialog
      :visible.sync="passwordResetVisible"
      title="重置用户密码"
      description="请输入一次性临时密码；用户登录后必须立即修改，系统不会回显该密码。"
      :value="passwordResetForm"
      :fields="passwordResetFields"
      :rules="passwordResetRules"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="确认重置"
      @submit="savePasswordReset"
    />

    <el-dialog
      :visible.sync="reviewVisible"
      title="审核注册申请"
      width="620px"
      custom-class="registration-review-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="resetReview"
    >
      <div v-if="reviewUser" class="registration-review-summary">
        <span>{{ reviewUser.accountType === 'RESIDENT' ? '居民注册' : '工作人员申请' }}</span>
        <strong>{{ reviewUser.realName }}</strong>
        <small>{{ reviewUser.username }} · {{ reviewUser.phone || '未留联系电话' }}</small>
        <p v-if="reviewUser.accountType === 'RESIDENT'">
          待绑定居民档案：{{ reviewUser.requestedResidentName || '未匹配' }}
        </p>
        <p v-else-if="reviewUser.registrationNote">申请说明：{{ reviewUser.registrationNote }}</p>
      </div>
      <el-alert
        class="review-alert"
        :title="reviewUser && reviewUser.accountType === 'RESIDENT'
          ? '批准后将自动绑定匹配的居民档案，并且只授予居民服务台权限。'
          : '批准工作人员申请时必须分配内部角色；责任网格仍在网格管理中单独分配。'"
        type="info"
        show-icon
        :closable="false"
      />
      <el-alert v-if="dialogError" class="review-alert" :title="dialogError" type="error" show-icon :closable="false" />
      <el-form label-position="top" @submit.native.prevent="submitReview">
        <el-form-item label="审核结论">
          <el-radio-group v-model="reviewForm.decision" class="registration-review-decision">
            <el-radio-button label="APPROVE">批准</el-radio-button>
            <el-radio-button label="REJECT">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="reviewForm.decision === 'APPROVE' && reviewUser && reviewUser.accountType === 'STAFF'"
          label="分配角色"
          required
        >
          <el-select v-model="reviewForm.roleCodes" multiple filterable placeholder="至少选择一个内部角色">
            <el-option v-for="role in staffRoleOptions" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="reviewForm.decision === 'REJECT'" label="驳回原因" required>
          <el-input v-model.trim="reviewForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="说明需要补充或更正的内容" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button class="registration-review-cancel" :disabled="submitting" @click="reviewVisible = false">取消</el-button>
        <el-button class="registration-review-submit" type="primary" :loading="submitting" @click="submitReview">
          {{ reviewForm.decision === 'APPROVE' ? '批准并启用' : '确认驳回' }}
        </el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import RecordCardGrid from '../../components/RecordCardGrid.vue'
import { getUserInsight } from '../../api/insights'
import {
  assignRoles,
  createUser,
  getUser,
  listRoles,
  listUsers,
  updateUser,
  updateUserStatus,
  reviewRegistration,
  resetUserPassword
} from '../../api/system'
import { errorMessage, formatDateTime } from '../../utils/data'

const DEFAULT_ROLES = [
  { value: 'SYSTEM_ADMIN', label: '系统管理员' },
  { value: 'COMMUNITY_STAFF', label: '社区工作人员' },
  { value: 'GRID_WORKER', label: '网格员' },
  { value: 'RESIDENT', label: '居民用户' }
]

export default {
  name: 'UserListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid },
  data() {
    return {
      listUsers,
      insight: {},
      insightLoading: false,
      insightError: '',
      formVisible: false,
      rolesVisible: false,
      passwordResetVisible: false,
      reviewVisible: false,
      reviewUser: null,
      reviewForm: { decision: 'APPROVE', roleCodes: [], reason: '', version: 0 },
      formMode: 'create',
      formData: {},
      rolesForm: {},
      passwordResetForm: {},
      activeUserId: '',
      submitting: false,
      dialogError: '',
      roleOptions: DEFAULT_ROLES,
      viewOptions: [
        { value: 'list', label: '列表', icon: 'el-icon-tickets' },
        { value: 'card', label: '角色卡片', icon: 'el-icon-postcard' }
      ],
      userStatusLabels: { ENABLED: '启用', DISABLED: '停用', LOCKED: '锁定' },
      columns: [
        { prop: 'username', label: '用户名', minWidth: 140 },
        { prop: 'realName', label: '姓名', minWidth: 140 },
        { prop: 'accountType', label: '账号类型', width: 120, labels: { STAFF: '工作人员', RESIDENT: '居民' } },
        { prop: 'approvalStatus', label: '审核状态', width: 120, labels: { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' } },
        { prop: 'requestedResidentName', label: '匹配居民', minWidth: 130 },
        { prop: 'phone', label: '联系电话', minWidth: 140 },
        { prop: 'roles', label: '角色', minWidth: 180 },
        { prop: 'status', label: '账号状态', width: 120, labels: { ENABLED: '启用', DISABLED: '停用', LOCKED: '锁定' } },
        { prop: 'lastLoginAt', label: '最近登录', minWidth: 180, date: true }
      ]
    }
  },
  computed: {
    insightMetrics() {
      return [
        { key: 'total', label: '账号总数', value: this.insight.total, note: '权限范围内' },
        { key: 'enabled', label: '正常启用', value: this.insight.enabled, note: '可正常登录', tone: 'positive' },
        { key: 'active', label: '近 30 日登录', value: this.insight.loggedInLast30Days, note: '近期活跃账号' },
        {
          key: 'risk',
          label: '停用或锁定',
          value: Number(this.insight.disabled || 0) + Number(this.insight.locked || 0),
          note: '需留意账号',
          tone: 'warning'
        }
      ]
    },
    insightGroups() {
      return [{
        key: 'roles',
        title: '角色配置',
        items: (this.insight.roles || []).map(item => ({
          ...item,
          label: this.roleLabel(item.key)
        }))
      }]
    },
    userFields() {
      const fields = [
        { prop: 'username', label: '用户名', required: true, maxlength: 64, span: 12, disabled: this.formMode === 'edit' },
        { prop: 'realName', label: '姓名', required: true, maxlength: 80, span: 12 },
        { prop: 'phone', label: '联系电话', maxlength: 30, span: 12 }
      ]
      if (this.formMode === 'create') {
        fields.push(
          { prop: 'password', label: '初始密码', type: 'password', required: true, maxlength: 128, span: 12, autocomplete: 'new-password' },
          { prop: 'roleCodes', label: '初始角色', type: 'select', multiple: true, required: true, options: this.staffRoleOptions }
        )
      }
      return fields
    },
    userRules() {
      return {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          { pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '用户名需为 3-64 位字母、数字或 _ . -', trigger: 'blur' }
        ],
        password: [
          { required: this.formMode === 'create', message: '请输入初始密码', trigger: 'blur' },
          { min: 8, message: '密码至少 8 位', trigger: 'blur' }
        ]
      }
    },
    roleFields() {
      return [{
        prop: 'roleCodes',
        label: '角色',
        type: 'select',
        multiple: true,
        required: true,
        options: this.staffRoleOptions
      }]
    },
    passwordResetFields() {
      return [
        { prop: 'temporaryPassword', label: '一次性临时密码', type: 'password', required: true, maxlength: 128, autocomplete: 'new-password' },
        { prop: 'confirmPassword', label: '确认临时密码', type: 'password', required: true, maxlength: 128, autocomplete: 'new-password' }
      ]
    },
    passwordResetRules() {
      return {
        temporaryPassword: [
          { required: true, message: '请输入一次性临时密码', trigger: 'blur' },
          { min: 8, max: 128, message: '密码长度需为 8—128 位', trigger: 'blur' }
        ],
        confirmPassword: [{ required: true, message: '请再次输入临时密码', trigger: 'blur' }]
      }
    },
    staffRoleOptions() {
      return this.roleOptions.filter(role => role.value !== 'RESIDENT')
    }
  },
  created() {
    this.loadRoleOptions()
    this.loadInsight()
  },
  methods: {
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        this.insight = await getUserInsight()
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    refresh() {
      return Promise.all([this.$refs.resource.reload(), this.loadInsight()])
    },
    roleLabel(code) {
      const match = this.roleOptions.find(item => item.value === code) ||
        DEFAULT_ROLES.find(item => item.value === code)
      return match ? match.label : code
    },
    roleText(roles) {
      const items = Array.isArray(roles)
        ? roles
        : String(roles || '').split(',').map(item => item.trim()).filter(Boolean)
      return items.length ? items.map(this.roleLabel).join('、') : '未分配'
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    formatDate(value) {
      return value ? formatDateTime(value) : '从未登录'
    },
    async loadRoleOptions() {
      try {
        const result = await listRoles()
        const roles = Array.isArray(result) ? result : (result && result.items) || []
        if (roles.length) {
          this.roleOptions = roles
            .filter(role => !role.status || role.status === 'ENABLED')
            .map(role => ({ value: role.code || role.roleCode, label: role.name || role.roleName || role.code }))
        }
      } catch (error) {
        // 预置角色保证表单仍可使用，最终权限由服务端校验。
      }
    },
    openCreate() {
      this.formMode = 'create'
      this.formData = { username: '', realName: '', phone: '', password: '', roleCodes: [] }
      this.dialogError = ''
      this.formVisible = true
    },
    async openEdit(row) {
      this.formMode = 'edit'
      this.dialogError = ''
      try {
        const detail = await getUser(row.id)
        this.formData = { ...detail, id: String(detail.id), version: detail.version }
        this.formVisible = true
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    openRoles(row) {
      this.activeUserId = String(row.id)
      this.rolesForm = { roleCodes: Array.isArray(row.roles) ? row.roles.slice() : [], version: row.version }
      this.dialogError = ''
      this.rolesVisible = true
    },
    openPasswordReset(row) {
      this.activeUserId = String(row.id)
      this.passwordResetForm = { temporaryPassword: '', confirmPassword: '', version: row.version }
      this.dialogError = ''
      this.passwordResetVisible = true
    },
    async openReview(row) {
      this.dialogError = ''
      try {
        this.reviewUser = await getUser(row.id)
        this.reviewForm = { decision: 'APPROVE', roleCodes: [], reason: '', version: this.reviewUser.version }
        this.reviewVisible = true
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    resetReview() {
      if (this.submitting) return
      this.reviewUser = null
      this.reviewForm = { decision: 'APPROVE', roleCodes: [], reason: '', version: 0 }
      this.dialogError = ''
    },
    async submitReview() {
      if (this.submitting || !this.reviewUser) return
      if (this.reviewForm.decision === 'APPROVE' && this.reviewUser.accountType === 'STAFF' && !this.reviewForm.roleCodes.length) {
        this.dialogError = '批准工作人员申请时至少分配一个内部角色'
        return
      }
      if (this.reviewForm.decision === 'REJECT' && !this.reviewForm.reason) {
        this.dialogError = '驳回注册申请必须填写原因'
        return
      }
      this.submitting = true
      this.dialogError = ''
      try {
        await reviewRegistration(this.reviewUser.id, {
          decision: this.reviewForm.decision,
          roleCodes: this.reviewForm.decision === 'APPROVE' ? this.reviewForm.roleCodes : [],
          reason: this.reviewForm.decision === 'REJECT' ? this.reviewForm.reason : null,
          version: this.reviewForm.version
        })
        this.$message.success(this.reviewForm.decision === 'APPROVE' ? '注册申请已批准，账号可以登录' : '注册申请已驳回')
        this.reviewVisible = false
        await this.refresh()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async saveUser(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.formMode === 'create') {
          await createUser({
            username: form.username,
            password: form.password,
            realName: form.realName,
            phone: form.phone || null,
            roleCodes: form.roleCodes
          })
        } else {
          await updateUser(form.id, {
            realName: form.realName,
            phone: form.phone || null,
            version: form.version
          })
        }
        this.$message.success(this.formMode === 'create' ? '用户创建成功' : '用户资料已更新')
        this.formVisible = false
        await this.refresh()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async saveRoles(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        await assignRoles(this.activeUserId, form.roleCodes, form.version)
        this.$message.success('角色分配已保存')
        this.rolesVisible = false
        await this.refresh()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async savePasswordReset(form) {
      if (this.submitting) return
      if (form.temporaryPassword !== form.confirmPassword) {
        this.dialogError = '两次输入的临时密码不一致'
        return
      }
      this.submitting = true
      this.dialogError = ''
      try {
        await resetUserPassword(this.activeUserId, form.temporaryPassword, form.version)
        this.$message.success('密码已重置；请通过安全渠道交付临时密码')
        this.passwordResetVisible = false
        await this.refresh()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async toggleStatus(row) {
      const enabling = row.status !== 'ENABLED'
      try {
        await this.$confirm(
          enabling ? `确认启用用户“${row.realName || row.username}”？` : `停用后该用户将无法登录，确认继续？`,
          enabling ? '启用用户' : '停用用户',
          { type: enabling ? 'info' : 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
        )
        await updateUserStatus(row.id, enabling, row.version)
        this.$message.success(enabling ? '用户已启用' : '用户已停用')
        await this.refresh()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    }
  }
}
</script>

<style scoped>
.registration-review-summary { display: grid; gap: 5px; padding: 18px 20px; background: var(--paper); border: 1px solid var(--line); border-left: 4px solid var(--accent); border-radius: var(--radius-control); }
.registration-review-summary > span { color: var(--accent); font-family: var(--font-utility); font-size: 12px; }
.registration-review-summary strong { font-family: var(--font-display); font-size: 22px; }
.registration-review-summary small, .registration-review-summary p { color: var(--muted); }
.registration-review-summary p { margin: 7px 0 0; }
.review-alert { margin: 18px 0; }
.el-dialog .el-select { width: 100%; }
</style>
