<template>
  <section>
    <PageHeader :title="title" :description="description">
      <template v-if="$scopedSlots.actions || (managePermission && canManage)" #actions>
        <slot name="actions" :reload="load">
          <el-button type="primary" icon="el-icon-plus" @click="handleCreate">
            {{ actionLabel }}
          </el-button>
        </slot>
      </template>
    </PageHeader>

    <slot name="insight" />

    <div class="workspace-panel">
      <div class="query-toolbar">
        <el-form class="query-bar" :inline="true" @submit.native.prevent="search">
          <el-form-item :label="searchLabel">
            <el-input
              v-model.trim="query.keyword"
              clearable
              :placeholder="searchPlaceholder"
              prefix-icon="el-icon-search"
              @keyup.enter.native="search"
            />
          </el-form-item>
          <el-form-item v-if="statusOptions.length" label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <slot name="filters" :query="query" />
          <el-form-item>
            <el-button type="primary" native-type="submit">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>

        <div v-if="viewOptions.length > 1" class="view-switcher-wrap">
          <span>呈现方式</span>
          <ViewSwitcher
            :value="activeView"
            :options="viewOptions"
            @input="changeView"
          />
        </div>
      </div>

      <el-alert
        v-if="error"
        class="state-alert"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      >
        <el-button slot="default" type="text" @click="load">重新加载</el-button>
      </el-alert>

      <template v-else-if="activeView === 'list'">
        <p class="table-scroll-hint">
          <i class="el-icon-d-caret" aria-hidden="true" />
          左右滑动可查看完整字段
        </p>

        <el-table
          class="resource-table"
          v-loading="loading"
          :data="page.items"
          row-key="id"
        >
          <template slot="empty">
            <div class="resource-empty">
              <span class="empty-coordinate" aria-hidden="true">0</span>
              <strong>暂无符合条件的数据</strong>
              <p>可以调整筛选条件，或创建第一条业务记录。</p>
              <el-button
                v-if="managePermission && canManage"
                type="text"
                icon="el-icon-plus"
                @click="handleCreate"
              >
                {{ actionLabel }}
              </el-button>
            </div>
          </template>
          <el-table-column
            v-for="column in columns"
            :key="column.prop"
            :prop="column.prop"
            :label="column.label"
            :min-width="column.minWidth || 120"
            :width="column.width"
          >
            <template slot-scope="{ row }">
              <el-tag
                v-if="column.labels"
                size="small"
                :type="tagType(row[column.prop])"
                effect="plain"
              >
                {{ column.labels[row[column.prop]] || row[column.prop] || '-' }}
              </el-tag>
              <span v-else-if="column.date">{{ formatDate(row[column.prop]) }}</span>
              <span v-else>{{ display(row[column.prop]) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            v-if="$scopedSlots.rowActions"
            :fixed="isNarrow ? false : 'right'"
            label="操作"
            :width="actionColumnWidth"
            align="right"
          >
            <template slot-scope="{ row }">
              <slot name="rowActions" :row="row" :reload="load" />
            </template>
          </el-table-column>
        </el-table>
      </template>

      <div v-else v-loading="loading" class="alternate-view">
        <p class="alternate-context">
          {{ alternateContext }}
        </p>
        <slot
          name="alternate"
          :view="activeView"
          :items="page.items"
          :page="page"
          :query="query"
          :reload="load"
        />
      </div>

      <div v-if="showPagination" class="pagination-row">
        <span>共 {{ page.total }} 条</span>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page.page"
          :page-size="page.size"
          :total="page.total"
          @current-change="changePage"
        />
      </div>
    </div>
  </section>
</template>

<script>
import PageHeader from './PageHeader.vue'
import ViewSwitcher from './ViewSwitcher.vue'
import { asPage, errorMessage, formatDateTime } from '../utils/data'
import { STATUS_TAG_TYPE } from '../constants/domain'

export default {
  name: 'ResourceListView',
  components: { PageHeader, ViewSwitcher },
  props: {
    title: { type: String, required: true },
    description: { type: String, required: true },
    fetcher: { type: Function, required: true },
    columns: { type: Array, required: true },
    statusOptions: { type: Array, default: () => [] },
    searchLabel: { type: String, default: '关键词' },
    searchPlaceholder: { type: String, default: '请输入关键词' },
    managePermission: { type: String, default: '' },
    actionLabel: { type: String, default: '新建' },
    actionColumnWidth: { type: Number, default: 220 },
    viewOptions: {
      type: Array,
      default: () => [{ value: 'list', label: '列表', icon: 'el-icon-tickets' }]
    },
    defaultView: { type: String, default: '' }
  },
  data() {
    const isNarrow = typeof window !== 'undefined' && window.innerWidth <= 820
    const routeView = this.$route && this.$route.query.view
    const values = this.viewOptions.map(item => item.value)
    const responsiveDefault = isNarrow && values.includes('card') ? 'card' : values[0]
    return {
      loading: false,
      error: '',
      isNarrow,
      activeView: values.includes(routeView)
        ? routeView
        : (values.includes(this.defaultView) ? this.defaultView : responsiveDefault),
      query: { keyword: '', status: '' },
      page: { items: [], total: 0, page: 1, size: 20 }
    }
  },
  computed: {
    canManage() {
      return this.$store.getters['session/hasPermission'](this.managePermission)
    },
    alternateContext() {
      const option = this.viewOptions.find(item => item.value === this.activeView)
      return (option && option.scopeLabel) ||
        `当前查询结果 · 第 ${this.page.page} 页 · 本页 ${this.page.items.length} 条`
    },
    showPagination() {
      const option = this.viewOptions.find(item => item.value === this.activeView)
      return !option || option.hidePagination !== true
    }
  },
  watch: {
    '$route.query.view'(value) {
      if (this.viewOptions.some(item => item.value === value)) this.activeView = value
    }
  },
  created() {
    this.load()
  },
  mounted() {
    window.addEventListener('resize', this.updateViewport)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updateViewport)
  },
  methods: {
    updateViewport() {
      this.isNarrow = window.innerWidth <= 820
    },
    changeView(view) {
      if (!this.viewOptions.some(item => item.value === view) || view === this.activeView) return
      this.activeView = view
      const query = { ...this.$route.query, view }
      this.$router.replace({ path: this.$route.path, query }).catch(() => null)
      this.$emit('view-change', view)
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        this.page = asPage(await this.fetcher({
          page: this.page.page,
          size: this.page.size,
          keyword: this.query.keyword || undefined,
          status: this.query.status || undefined
        }))
        this.$emit('loaded', this.page)
      } catch (error) {
        this.error = errorMessage(error)
      } finally {
        this.loading = false
      }
    },
    search() {
      this.page.page = 1
      return this.load()
    },
    reset() {
      this.query = { keyword: '', status: '' }
      return this.search()
    },
    changePage(page) {
      this.page.page = page
      return this.load()
    },
    display(value) {
      return value === null || value === undefined || value === '' ? '-' : String(value)
    },
    formatDate(value) {
      return formatDateTime(value)
    },
    tagType(status) {
      return STATUS_TAG_TYPE[status] || 'info'
    },
    showScaffoldNotice() {
      this.$message.info('页面骨架已预留，表单将在对应业务接口完成后接入。')
    },
    handleCreate() {
      if (this.$listeners.create) {
        this.$emit('create')
        return
      }
      this.showScaffoldNotice()
    },
    reload() {
      return this.load()
    }
  }
}
</script>
