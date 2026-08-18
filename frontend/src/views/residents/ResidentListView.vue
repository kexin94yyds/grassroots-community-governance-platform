<template>
  <section>
    <el-tabs v-model="activeTab" class="module-tabs">
      <el-tab-pane label="居民档案" name="residents">
        <ResourceListView
          ref="residentResource"
          title="居民档案"
          description="按网格管理居民资料；身份证和电话仅在授权场景显示，列表默认脱敏。"
          :fetcher="listResidents"
          :columns="residentColumns"
          :status-options="residentStatuses"
          search-placeholder="姓名、居民编号或地址"
          manage-permission="resident:write"
          action-label="新增居民"
          :action-column-width="310"
          :view-options="viewOptions"
          @create="openResidentCreate"
        >
          <template #actions>
            <el-button
              v-if="canReadSensitive"
              icon="el-icon-search"
              @click="openSensitiveSearch"
            >
              敏感精确检索
            </el-button>
            <el-button
              v-if="canReadSensitiveAudit"
              icon="el-icon-document"
              @click="openSensitiveAudit"
            >
              敏感访问审计
            </el-button>
            <el-button
              v-if="canWrite"
              type="primary"
              icon="el-icon-plus"
              @click="openResidentCreate"
            >
              新增居民
            </el-button>
          </template>
          <template #insight>
            <InsightOverview
              title="人口与家庭概览"
              description="在不暴露敏感字段的前提下，掌握居民状态、家庭户和重点服务对象规模。"
              :loading="insightLoading"
              :error="insightError"
              :metrics="insightMetrics"
              :groups="insightGroups"
              @retry="loadInsight"
            />
          </template>
          <template #rowActions="{ row }">
            <el-button v-if="canReadSensitive" type="text" @click="openSensitiveView(row)">查看敏感字段</el-button>
            <el-button v-if="canWrite" type="text" @click="openResidentEdit(row)">编辑</el-button>
            <el-dropdown v-if="canWrite" trigger="click" @command="handleResidentCommand($event, row)">
              <el-button type="text">状态操作<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="ACTIVE">恢复在册</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 'MOVED'" command="MOVED">标记迁出</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 'DECEASED'" command="DECEASED">标记死亡</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 'ARCHIVED'" command="ARCHIVED">归档</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
          <template #alternate="{ view, items }">
            <RecordCardGrid
              v-if="view === 'card'"
              :items="items"
              title-prop="realName"
              eyebrow-prop="residentNo"
              status-prop="status"
              :status-labels="residentStatusLabels"
            >
              <template #default="{ item }">
                <dl class="record-meta">
                  <div><dt>所属网格</dt><dd>{{ display(item.gridName) }}</dd></div>
                  <div><dt>所属家庭户</dt><dd>{{ display(item.householdNo) }}</dd></div>
                  <div><dt>联系电话</dt><dd>{{ display(item.phoneMasked) }}</dd></div>
                  <div><dt>重点标签</dt><dd>{{ tagText(item.specialGroupTags) }}</dd></div>
                </dl>
              </template>
              <template #actions="{ item }">
                <el-button v-if="canReadSensitive" type="text" @click="openSensitiveView(item)">查看敏感字段</el-button>
                <el-button v-if="canWrite" type="text" @click="openResidentEdit(item)">编辑</el-button>
                <el-dropdown v-if="canWrite" trigger="click" @command="handleResidentCommand($event, item)">
                  <el-button type="text">状态操作<i class="el-icon-arrow-down el-icon--right" /></el-button>
                  <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item v-if="item.status !== 'ACTIVE'" command="ACTIVE">恢复在册</el-dropdown-item>
                    <el-dropdown-item v-if="item.status !== 'MOVED'" command="MOVED">标记迁出</el-dropdown-item>
                    <el-dropdown-item v-if="item.status !== 'DECEASED'" command="DECEASED">标记死亡</el-dropdown-item>
                    <el-dropdown-item v-if="item.status !== 'ARCHIVED'" command="ARCHIVED">归档</el-dropdown-item>
                  </el-dropdown-menu>
                </el-dropdown>
              </template>
            </RecordCardGrid>
          </template>
        </ResourceListView>
      </el-tab-pane>

      <el-tab-pane label="家庭户" name="households">
        <ResourceListView
          ref="householdResource"
          title="家庭户"
          description="维护家庭户的责任网格、门牌信息和当前状态。"
          :fetcher="listHouseholds"
          :columns="householdColumns"
          :status-options="householdStatuses"
          search-placeholder="家庭户编号或地址"
          manage-permission="resident:write"
          action-label="新增家庭户"
          :action-column-width="210"
          :view-options="viewOptions"
          @create="openHouseholdCreate"
        >
          <template #insight>
            <InsightOverview
              title="人口与家庭概览"
              description="在不暴露敏感字段的前提下，掌握居民状态、家庭户和重点服务对象规模。"
              :loading="insightLoading"
              :error="insightError"
              :metrics="insightMetrics"
              :groups="insightGroups"
              @retry="loadInsight"
            />
          </template>
          <template #rowActions="{ row }">
            <el-button v-if="canWrite" type="text" @click="openHouseholdEdit(row)">编辑</el-button>
            <el-dropdown v-if="canWrite" trigger="click" @command="handleHouseholdCommand($event, row)">
              <el-button type="text">状态操作<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="ACTIVE">恢复有效</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 'MOVED'" command="MOVED">标记迁出</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 'ARCHIVED'" command="ARCHIVED">归档</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
          <template #alternate="{ view, items }">
            <RecordCardGrid
              v-if="view === 'card'"
              :items="items"
              title-prop="address"
              eyebrow-prop="householdNo"
              status-prop="status"
              :status-labels="householdStatusLabels"
            >
              <template #default="{ item }">
                <dl class="record-meta">
                  <div><dt>所属网格</dt><dd>{{ display(item.gridName) }}</dd></div>
                  <div><dt>楼栋 / 单元</dt><dd>{{ buildingText(item) }}</dd></div>
                  <div><dt>门牌</dt><dd>{{ display(item.roomNo) }}</dd></div>
                </dl>
              </template>
              <template #actions="{ item }">
                <el-button v-if="canWrite" type="text" @click="openHouseholdEdit(item)">编辑</el-button>
                <el-dropdown v-if="canWrite" trigger="click" @command="handleHouseholdCommand($event, item)">
                  <el-button type="text">状态操作<i class="el-icon-arrow-down el-icon--right" /></el-button>
                  <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item v-if="item.status !== 'ACTIVE'" command="ACTIVE">恢复有效</el-dropdown-item>
                    <el-dropdown-item v-if="item.status !== 'MOVED'" command="MOVED">标记迁出</el-dropdown-item>
                    <el-dropdown-item v-if="item.status !== 'ARCHIVED'" command="ARCHIVED">归档</el-dropdown-item>
                  </el-dropdown-menu>
                </el-dropdown>
              </template>
            </RecordCardGrid>
          </template>
        </ResourceListView>
      </el-tab-pane>
    </el-tabs>

    <FormDialog
      :visible.sync="formVisible"
      :title="dialogTitle"
      :description="dialogDescription"
      :value="formData"
      :fields="dialogFields"
      :rules="dialogRules"
      :submitting="submitting"
      :error="dialogError"
      width="760px"
      @submit="saveForm"
    />

    <FormDialog
      :visible.sync="sensitiveSearchVisible"
      title="敏感字段精确检索"
      description="身份证号或手机号仅用于服务端精确匹配，不会出现在地址栏；检索结果继续保持脱敏。"
      :value="sensitiveSearchForm"
      :fields="sensitiveSearchFields"
      :rules="sensitiveSearchRules"
      :submitting="sensitiveSearchLoading"
      :error="sensitiveSearchError"
      confirm-text="精确检索"
      width="820px"
      @submit="searchSensitiveResidents"
      @close="resetSensitiveSearch"
    >
      <template #extra>
        <div v-if="sensitiveSearchCompleted" class="sensitive-results">
          <div class="sensitive-results__heading">
            <strong>检索结果</strong>
            <span>共 {{ sensitiveSearchPage.total }} 条，仅展示脱敏信息</span>
          </div>
          <el-table
            v-loading="sensitiveSearchLoading"
            :data="sensitiveSearchPage.items"
            size="small"
            empty-text="未找到匹配的居民档案"
          >
            <el-table-column prop="residentNo" label="居民编号" min-width="145" />
            <el-table-column prop="realName" label="姓名" min-width="100" />
            <el-table-column prop="gridName" label="所属网格" min-width="145" />
            <el-table-column prop="idCardMasked" label="身份证号" min-width="160" />
            <el-table-column prop="phoneMasked" label="手机号" min-width="130" />
            <el-table-column label="操作" width="110" align="right">
              <template slot-scope="{ row }">
                <el-button type="text" @click="openSensitiveView(row)">授权查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="sensitiveSearchPage.total > sensitiveSearchPage.size"
            class="sensitive-results__pagination"
            background
            layout="prev, pager, next"
            :current-page="sensitiveSearchPage.page"
            :page-size="sensitiveSearchPage.size"
            :total="sensitiveSearchPage.total"
            @current-change="changeSensitiveSearchPage"
          />
        </div>
      </template>
    </FormDialog>

    <el-dialog
      :visible.sync="sensitiveViewVisible"
      title="授权查看居民敏感字段"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!sensitiveViewLoading"
      :show-close="!sensitiveViewLoading"
      @closed="resetSensitiveView"
    >
      <template v-if="sensitiveViewTarget">
        <p class="dialog-description">
          查看对象：{{ sensitiveViewTarget.realName }}（{{ sensitiveViewTarget.residentNo }}）。本次操作将写入审计日志。
        </p>
        <el-alert
          v-if="sensitiveViewError"
          class="dialog-alert"
          :title="sensitiveViewError"
          type="error"
          show-icon
          :closable="false"
        />

        <el-form
          v-if="!sensitiveViewData"
          ref="sensitiveViewForm"
          :model="sensitiveViewForm"
          :rules="sensitiveViewRules"
          label-position="top"
          @submit.native.prevent="revealSensitiveData"
        >
          <el-form-item label="查看用途" prop="purpose">
            <el-input
              v-model.trim="sensitiveViewForm.purpose"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="请填写具体业务用途，例如：办理居民养老补贴身份核验"
            />
          </el-form-item>
        </el-form>

        <div v-else class="sensitive-reveal">
          <el-alert
            :title="`敏感信息将在 ${sensitiveViewRemaining} 秒后自动隐藏`"
            type="warning"
            show-icon
            :closable="false"
          />
          <dl>
            <div><dt>身份证号</dt><dd>{{ display(sensitiveViewData.idCard) }}</dd></div>
            <div><dt>手机号</dt><dd>{{ display(sensitiveViewData.phone) }}</dd></div>
          </dl>
        </div>
      </template>

      <div slot="footer" class="dialog-footer">
        <el-button :disabled="sensitiveViewLoading" @click="sensitiveViewVisible = false">
          {{ sensitiveViewData ? '立即隐藏' : '取消' }}
        </el-button>
        <el-button
          v-if="!sensitiveViewData"
          type="primary"
          :loading="sensitiveViewLoading"
          @click="revealSensitiveData"
        >
          确认并查看
        </el-button>
      </div>
    </el-dialog>

    <el-dialog
      :visible.sync="sensitiveAuditVisible"
      title="敏感信息访问审计"
      width="980px"
      append-to-body
      :close-on-click-modal="false"
    >
      <p class="dialog-description">记录敏感字段检索和查看操作。仅展示当前账号有权查看的责任范围日志。</p>
      <el-alert
        v-if="sensitiveAuditError"
        class="dialog-alert"
        :title="sensitiveAuditError"
        type="error"
        show-icon
        :closable="false"
      />
      <el-form class="query-bar sensitive-audit-query" :inline="true" @submit.native.prevent="searchSensitiveAudit">
        <el-form-item label="操作类型">
          <el-select v-model="sensitiveAuditQuery.action" clearable placeholder="全部操作">
            <el-option label="敏感检索" value="SEARCH" />
            <el-option label="敏感查看" value="VIEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段范围">
          <el-select v-model="sensitiveAuditQuery.fieldType" clearable placeholder="全部字段">
            <el-option label="身份证号" value="ID_CARD" />
            <el-option label="手机号" value="PHONE" />
            <el-option label="身份证号和手机号" value="BOTH" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model.trim="sensitiveAuditQuery.keyword"
            clearable
            placeholder="操作人员、居民编号或用途"
            @keyup.enter.native="searchSensitiveAudit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="resetSensitiveAudit">重置</el-button>
        </el-form-item>
      </el-form>
      <div v-loading="sensitiveAuditLoading" class="sensitive-audit-table">
        <el-table :data="sensitiveAuditPage.items" size="small" empty-text="暂无符合条件的敏感访问日志">
          <el-table-column prop="createdAt" label="访问时间" min-width="170">
            <template slot-scope="{ row }">{{ formatAuditDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template slot-scope="{ row }">{{ auditActionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column label="操作人员" min-width="130">
            <template slot-scope="{ row }">{{ auditOperator(row) }}</template>
          </el-table-column>
          <el-table-column label="居民" min-width="150">
            <template slot-scope="{ row }">{{ auditResident(row) }}</template>
          </el-table-column>
          <el-table-column label="字段范围" min-width="110">
            <template slot-scope="{ row }">{{ row.fieldType || '—' }}</template>
          </el-table-column>
          <el-table-column label="范围网格" min-width="140">
            <template slot-scope="{ row }">{{ row.scopeGridName || row.scopeGridCode || row.scopeGridId || '—' }}</template>
          </el-table-column>
          <el-table-column label="用途 / 结果" min-width="220">
            <template slot-scope="{ row }">
              {{ row.purpose || `检索结果 ${Number(row.resultCount || 0)} 条` }}
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!sensitiveAuditLoading && !sensitiveAuditPage.items.length" class="sensitive-audit-empty">
          可以调整操作类型或关键词后重新查询。
        </div>
      </div>
      <div class="sensitive-audit-footer">
        <span>共 {{ sensitiveAuditPage.total }} 条</span>
        <el-pagination
          v-if="sensitiveAuditPage.total > sensitiveAuditPage.size"
          background
          layout="prev, pager, next"
          :current-page="sensitiveAuditPage.page"
          :page-size="sensitiveAuditPage.size"
          :total="sensitiveAuditPage.total"
          @current-change="changeSensitiveAuditPage"
        />
      </div>
    </el-dialog>
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import RecordCardGrid from '../../components/RecordCardGrid.vue'
import { listGrids } from '../../api/grids'
import { getResidentInsight } from '../../api/insights'
import {
  createHousehold,
  createResident,
  getHousehold,
  getResident,
  listHouseholds,
  listResidents,
  listSensitiveAccessLogs,
  searchResidentsBySensitiveValue,
  updateHousehold,
  updateHouseholdStatus,
  updateResident,
  updateResidentStatus,
  viewResidentSensitiveData
} from '../../api/residents'
import { errorMessage, formatDateTime } from '../../utils/data'

const SPECIAL_TAG_OPTIONS = [
  { value: '独居老人', label: '独居老人' },
  { value: '低保对象', label: '低保对象' },
  { value: '残障人士', label: '残障人士' },
  { value: '重点帮扶', label: '重点帮扶' }
]

function pageItems(result) {
  return Array.isArray(result) ? result : (result && result.items) || []
}

function parseTags(value) {
  if (Array.isArray(value)) return value.slice()
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return String(value).split(',').map(item => item.trim()).filter(Boolean)
  }
}

export default {
  name: 'ResidentListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid },
  data() {
    const requestedTab = this.$route && this.$route.query.tab
    return {
      listResidents,
      listHouseholds,
      activeTab: ['residents', 'households'].includes(requestedTab) ? requestedTab : 'residents',
      insight: {},
      insightLoading: false,
      insightError: '',
      formVisible: false,
      formKind: 'resident',
      formMode: 'create',
      formData: {},
      submitting: false,
      dialogError: '',
      sensitiveSearchVisible: false,
      sensitiveSearchForm: { type: 'PHONE', value: '', gridId: '', status: '' },
      sensitiveSearchLoading: false,
      sensitiveSearchError: '',
      sensitiveSearchCompleted: false,
      sensitiveSearchPage: { items: [], total: 0, page: 1, size: 20 },
      sensitiveViewVisible: false,
      sensitiveViewTarget: null,
      sensitiveViewForm: { purpose: '' },
      sensitiveViewData: null,
      sensitiveViewLoading: false,
      sensitiveViewError: '',
      sensitiveViewRemaining: 0,
      sensitiveViewTimer: null,
      sensitiveAuditVisible: false,
      sensitiveAuditLoading: false,
      sensitiveAuditError: '',
      sensitiveAuditQuery: { action: '', fieldType: '', keyword: '' },
      sensitiveAuditPage: { items: [], total: 0, page: 1, size: 20 },
      gridOptions: [],
      householdOptions: [],
      viewOptions: [
        { value: 'list', label: '列表', icon: 'el-icon-tickets' },
        { value: 'card', label: '档案卡片', icon: 'el-icon-postcard' }
      ],
      residentStatusLabels: { ACTIVE: '在册', MOVED: '迁出', DECEASED: '死亡', ARCHIVED: '归档' },
      householdStatusLabels: { ACTIVE: '有效', MOVED: '迁出', ARCHIVED: '归档' },
      residentStatuses: [
        { label: '在册', value: 'ACTIVE' },
        { label: '迁出', value: 'MOVED' },
        { label: '死亡', value: 'DECEASED' },
        { label: '归档', value: 'ARCHIVED' }
      ],
      householdStatuses: [
        { label: '有效', value: 'ACTIVE' },
        { label: '迁出', value: 'MOVED' },
        { label: '归档', value: 'ARCHIVED' }
      ],
      residentColumns: [
        { prop: 'residentNo', label: '居民编号', minWidth: 150 },
        { prop: 'realName', label: '姓名', minWidth: 120 },
        { prop: 'gridName', label: '所属网格', minWidth: 180 },
        { prop: 'householdNo', label: '家庭户编号', minWidth: 160 },
        { prop: 'phoneMasked', label: '联系电话', minWidth: 150 },
        { prop: 'specialGroupTags', label: '重点标签', minWidth: 180 },
        { prop: 'status', label: '状态', width: 110, labels: { ACTIVE: '在册', MOVED: '迁出', DECEASED: '死亡', ARCHIVED: '归档' } }
      ],
      householdColumns: [
        { prop: 'householdNo', label: '家庭户编号', minWidth: 160 },
        { prop: 'gridName', label: '所属网格', minWidth: 180 },
        { prop: 'address', label: '地址', minWidth: 240 },
        { prop: 'status', label: '状态', width: 110, labels: { ACTIVE: '有效', MOVED: '迁出', ARCHIVED: '归档' } }
      ]
    }
  },
  computed: {
    insightMetrics() {
      return [
        { key: 'residents', label: '居民档案', value: this.insight.residentCount, note: '权限范围内' },
        { key: 'households', label: '家庭户', value: this.insight.householdCount, note: '已建立家庭关系' },
        { key: 'active', label: '在册居民', value: this.insight.active, note: '当前有效档案', tone: 'positive' },
        {
          key: 'key-population',
          label: '重点服务对象',
          value: this.insight.keyPopulationCount,
          note: '至少含一个重点标签',
          tone: 'warning'
        }
      ]
    },
    insightGroups() {
      return [
        {
          key: 'resident-status',
          title: '居民状态',
          items: [
            { key: 'ACTIVE', label: '在册', count: this.insight.active || 0 },
            { key: 'MOVED', label: '迁出', count: this.insight.moved || 0 },
            { key: 'DECEASED', label: '死亡', count: this.insight.deceased || 0 },
            { key: 'ARCHIVED', label: '归档', count: this.insight.archived || 0 }
          ]
        },
        {
          key: 'special-groups',
          title: '重点人群标签',
          items: (this.insight.specialGroups || []).map(item => ({
            ...item,
            label: item.key
          }))
        }
      ]
    },
    canWrite() {
      return this.$store.getters['session/hasPermission']('resident:write')
    },
    canReadSensitive() {
      return this.$store.getters['session/hasPermission']('resident:sensitive:read')
    },
    canReadSensitiveAudit() {
      return this.$store.getters['session/hasPermission']('resident:sensitive:audit:read')
    },
    sensitiveSearchFields() {
      return [
        {
          prop: 'type',
          label: '敏感字段',
          type: 'select',
          required: true,
          span: 12,
          options: [
            { value: 'PHONE', label: '手机号' },
            { value: 'ID_CARD', label: '身份证号' }
          ]
        },
        {
          prop: 'value',
          label: '完整号码',
          required: true,
          maxlength: 64,
          span: 12,
          placeholder: '请输入完整号码，仅用于精确匹配',
          autocomplete: 'off'
        },
        { prop: 'gridId', label: '所属网格', type: 'select', span: 12, options: this.gridOptions },
        { prop: 'status', label: '居民状态', type: 'select', span: 12, options: this.residentStatuses }
      ]
    },
    sensitiveSearchRules() {
      return {
        value: [{
          validator: (rule, value, callback, source) => {
            const normalized = String(value || '').trim()
            if (!normalized) return callback(new Error('完整号码不能为空'))
            const valid = source.type === 'ID_CARD'
              ? /^(\d{15}|\d{17}[0-9Xx])$/.test(normalized)
              : /^\+?[0-9 -]{7,30}$/.test(normalized)
            return valid ? callback() : callback(new Error('请输入有效的完整号码'))
          },
          trigger: 'blur'
        }]
      }
    },
    sensitiveViewRules() {
      return {
        purpose: [
          { required: true, message: '查看用途不能为空', trigger: 'blur' },
          { min: 5, max: 200, message: '查看用途需为 5 至 200 个字符', trigger: 'blur' }
        ]
      }
    },
    dialogTitle() {
      const entity = this.formKind === 'resident' ? '居民' : '家庭户'
      return `${this.formMode === 'create' ? '新增' : '编辑'}${entity}`
    },
    dialogDescription() {
      if (this.formKind === 'resident') {
        return this.formMode === 'create'
          ? '居民编号由后端生成，身份证和手机号将由后端加密保存。'
          : '敏感字段留空表示保持原值；居民归属网格不能通过普通编辑变更。'
      }
      return '家庭户编号由后端生成，家庭户与居民必须属于同一网格。'
    },
    dialogFields() {
      if (this.formKind === 'household') {
        return [
          { prop: 'gridId', label: '所属网格', type: 'select', required: true, options: this.gridOptions, disabled: this.formMode === 'edit', span: 12 },
          { prop: 'buildingNo', label: '楼栋号', maxlength: 50, span: 8 },
          { prop: 'unitNo', label: '单元号', maxlength: 50, span: 8 },
          { prop: 'roomNo', label: '房号', maxlength: 50, span: 8 },
          { prop: 'address', label: '详细地址', required: true, maxlength: 255 }
        ]
      }
      return [
        { prop: 'gridId', label: '所属网格', type: 'select', required: true, options: this.gridOptions, disabled: this.formMode === 'edit', span: 12 },
        { prop: 'householdId', label: '家庭户', type: 'select', options: this.householdOptions, span: 12, help: '可选；家庭户必须与居民属于同一网格。' },
        { prop: 'realName', label: '姓名', required: true, maxlength: 80, span: 12 },
        {
          prop: 'gender',
          label: '性别',
          type: 'select',
          span: 12,
          options: [
            { value: 'MALE', label: '男' },
            { value: 'FEMALE', label: '女' },
            { value: 'OTHER', label: '其他' },
            { value: 'UNKNOWN', label: '未知' }
          ]
        },
        { prop: 'birthDate', label: '出生日期', type: 'date', span: 12 },
        { prop: 'isHouseholder', label: '户主', type: 'switch', activeText: '是', inactiveText: '否', span: 12 },
        {
          prop: 'idCard',
          label: '身份证号',
          maxlength: 18,
          span: 12,
          help: this.formMode === 'edit' ? '留空则保持原身份证信息。' : ''
        },
        {
          prop: 'phone',
          label: '手机号',
          maxlength: 30,
          span: 12,
          help: this.formMode === 'edit' ? '留空则保持原手机号。' : ''
        },
        { prop: 'address', label: '居住地址', required: true, maxlength: 255 },
        { prop: 'specialGroupTags', label: '重点人群标签', type: 'select', multiple: true, options: SPECIAL_TAG_OPTIONS },
        { prop: 'remark', label: '备注', type: 'textarea', rows: 3, maxlength: 500 }
      ]
    },
    dialogRules() {
      return {
        phone: [{ pattern: /^$|^\+?[0-9 -]{7,30}$/, message: '请输入有效联系电话', trigger: 'blur' }],
        idCard: [{ pattern: /^$|^(\d{15}|\d{17}[0-9Xx])$/, message: '请输入 15 位或 18 位身份证号', trigger: 'blur' }]
      }
    }
  },
  watch: {
    activeTab(value) {
      if (!this.$route || this.$route.query.tab === value) return
      const query = { ...this.$route.query, tab: value }
      this.$router.replace({ path: this.$route.path, query }).catch(() => null)
    }
  },
  created() {
    this.loadOptions()
    this.loadInsight()
  },
  beforeDestroy() {
    this.clearSensitiveViewTimer()
  },
  methods: {
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        this.insight = await getResidentInsight()
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    tagText(value) {
      const tags = parseTags(value)
      return tags.length ? tags.join('、') : '无重点标签'
    },
    buildingText(item) {
      return [item.buildingNo, item.unitNo].filter(Boolean).join(' / ') || '—'
    },
    openSensitiveSearch() {
      this.resetSensitiveSearch()
      this.sensitiveSearchVisible = true
      this.loadOptions()
    },
    resetSensitiveSearch() {
      this.sensitiveSearchForm = { type: 'PHONE', value: '', gridId: '', status: '' }
      this.sensitiveSearchError = ''
      this.sensitiveSearchCompleted = false
      this.sensitiveSearchPage = { items: [], total: 0, page: 1, size: 20 }
    },
    openSensitiveAudit() {
      this.sensitiveAuditVisible = true
      this.sensitiveAuditQuery = { action: '', fieldType: '', keyword: '' }
      this.sensitiveAuditPage = { items: [], total: 0, page: 1, size: 20 }
      this.sensitiveAuditError = ''
      this.loadSensitiveAudit()
    },
    async loadSensitiveAudit(page = 1) {
      if (this.sensitiveAuditLoading) return
      this.sensitiveAuditLoading = true
      this.sensitiveAuditError = ''
      try {
        const result = await listSensitiveAccessLogs({
          action: this.sensitiveAuditQuery.action || undefined,
          fieldType: this.sensitiveAuditQuery.fieldType || undefined,
          keyword: this.sensitiveAuditQuery.keyword || undefined,
          page,
          size: this.sensitiveAuditPage.size
        })
        this.sensitiveAuditPage = {
          items: Array.isArray(result.items) ? result.items : [],
          total: Number(result.total) || 0,
          page: Number(result.page) || page,
          size: Number(result.size) || this.sensitiveAuditPage.size
        }
      } catch (error) {
        this.sensitiveAuditError = errorMessage(error)
      } finally {
        this.sensitiveAuditLoading = false
      }
    },
    searchSensitiveAudit() {
      return this.loadSensitiveAudit(1)
    },
    resetSensitiveAudit() {
      this.sensitiveAuditQuery = { action: '', fieldType: '', keyword: '' }
      return this.loadSensitiveAudit(1)
    },
    changeSensitiveAuditPage(page) {
      return this.loadSensitiveAudit(page)
    },
    auditActionLabel(action) {
      return { SEARCH: '敏感检索', VIEW: '敏感查看' }[action] || action || '—'
    },
    auditOperator(row) {
      return row.operatorName || row.operatorUsername || row.operatorUserId || '—'
    },
    auditResident(row) {
      return [row.residentName, row.residentNo].filter(Boolean).join(' · ') || '未关联居民'
    },
    formatAuditDate(value) {
      return formatDateTime(value)
    },
    async searchSensitiveResidents(form, page = 1) {
      if (this.sensitiveSearchLoading) return
      this.sensitiveSearchLoading = true
      this.sensitiveSearchError = ''
      this.sensitiveSearchForm = { ...form }
      try {
        const result = await searchResidentsBySensitiveValue({
          type: form.type,
          value: String(form.value || '').trim(),
          gridId: form.gridId || null,
          status: form.status || null,
          page,
          size: 20
        })
        this.sensitiveSearchPage = {
          items: Array.isArray(result.items) ? result.items : [],
          total: Number(result.total) || 0,
          page: Number(result.page) || page,
          size: Number(result.size) || 20
        }
        this.sensitiveSearchCompleted = true
      } catch (error) {
        this.sensitiveSearchError = errorMessage(error)
      } finally {
        this.sensitiveSearchLoading = false
      }
    },
    changeSensitiveSearchPage(page) {
      return this.searchSensitiveResidents(this.sensitiveSearchForm, page)
    },
    openSensitiveView(row) {
      this.resetSensitiveView()
      this.sensitiveViewTarget = row
      this.sensitiveViewVisible = true
    },
    revealSensitiveData() {
      if (this.sensitiveViewLoading || !this.$refs.sensitiveViewForm) return
      this.$refs.sensitiveViewForm.validate(async valid => {
        if (!valid) return
        this.sensitiveViewLoading = true
        this.sensitiveViewError = ''
        try {
          this.sensitiveViewData = await viewResidentSensitiveData(
            this.sensitiveViewTarget.id,
            this.sensitiveViewForm.purpose.trim()
          )
          this.startSensitiveViewTimer()
        } catch (error) {
          this.sensitiveViewError = errorMessage(error)
        } finally {
          this.sensitiveViewLoading = false
        }
      })
    },
    startSensitiveViewTimer() {
      this.clearSensitiveViewTimer()
      this.sensitiveViewRemaining = 60
      this.sensitiveViewTimer = window.setInterval(() => {
        this.sensitiveViewRemaining -= 1
        if (this.sensitiveViewRemaining <= 0) {
          this.sensitiveViewVisible = false
          this.clearSensitiveViewTimer()
          this.$message.info('敏感信息已自动隐藏')
        }
      }, 1000)
    },
    clearSensitiveViewTimer() {
      if (this.sensitiveViewTimer) window.clearInterval(this.sensitiveViewTimer)
      this.sensitiveViewTimer = null
    },
    resetSensitiveView() {
      this.clearSensitiveViewTimer()
      this.sensitiveViewTarget = null
      this.sensitiveViewForm = { purpose: '' }
      this.sensitiveViewData = null
      this.sensitiveViewLoading = false
      this.sensitiveViewError = ''
      this.sensitiveViewRemaining = 0
    },
    async loadOptions() {
      const [grids, households] = await Promise.allSettled([
        listGrids({ page: 1, size: 100 }),
        listHouseholds({ page: 1, size: 100 })
      ])
      if (grids.status === 'fulfilled') {
        this.gridOptions = pageItems(grids.value).map(item => ({
          value: String(item.id),
          label: `${item.areaName || item.areaCode} (${item.areaCode || item.id})`
        }))
      }
      if (households.status === 'fulfilled') {
        this.householdOptions = pageItems(households.value).map(item => ({
          value: String(item.id),
          label: `${item.householdNo} ${item.address || ''}`.trim()
        }))
      }
    },
    openResidentCreate() {
      this.formKind = 'resident'
      this.formMode = 'create'
      this.formData = {
        gridId: '',
        householdId: '',
        realName: '',
        gender: 'UNKNOWN',
        birthDate: '',
        idCard: '',
        phone: '',
        address: '',
        isHouseholder: false,
        specialGroupTags: [],
        remark: ''
      }
      this.dialogError = ''
      this.formVisible = true
      this.loadOptions()
    },
    async openResidentEdit(row) {
      this.formKind = 'resident'
      this.formMode = 'edit'
      this.dialogError = ''
      try {
        const detail = await getResident(row.id)
        this.formData = {
          ...detail,
          id: String(detail.id),
          householdId: detail.householdId ? String(detail.householdId) : '',
          idCard: '',
          phone: '',
          isHouseholder: Boolean(detail.isHouseholder),
          specialGroupTags: parseTags(detail.specialGroupTags)
        }
        this.formVisible = true
        await this.loadOptions()
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    openHouseholdCreate() {
      this.formKind = 'household'
      this.formMode = 'create'
      this.formData = { gridId: '', buildingNo: '', unitNo: '', roomNo: '', address: '' }
      this.dialogError = ''
      this.formVisible = true
      this.loadOptions()
    },
    async openHouseholdEdit(row) {
      this.formKind = 'household'
      this.formMode = 'edit'
      this.dialogError = ''
      try {
        const detail = await getHousehold(row.id)
        this.formData = { ...detail, id: String(detail.id) }
        this.formVisible = true
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    async saveForm(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      try {
        if (this.formKind === 'resident') await this.saveResident(form)
        else await this.saveHousehold(form)
        this.$message.success(`${this.formMode === 'create' ? '新增' : '编辑'}成功`)
        this.formVisible = false
        await this.reloadActive()
        await this.loadOptions()
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    saveResident(form) {
      const data = {
        householdId: form.householdId || null,
        realName: form.realName,
        gender: form.gender || null,
        birthDate: form.birthDate || null,
        idCard: form.idCard || null,
        phone: form.phone || null,
        address: form.address,
        isHouseholder: Boolean(form.isHouseholder),
        specialGroupTags: form.specialGroupTags || [],
        remark: form.remark || null
      }
      if (this.formMode === 'create') return createResident({ ...data, gridId: form.gridId })
      return updateResident(form.id, { ...data, version: form.version })
    },
    saveHousehold(form) {
      const data = {
        buildingNo: form.buildingNo || null,
        unitNo: form.unitNo || null,
        roomNo: form.roomNo || null,
        address: form.address
      }
      if (this.formMode === 'create') return createHousehold({ ...data, gridId: form.gridId })
      return updateHousehold(form.id, { ...data, version: form.version })
    },
    handleResidentCommand(status, row) {
      this.changeStatus('resident', row, status)
    },
    handleHouseholdCommand(status, row) {
      this.changeStatus('household', row, status)
    },
    async changeStatus(kind, row, status) {
      const labels = { ACTIVE: '恢复有效', MOVED: '标记迁出', DECEASED: '标记死亡', ARCHIVED: '归档' }
      try {
        await this.$confirm(`确认将“${row.realName || row.householdNo}”${labels[status]}？`, '状态变更', {
          type: status === 'ACTIVE' ? 'info' : 'warning'
        })
        if (kind === 'resident') await updateResidentStatus(row.id, status, row.version)
        else await updateHouseholdStatus(row.id, status, row.version)
        this.$message.success('状态已更新')
        await this.reloadActive()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    },
    reloadActive() {
      const ref = this.activeTab === 'residents' ? this.$refs.residentResource : this.$refs.householdResource
      return Promise.all([
        ref ? ref.reload() : Promise.resolve(),
        this.loadInsight()
      ])
    }
  }
}
</script>

<style scoped>
.sensitive-results {
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.sensitive-results__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  color: #303133;
}

.sensitive-results__heading span {
  color: #909399;
  font-size: 13px;
}

.sensitive-results__pagination {
  margin-top: 16px;
  text-align: right;
}

.sensitive-reveal {
  display: grid;
  gap: 18px;
}

.sensitive-reveal dl {
  display: grid;
  gap: 12px;
  margin: 0;
}

.sensitive-reveal dl div {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #f8fafc;
}

.sensitive-reveal dt {
  margin-bottom: 6px;
  color: #909399;
  font-size: 13px;
}

.sensitive-reveal dd {
  margin: 0;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 16px;
  letter-spacing: 0.04em;
  word-break: break-all;
}

.sensitive-audit-query {
  margin: 0 0 14px;
}

.sensitive-audit-table {
  min-height: 150px;
}

.sensitive-audit-empty {
  padding: 18px;
  color: #909399;
  text-align: center;
}

.sensitive-audit-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}

@media (max-width: 640px) {
  .sensitive-results__heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .sensitive-audit-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>
