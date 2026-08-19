import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '../store'
import { isUsableNavigationPath, resolveHomePath } from '../utils/navigation'

Vue.use(VueRouter)

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/auth/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/auth/RegistrationView.vue'),
    meta: { public: true, title: '注册申请' }
  },
  {
    path: '/change-password',
    name: 'password-change',
    component: () => import('../views/auth/PasswordChangeView.vue'),
    meta: { title: '修改密码' }
  },
  {
    path: '/',
    component: () => import('../layouts/AppLayout.vue'),
    children: [
      {
        path: 'admin/home',
        name: 'admin-home',
        component: () => import('../views/workbenches/RoleHomeView.vue'),
        meta: { title: '管理驾驶舱', icon: 'el-icon-odometer', permission: 'workbench:admin:read', nav: true, menuCode: 'ADMIN_HOME' }
      },
      {
        path: 'community/home',
        name: 'community-home',
        component: () => import('../views/workbenches/RoleHomeView.vue'),
        meta: { title: '社区治理中心', icon: 'el-icon-house', permission: 'workbench:community:read', nav: true, menuCode: 'COMMUNITY_HOME' }
      },
      {
        path: 'community/todo',
        name: 'community-todo',
        component: () => import('../views/services/ServiceApplicationListView.vue'),
        meta: { title: '社区待办', icon: 'el-icon-s-order', permission: 'workbench:community:read', nav: true, menuCode: 'COMMUNITY_TODO' }
      },
      {
        path: 'community/service',
        name: 'community-service',
        component: () => import('../views/services/ServiceApplicationListView.vue'),
        meta: { title: '社区服务', icon: 'el-icon-service', permission: 'service:application:read', nav: true, menuCode: 'COMMUNITY_SERVICE' }
      },
      {
        path: 'community/patrol',
        name: 'community-patrol',
        component: () => import('../views/patrols/PatrolPlanListView.vue'),
        meta: { title: '社区巡查', icon: 'el-icon-map-location', permission: 'patrol:read', nav: true, menuCode: 'COMMUNITY_PATROL' }
      },
      {
        path: 'community/report',
        name: 'community-report',
        component: () => import('../views/workbenches/CommunityReportView.vue'),
        meta: { title: '社区报表', icon: 'el-icon-data-analysis', permission: 'workbench:community:read', nav: true, menuCode: 'COMMUNITY_REPORT' }
      },
      {
        path: 'grid/home',
        name: 'grid-home',
        component: () => import('../views/workbenches/RoleHomeView.vue'),
        meta: { title: '网格执行台', icon: 'el-icon-s-cooperation', permission: 'workbench:grid:read', nav: true, menuCode: 'GRID_HOME' }
      },
      {
        path: 'grid/patrol',
        name: 'grid-patrol',
        component: () => import('../views/patrols/PatrolPlanListView.vue'),
        meta: { title: '我的巡查', icon: 'el-icon-map-location', permission: 'patrol:read', nav: true, menuCode: 'GRID_PATROL' }
      },
      {
        path: 'grid/event-report',
        name: 'grid-event-report',
        component: () => import('../views/events/EventListView.vue'),
        meta: { title: '现场上报', icon: 'el-icon-warning-outline', permission: 'event:report', nav: true, menuCode: 'GRID_EVENT_REPORT' }
      },
      {
        path: 'grid/tasks',
        name: 'grid-task',
        component: () => import('../views/tasks/TaskListView.vue'),
        meta: { title: '我的任务', icon: 'el-icon-finished', permission: 'task:read', nav: true, menuCode: 'GRID_TASK' }
      },
      {
        path: 'grid/map',
        name: 'grid-map',
        component: () => import('../views/grids/GridListView.vue'),
        meta: { title: '责任地图', icon: 'el-icon-place', permission: 'grid:read', nav: true, menuCode: 'GRID_MAP' }
      },
      {
        path: 'grid/history',
        name: 'grid-history',
        component: () => import('../views/scoped/GridHistoryView.vue'),
        meta: { title: '工作历史', icon: 'el-icon-tickets', permission: 'workbench:grid:read', nav: true, menuCode: 'GRID_HISTORY' }
      },
      {
        path: 'resident/home',
        name: 'resident-home',
        component: () => import('../views/workbenches/RoleHomeView.vue'),
        meta: { title: '居民服务中心', icon: 'el-icon-house', permission: 'workbench:resident:read', nav: true, menuCode: 'RESIDENT_PORTAL' }
      },
      {
        path: 'resident/report',
        name: 'resident-report',
        component: () => import('../views/residents/ResidentPortalView.vue'),
        meta: { title: '事项上报', icon: 'el-icon-edit-outline', permission: 'resident:portal', nav: true, menuCode: 'RESIDENT_REPORT' }
      },
      {
        path: 'resident/events',
        name: 'resident-events',
        component: () => import('../views/residents/ResidentPortalView.vue'),
        meta: { title: '我的事项', icon: 'el-icon-tickets', permission: 'resident:portal', nav: true, menuCode: 'RESIDENT_EVENTS' }
      },
      {
        path: 'resident/profile',
        name: 'resident-profile',
        component: () => import('../views/residents/ResidentPortalView.vue'),
        meta: { title: '我的档案', icon: 'el-icon-user', permission: 'resident:portal', nav: true, menuCode: 'RESIDENT_PROFILE' }
      },
      {
        path: 'resident/service',
        name: 'resident-service',
        component: () => import('../views/services/ServiceApplicationListView.vue'),
        meta: { title: '服务申请', icon: 'el-icon-service', permission: 'resident:portal', nav: true, menuCode: 'RESIDENT_SERVICE' }
      },
      {
        path: 'resident/ratings',
        name: 'resident-rating',
        component: () => import('../views/services/ServiceApplicationListView.vue'),
        meta: { title: '服务评价', icon: 'el-icon-star-on', permission: 'resident:portal', nav: true, menuCode: 'RESIDENT_RATING' }
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '治理概览', icon: 'el-icon-data-analysis', permission: 'dashboard:read', nav: true, menuCode: 'DASHBOARD' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('../views/system/UserListView.vue'),
        meta: { title: '用户权限', icon: 'el-icon-user', permission: 'system:user:manage', nav: true, menuCode: 'SYSTEM_USER' }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('../views/system/RoleListView.vue'),
        meta: { title: '角色管理', icon: 'el-icon-s-custom', permission: 'system:role:manage', nav: true, menuCode: 'SYSTEM_ROLE' }
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        component: () => import('../views/system/MenuListView.vue'),
        meta: { title: '菜单权限', icon: 'el-icon-menu', permission: 'system:menu:manage', nav: true, menuCode: 'SYSTEM_MENU' }
      },
      {
        path: 'system/event-categories',
        name: 'system-event-categories',
        component: () => import('../views/system/EventCategoryListView.vue'),
        meta: { title: '事件类别', icon: 'el-icon-collection-tag', permission: 'event:category:manage', nav: true, menuCode: 'EVENT_CATEGORY' }
      },
      {
        path: 'system/service-catalogs',
        name: 'system-service-catalogs',
        component: () => import('../views/services/ServiceCatalogListView.vue'),
        meta: { title: '服务目录', icon: 'el-icon-collection-tag', permission: 'service:catalog:manage', nav: true, menuCode: 'SERVICE_CATALOG' }
      },
      {
        path: 'system/operations',
        name: 'system-operations',
        component: () => import('../views/system/SystemAuditView.vue'),
        meta: { title: '管理审计', icon: 'el-icon-document', permission: 'system:audit:read', nav: true, menuCode: 'ADMIN_AUDIT' }
      },
      {
        path: 'system/health',
        name: 'system-health',
        component: () => import('../views/system/SystemHealthView.vue'),
        meta: { title: '系统健康', icon: 'el-icon-odometer', permission: 'system:health:read', nav: true, menuCode: 'SYSTEM_HEALTH' }
      },
      {
        path: 'announcements',
        name: 'announcements',
        component: () => import('../views/announcements/AnnouncementListView.vue'),
        meta: { title: '社区公告', icon: 'el-icon-bell', permission: 'announcement:read', nav: true, menuCode: 'ANNOUNCEMENT' }
      },
      {
        path: 'grids',
        name: 'grids',
        component: () => import('../views/grids/GridListView.vue'),
        meta: { title: '网格区域', icon: 'el-icon-place', permission: 'grid:read', nav: true, menuCode: 'GRID' }
      },
      {
        path: 'residents',
        name: 'residents',
        component: () => import('../views/residents/ResidentListView.vue'),
        meta: { title: '居民档案', icon: 'el-icon-s-custom', permission: 'resident:read', nav: true, menuCode: 'RESIDENT' }
      },
      {
        path: 'events',
        name: 'events',
        component: () => import('../views/events/EventListView.vue'),
        meta: { title: '治理事件', icon: 'el-icon-warning-outline', permission: 'event:read', nav: true, menuCode: 'EVENT' }
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('../views/tasks/TaskListView.vue'),
        meta: { title: '网格任务', icon: 'el-icon-finished', permission: 'task:read', nav: true, menuCode: 'TASK' }
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('../views/errors/ForbiddenView.vue'),
        meta: { title: '无权访问' }
      }
    ]
  },
  {
    path: '*',
    component: () => import('../views/errors/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = new VueRouter({
  mode: 'history',
  routes,
  scrollBehavior: () => ({ x: 0, y: 0 })
})

router.beforeEach(async (to, from, next) => {
  document.title = `${to.meta.title || '管理端'} | 社区网格治理平台`
  if (to.meta.public) {
    if (to.name === 'login' && store.getters['session/authenticated']) {
      if (store.state.navigation.status !== 'ready') {
        await store.dispatch('navigation/refresh').catch(() => null)
      }
      next(resolveHomePath(router, store))
      return
    }
    next()
    return
  }

  try {
    const user = await store.dispatch('session/bootstrap')
    if (!user) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    if (user.passwordChangeRequired && to.name !== 'password-change') {
      next('/change-password')
      return
    }
    if (to.path === '/') {
      if (store.state.navigation.status !== 'ready') {
        await store.dispatch('navigation/refresh').catch(() => null)
      }
      next(resolveHomePath(router, store))
      return
    }
    if (to.meta.nav && !isUsableNavigationPath(router, store, to.path)) {
      next('/forbidden')
      return
    }
    if (to.meta.permission && !store.getters['session/hasPermission'](to.meta.permission)) {
      next('/forbidden')
      return
    }
    next()
  } catch (error) {
    next({ path: '/login', query: { redirect: to.fullPath, reason: 'unavailable' } })
  }
})

export default router
