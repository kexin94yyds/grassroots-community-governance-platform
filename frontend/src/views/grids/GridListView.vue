<template>
  <section>
    <ResourceListView
      ref="resource"
      title="网格区域"
      description="维护社区与网格层级、真实空间位置和网格员分配。"
      :fetcher="fetchAreas"
      :columns="columns"
      :status-options="statuses"
      search-placeholder="网格编码或名称"
      manage-permission="grid:write"
      action-label="新增区域"
      :action-column-width="250"
      :view-options="viewOptions"
      :default-view="routeDefaultView"
      @create="openCreate"
    >
      <template #insight>
        <InsightOverview
          title="网格责任概览"
          description="汇总网格覆盖、人员分配与地理数据就绪情况。"
          :loading="insightLoading"
          :error="insightError"
          :metrics="insightMetrics"
          :groups="insightGroups"
          @retry="loadInsight"
        />
      </template>
      <template #filters>
        <el-form-item label="区域类型">
          <el-select v-model="activeAreaType" @change="switchAreaType">
            <el-option label="网格" value="GRID" />
            <el-option label="社区" value="COMMUNITY" />
          </el-select>
        </el-form-item>
      </template>
      <template #rowActions="{ row }">
        <el-button v-if="can('grid:write')" type="text" @click="openEdit(row)">编辑</el-button>
        <el-button
          v-if="canAssign(row)"
          type="text"
          @click="openAssignments(row)"
        >
          {{ row.areaType === 'COMMUNITY' ? '分配社区人员' : '分配网格员' }}
        </el-button>
        <el-button
          v-if="can('grid:write')"
          type="text"
          :class="{ 'danger-text': row.status === 'ENABLED' }"
          @click="toggleStatus(row)"
        >
          {{ row.status === 'ENABLED' ? '停用' : '启用' }}
        </el-button>
      </template>
      <template #alternate="{ view, items, query }">
        <RecordCardGrid
          v-if="view === 'card'"
          :items="items"
          title-prop="areaName"
          eyebrow-prop="areaCode"
          status-prop="status"
          :status-labels="areaStatusLabels"
        >
          <template #default="{ item }">
            <dl class="record-meta">
              <div><dt>区域类型</dt><dd>{{ areaTypeLabel(item.areaType) }}</dd></div>
              <div><dt>所属社区</dt><dd>{{ display(item.communityName) }}</dd></div>
              <div><dt>地址说明</dt><dd>{{ display(item.address) }}</dd></div>
            </dl>
          </template>
          <template #actions="{ item }">
            <el-button v-if="can('grid:write')" type="text" @click="openEdit(item)">编辑</el-button>
            <el-button
              v-if="canAssign(item)"
              type="text"
              @click="openAssignments(item)"
            >
              {{ item.areaType === 'COMMUNITY' ? '分配社区人员' : '分配网格员' }}
            </el-button>
            <el-button
              v-if="can('grid:write')"
              type="text"
              :class="{ 'danger-text': item.status === 'ENABLED' }"
              @click="toggleStatus(item)"
            >
              {{ item.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </RecordCardGrid>

        <SpatialGridMap
          v-else-if="view === 'map'"
          :communities="filteredTopologyCommunities(query)"
          :can-manage="can('grid:write')"
          @edit="openEdit"
        />
      </template>
    </ResourceListView>

    <FormDialog
      :visible.sync="formVisible"
      :title="formMode === 'create' ? '新增社区或网格' : '编辑网格区域'"
      description="区域编码由后端生成；建立网格时必须选择所属社区。"
      :value="formData"
      :fields="gridFields"
      :rules="gridRules"
      :submitting="submitting"
      :error="dialogError"
      width="720px"
      @submit="saveGrid"
    />

    <FormDialog
      :visible.sync="assignVisible"
      :title="assignmentTitle"
      :description="assignmentDescription"
      :value="assignForm"
      :fields="assignmentFields"
      :rules="assignmentRules"
      :submitting="submitting"
      :error="dialogError"
      confirm-text="保存分配"
      @submit="saveAssignments"
    />
  </section>
</template>

<script>
import ResourceListView from '../../components/ResourceListView.vue'
import FormDialog from '../../components/FormDialog.vue'
import InsightOverview from '../../components/InsightOverview.vue'
import RecordCardGrid from '../../components/RecordCardGrid.vue'
import SpatialGridMap from '../../components/SpatialGridMap.vue'
import { getGridInsight } from '../../api/insights'
import {
  assignWorkers,
  createGrid,
  getGrid,
  listCommunityStaffOptions,
  listCommunities,
  listGrids,
  listWorkerOptions,
  updateGrid,
  updateGridStatus
} from '../../api/grids'
import { errorMessage } from '../../utils/data'

function asOptions(result, fallbackLabel) {
  const items = Array.isArray(result) ? result : (result && result.items) || []
  return items.map(item => ({
    value: String(item.id),
    label: item.areaName || item.realName || item.username || `${fallbackLabel} ${item.id}`
  }))
}

export default {
  name: 'GridListView',
  components: { ResourceListView, FormDialog, InsightOverview, RecordCardGrid, SpatialGridMap },
  data() {
    return {
      activeAreaType: 'GRID',
      insight: {},
      insightLoading: false,
      insightError: '',
      formVisible: false,
      assignVisible: false,
      formMode: 'create',
      formData: {},
      assignForm: {},
      activeGridId: '',
      submitting: false,
      dialogError: '',
      communityOptions: [],
      workerOptions: [],
      communityStaffOptions: [],
      activeAssignmentType: 'GRID',
      viewOptions: [
        { value: 'list', label: '列表', icon: 'el-icon-tickets' },
        { value: 'card', label: '区域卡片', icon: 'el-icon-postcard' },
        {
          value: 'map',
          label: '空间地图',
          icon: 'el-icon-map-location',
          scopeLabel: '真实坐标地图 · 当前筛选实时生效',
          hidePagination: true
        }
      ],
      areaStatusLabels: { ENABLED: '启用', DISABLED: '停用' },
      statuses: [{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }],
      columns: [
        { prop: 'areaCode', label: '区域编码', minWidth: 150 },
        { prop: 'areaName', label: '区域名称', minWidth: 180 },
        { prop: 'areaType', label: '区域类型', width: 110, labels: { GRID: '网格', COMMUNITY: '社区' } },
        { prop: 'communityName', label: '所属社区', minWidth: 180 },
        { prop: 'address', label: '地址说明', minWidth: 220 },
        { prop: 'status', label: '状态', width: 110, labels: { ENABLED: '启用', DISABLED: '停用' } }
      ]
    }
  },
  computed: {
    routeDefaultView() {
      return this.$route.name === 'grid-map' ? 'map' : ''
    },
    insightMetrics() {
      const gridCount = Number(this.insight.gridCount || 0)
      const assigned = Number(this.insight.assignedGridCount || 0)
      const geoReady = Number(this.insight.geoReadyGridCount || 0)
      return [
        { key: 'communities', label: '社区数量', value: this.insight.communityCount, note: '责任体系上层' },
        { key: 'grids', label: '网格数量', value: this.insight.gridCount, note: '当前权限范围' },
        {
          key: 'assigned',
          label: '人员覆盖',
          value: assigned,
          note: gridCount ? `覆盖率 ${Math.round((assigned / gridCount) * 100)}%` : '暂无网格',
          tone: assigned === gridCount && gridCount ? 'positive' : 'warning'
        },
        {
          key: 'geo',
          label: '地理数据就绪',
          value: geoReady,
          note: gridCount ? `${geoReady}/${gridCount} 个网格` : '暂无网格',
          tone: geoReady < gridCount ? 'warning' : 'positive'
        }
      ]
    },
    insightGroups() {
      return [{
        key: 'community-grids',
        title: '社区下辖网格',
        items: this.topologyCommunities.map(item => ({
          key: item.id,
          label: item.name,
          count: item.grids.length
        }))
      }]
    },
    topologyCommunities() {
      return Array.isArray(this.insight.communities) ? this.insight.communities : []
    },
    gridFields() {
      const editing = this.formMode === 'edit'
      return [
        {
          prop: 'areaType',
          label: '区域类型',
          type: 'select',
          required: true,
          disabled: editing,
          options: [
            { value: 'COMMUNITY', label: '社区' },
            { value: 'GRID', label: '网格' }
          ],
          span: 12
        },
        {
          prop: 'communityId',
          label: '所属社区',
          type: 'select',
          required: true,
          options: this.communityOptions,
          show: form => form.areaType === 'GRID',
          span: 12
        },
        { prop: 'areaName', label: '区域名称', required: true, maxlength: 100, span: 12 },
        { prop: 'address', label: '地址说明', maxlength: 255, span: 12 },
        { prop: 'centerLongitude', label: '中心点经度', span: 12, placeholder: '例如 120.1234567' },
        { prop: 'centerLatitude', label: '中心点纬度', span: 12, placeholder: '例如 30.1234567' },
        {
          prop: 'boundaryGeojson',
          label: '边界 GeoJSON',
          type: 'textarea',
          rows: 4,
          maxlength: 20000,
          help: '可留空；填写时请输入合法 JSON。'
        }
      ]
    },
    gridRules() {
      const validateGeojson = (rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        try {
          JSON.parse(value)
          callback()
        } catch (error) {
          callback(new Error('请输入合法的 GeoJSON'))
        }
      }
      return {
        boundaryGeojson: [{ validator: validateGeojson, trigger: 'blur' }],
        centerLongitude: [{ pattern: /^-?(180(\\.0+)?|1[0-7]\\d(\\.\\d+)?|\\d{1,2}(\\.\\d+)?)$/, message: '经度范围应为 -180 至 180', trigger: 'blur' }],
        centerLatitude: [{ pattern: /^-?(90(\\.0+)?|[0-8]?\\d(\\.\\d+)?)$/, message: '纬度范围应为 -90 至 90', trigger: 'blur' }]
      }
    },
    assignmentFields() {
      const community = this.activeAssignmentType === 'COMMUNITY'
      const options = community ? this.communityStaffOptions : this.workerOptions
      return [
        { prop: 'workerUserIds', label: community ? '社区工作人员' : '网格员', type: 'select', multiple: true, required: true, options },
        { prop: 'primaryUserId', label: '主负责人', type: 'select', required: true, options }
      ]
    },
    assignmentTitle() {
      return this.activeAssignmentType === 'COMMUNITY' ? '分配社区工作人员' : '分配网格员'
    },
    assignmentDescription() {
      return this.activeAssignmentType === 'COMMUNITY'
        ? '系统管理员可为社区分配多名工作人员，其中必须指定一名主负责人。'
        : '可分配多名网格员，其中主负责人必须在已选择人员中。'
    },
    assignmentRules() {
      return {
        primaryUserId: [{
          validator: (rule, value, callback) => {
            if (!value) callback(new Error('请选择主负责人'))
            else callback()
          },
          trigger: 'change'
        }]
      }
    }
  },
  created() {
    this.loadReferenceOptions()
    this.loadInsight()
  },
  methods: {
    async loadInsight() {
      this.insightLoading = true
      this.insightError = ''
      try {
        this.insight = await getGridInsight()
      } catch (error) {
        this.insightError = errorMessage(error)
      } finally {
        this.insightLoading = false
      }
    },
    areaTypeLabel(value) {
      return { GRID: '网格', COMMUNITY: '社区' }[value] || value || '—'
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '—' : String(value)
    },
    filteredTopologyCommunities(query = {}) {
      const keyword = String(query.keyword || '').trim().toLocaleLowerCase('zh-CN')
      const status = query.status || ''
      if (this.activeAreaType === 'COMMUNITY') {
        return this.topologyCommunities.filter(community => {
          const matchesStatus = !status || community.status === status
          const matchesKeyword = !keyword || [community.code, community.name]
            .some(value => String(value || '').toLocaleLowerCase('zh-CN').includes(keyword))
          return matchesStatus && matchesKeyword
        })
      }
      return this.topologyCommunities
        .map(community => ({
          ...community,
          grids: community.grids.filter(grid => {
            const matchesStatus = !status || grid.status === status
            const matchesKeyword = !keyword || [grid.code, grid.name, grid.address]
              .some(value => String(value || '').toLocaleLowerCase('zh-CN').includes(keyword))
            return matchesStatus && matchesKeyword
          })
        }))
        .filter(community => community.grids.length)
    },
    fetchAreas(params) {
      return listGrids({ ...params, areaType: this.activeAreaType })
    },
    switchAreaType() {
      if (this.$refs.resource) this.$refs.resource.search()
    },
    can(permission) {
      return this.$store.getters['session/hasPermission'](permission)
    },
    canAssign(row) {
      if (!this.can('grid:assign')) return false
      if (row.areaType === 'GRID') return true
      return row.areaType === 'COMMUNITY' && this.$store.getters['session/hasRole']('SYSTEM_ADMIN')
    },
    async loadReferenceOptions() {
      const canAssign = this.can('grid:assign')
      const [communities, workers, staff] = await Promise.allSettled([
        listCommunities(),
        canAssign ? listWorkerOptions() : Promise.resolve([]),
        canAssign ? listCommunityStaffOptions() : Promise.resolve([])
      ])
      if (communities.status === 'fulfilled') this.communityOptions = asOptions(communities.value, '社区')
      if (workers.status === 'fulfilled') this.workerOptions = asOptions(workers.value, '用户')
      if (staff.status === 'fulfilled') this.communityStaffOptions = asOptions(staff.value, '用户')
    },
    openCreate() {
      this.formMode = 'create'
      this.formData = {
        areaType: this.activeAreaType,
        communityId: '',
        areaName: '',
        address: '',
        centerLongitude: '',
        centerLatitude: '',
        boundaryGeojson: ''
      }
      this.dialogError = ''
      this.formVisible = true
      this.loadReferenceOptions()
    },
    async openEdit(row) {
      this.formMode = 'edit'
      this.dialogError = ''
      try {
        const detail = await getGrid(row.id)
        this.formData = {
          ...detail,
          id: String(detail.id),
          areaType: detail.areaType || 'GRID',
          boundaryGeojson: detail.boundaryGeojson || ''
        }
        this.formVisible = true
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    async saveGrid(form) {
      if (this.submitting) return
      this.submitting = true
      this.dialogError = ''
      const common = {
        areaName: form.areaName,
        address: form.address || null,
        centerLongitude: form.centerLongitude === '' ? null : Number(form.centerLongitude),
        centerLatitude: form.centerLatitude === '' ? null : Number(form.centerLatitude),
        boundaryGeojson: form.boundaryGeojson || null
      }
      try {
        if (this.formMode === 'create') {
          await createGrid({
            ...common,
            areaType: form.areaType,
            communityId: form.areaType === 'GRID' ? form.communityId : null
          })
        } else {
          await updateGrid(form.id, { ...common, version: form.version })
        }
        this.$message.success(this.formMode === 'create' ? '区域创建成功' : '区域资料已更新')
        this.formVisible = false
        if (this.formMode === 'create') this.activeAreaType = form.areaType
        await this.$nextTick()
        await Promise.all([this.$refs.resource.search(), this.loadInsight()])
      } catch (error) {
        this.dialogError = errorMessage(error)
      } finally {
        this.submitting = false
      }
    },
    async openAssignments(row) {
      this.activeGridId = String(row.id)
      this.activeAssignmentType = row.areaType || 'GRID'
      this.dialogError = ''
      try {
        const detail = await getGrid(row.id)
        const assignments = Array.isArray(detail.assignments) ? detail.assignments : []
        this.assignForm = {
          workerUserIds: assignments.map(item => String(item.userId)),
          primaryUserId: String((assignments.find(item => item.primary === true || item.isPrimary === true) || {}).userId || ''),
          version: detail.version
        }
        this.assignVisible = true
        await this.loadReferenceOptions()
      } catch (error) {
        this.$message.error(errorMessage(error))
      }
    },
    async saveAssignments(form) {
      if (!form.workerUserIds.includes(form.primaryUserId)) {
        this.dialogError = '主负责人必须包含在已选择的网格员中'
        return
      }
      this.submitting = true
      this.dialogError = ''
      try {
        await assignWorkers(this.activeGridId, {
          version: form.version,
          assignments: form.workerUserIds.map(userId => ({
            userId: String(userId),
            isPrimary: String(userId) === String(form.primaryUserId)
          }))
        })
        this.$message.success('网格员分配已保存')
        this.assignVisible = false
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
        await this.$confirm(
          next === 'DISABLED' ? '停用前请确认该网格没有未办结事项，是否继续？' : '确认重新启用该网格？',
          next === 'DISABLED' ? '停用网格' : '启用网格',
          { type: next === 'DISABLED' ? 'warning' : 'info' }
        )
        await updateGridStatus(row.id, next, row.version)
        this.$message.success(next === 'DISABLED' ? '网格已停用' : '网格已启用')
        await Promise.all([this.$refs.resource.reload(), this.loadInsight()])
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.$message.error(errorMessage(error))
      }
    }
  }
}
</script>
