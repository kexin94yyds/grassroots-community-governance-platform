<template>
  <el-container class="app-shell" :class="`role-shell--${primaryRole.toLowerCase()}`">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <button
      v-if="mobileNavOpen"
      class="nav-scrim"
      type="button"
      aria-label="关闭主导航"
      @click="mobileNavOpen = false"
    />

    <el-aside
      id="primary-navigation"
      class="app-sidebar"
      :class="{ 'is-open': mobileNavOpen }"
      width="232px"
    >
      <router-link class="brand" :to="homePath" :aria-label="`返回${brand.title}`">
        <span class="brand-mark" aria-hidden="true">{{ brand.mark }}</span>
        <span>
          <strong>{{ brand.title }}</strong>
          <small>{{ brand.subtitle }}</small>
        </span>
      </router-link>

      <el-menu
        class="app-menu"
        :default-active="$route.path"
        router
        background-color="transparent"
        text-color="#cbd8e3"
        active-text-color="#ffffff"
        @select="mobileNavOpen = false"
      >
        <el-menu-item v-for="item in navigation" :key="item.path" :index="item.path">
          <i :class="item.meta.icon" />
          <span slot="title">{{ item.meta.title }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-note">
        <span class="sidebar-note-mark" aria-hidden="true" />
        <span>
          <strong>当前职责</strong>
          {{ roleLabel }} · 权限范围由服务端校验
        </span>
      </div>
    </el-aside>

    <el-container class="app-main">
      <el-header class="app-header" height="64px">
        <div class="header-leading">
          <button
            class="mobile-menu-toggle"
            type="button"
            aria-controls="primary-navigation"
            :aria-expanded="String(mobileNavOpen)"
            aria-label="打开主导航"
            @click="mobileNavOpen = true"
          >
            <i class="el-icon-menu" />
          </button>
          <div>
            <p class="header-context">{{ brand.context }}</p>
            <p class="header-trail">
              <span>{{ brand.workspace }}</span>
              <i aria-hidden="true">/</i>
              <strong>{{ $route.meta.title }}</strong>
            </p>
          </div>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <button class="user-menu" type="button">
            <span class="user-avatar">{{ initials }}</span>
            <span class="user-copy">
              <strong>{{ displayName }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
            <i class="el-icon-arrow-down" />
          </button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="password" icon="el-icon-key">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" icon="el-icon-switch-button">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </el-header>
      <el-main id="main-content" class="page-main" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import { resolveHomePath } from '../utils/navigation'

const ROLE_LABELS = {
  SYSTEM_ADMIN: '系统管理员',
  COMMUNITY_STAFF: '社区工作人员',
  GRID_WORKER: '网格员',
  RESIDENT: '居民用户'
}

export default {
  name: 'AppLayout',
  data() {
    return {
      mobileNavOpen: false
    }
  },
  computed: {
    user() {
      return this.$store.state.session.user || {}
    },
    userId() {
      return this.user.id || ''
    },
    displayName() {
      return this.$store.getters['session/displayName'] || '当前用户'
    },
    initials() {
      return this.displayName.slice(-2)
    },
    homePath() {
      return resolveHomePath(this.$router, this.$store)
    },
    roleLabel() {
      return ROLE_LABELS[this.primaryRole] || this.primaryRole || '已登录'
    },
    primaryRole() {
      const roles = this.user.roles || []
      return ['SYSTEM_ADMIN', 'COMMUNITY_STAFF', 'GRID_WORKER', 'RESIDENT'].find(role => roles.includes(role)) || roles[0] || 'AUTHENTICATED'
    },
    brand() {
      const brands = {
        SYSTEM_ADMIN: { mark: '管', title: '全局治理', subtitle: '系统管理工作簿', context: '基层社区网格化综合治理 · 全局视角', workspace: '管理驾驶舱' },
        COMMUNITY_STAFF: { mark: '社', title: '社区治理', subtitle: '社区责任工作簿', context: '基层社区网格化综合治理 · 社区范围', workspace: '社区治理中心' },
        GRID_WORKER: { mark: '巡', title: '网格现场', subtitle: '现场执行工作簿', context: '基层社区网格化综合治理 · 责任网格', workspace: '现场执行台' },
        RESIDENT: { mark: '民', title: '居民服务', subtitle: '居民服务工作簿', context: '基层社区网格化综合治理 · 本人服务', workspace: '居民服务中心' }
      }
      return brands[this.primaryRole] || { mark: '格', title: '网格治理', subtitle: '社区责任工作簿', context: '基层社区网格化综合治理', workspace: '治理工作台' }
    },
    navigationItems() {
      return this.$store.state.navigation.items || []
    },
    navigationStatus() {
      return this.$store.state.navigation.status
    },
    navigation() {
      const layoutRoute = this.$router.options.routes.find(route => route.path === '/')
      const routeItems = (layoutRoute.children || [])
        .filter(route => route.meta && route.meta.nav)
        .filter(route => this.$store.getters['session/hasPermission'](route.meta.permission))
        .map(route => ({ ...route, path: `/${route.path}` }))
      const routeByPath = new Map(routeItems.map(route => [route.path, route]))

      // 服务端只返回当前会话可见且启用的导航。前端仍以已注册路由为白名单，
      // 避免配置错误的 routePath 直接成为可访问页面。
      if (this.navigationStatus === 'ready') {
        return this.navigationItems
          .map(item => {
            const route = routeByPath.get(item.routePath)
            if (!route) return null
            return {
              ...route,
              meta: {
                ...route.meta,
                title: item.name || route.meta.title,
                icon: item.icon || '',
                sortNo: Number(item.sortNo) || 0
              }
            }
          })
          .filter(Boolean)
          .sort((left, right) => left.meta.sortNo - right.meta.sortNo)
      }

      // 导航请求失败时不显示静态菜单，避免仅凭本地权限推断可见入口。
      return []
    }
  },
  watch: {
    '$route.path'() {
      this.mobileNavOpen = false
    },
    userId(value, previousValue) {
      if (value && value !== previousValue) this.refreshNavigation()
    }
  },
  created() {
    this.refreshNavigation()
  },
  methods: {
    async refreshNavigation() {
      try {
        await this.$store.dispatch('navigation/refresh')
      } catch (error) {
        // Store status is already "error"; the layout intentionally stays fail-closed.
        if (this.$route.meta.nav && this.$route.path !== '/forbidden') {
          this.$router.replace('/forbidden').catch(() => null)
        }
      }
    },
    async handleCommand(command) {
      if (command === 'password') {
        this.$router.push('/change-password')
        return
      }
      if (command !== 'logout') return
      try {
        await this.$store.dispatch('session/logout')
      } catch (error) {
        this.$message.warning('服务端注销未确认，本地会话已清理。')
      } finally {
        this.$router.replace('/login').catch(() => null)
      }
    }
  }
}
</script>
