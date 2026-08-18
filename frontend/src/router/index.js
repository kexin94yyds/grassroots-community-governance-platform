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
        path: 'resident/home',
        name: 'resident-home',
        component: () => import('../views/residents/ResidentPortalView.vue'),
        meta: { title: '居民服务台', icon: 'el-icon-house', permission: 'resident:portal', nav: true }
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '治理概览', icon: 'el-icon-data-analysis', permission: 'dashboard:read', nav: true }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('../views/system/UserListView.vue'),
        meta: { title: '用户权限', icon: 'el-icon-user', permission: 'system:user:manage', nav: true }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('../views/system/RoleListView.vue'),
        meta: { title: '角色管理', icon: 'el-icon-s-custom', permission: 'system:role:manage', nav: true }
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        component: () => import('../views/system/MenuListView.vue'),
        meta: { title: '菜单权限', icon: 'el-icon-menu', permission: 'system:menu:manage', nav: true }
      },
      {
        path: 'system/event-categories',
        name: 'system-event-categories',
        component: () => import('../views/system/EventCategoryListView.vue'),
        meta: { title: '事件类别', icon: 'el-icon-collection-tag', permission: 'event:category:manage', nav: true }
      },
      {
        path: 'grids',
        name: 'grids',
        component: () => import('../views/grids/GridListView.vue'),
        meta: { title: '网格区域', icon: 'el-icon-place', permission: 'grid:read', nav: true }
      },
      {
        path: 'residents',
        name: 'residents',
        component: () => import('../views/residents/ResidentListView.vue'),
        meta: { title: '居民档案', icon: 'el-icon-s-custom', permission: 'resident:read', nav: true }
      },
      {
        path: 'events',
        name: 'events',
        component: () => import('../views/events/EventListView.vue'),
        meta: { title: '治理事件', icon: 'el-icon-warning-outline', permission: 'event:read', nav: true }
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('../views/tasks/TaskListView.vue'),
        meta: { title: '网格任务', icon: 'el-icon-finished', permission: 'task:read', nav: true }
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
