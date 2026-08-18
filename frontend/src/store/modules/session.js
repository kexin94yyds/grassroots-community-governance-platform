import * as authApi from '../../api/auth'

function normalizeCodes(values) {
  return Array.isArray(values)
    ? values.map(item => typeof item === 'string' ? item : item.code).filter(Boolean)
    : []
}

function normalizeUser(user) {
  if (!user) return null
  return {
    ...user,
    id: user.id == null ? null : String(user.id),
    roles: normalizeCodes(user.roles),
    permissions: normalizeCodes(user.permissions)
  }
}

export default {
  namespaced: true,
  state: {
    user: null,
    status: 'idle'
  },
  getters: {
    authenticated: state => Boolean(state.user),
    displayName: state => (state.user && (state.user.realName || state.user.username)) || '',
    hasRole: state => role => Boolean(state.user && state.user.roles.includes(role)),
    hasPermission: state => permission => {
      if (!permission) return true
      if (!state.user) return false
      return state.user.permissions.includes(permission)
    }
  },
  mutations: {
    SET_STATUS(state, status) {
      state.status = status
    },
    SET_USER(state, user) {
      state.user = normalizeUser(user)
      state.status = user ? 'ready' : 'anonymous'
    },
    CLEAR_SESSION(state) {
      state.user = null
      state.status = 'anonymous'
    }
  },
  actions: {
    async bootstrap({ state, commit, dispatch, rootState }) {
      if (state.status === 'ready') {
        if (rootState.navigation.status === 'idle') {
          await dispatch('navigation/refresh', null, { root: true }).catch(() => null)
        }
        return state.user
      }
      commit('SET_STATUS', 'loading')
      try {
        const user = await authApi.getCurrentUser()
        commit('SET_USER', user)
        await dispatch('navigation/refresh', null, { root: true }).catch(() => null)
        return user
      } catch (error) {
        if (error.status === 401) {
          commit('CLEAR_SESSION')
          return null
        }
        commit('SET_STATUS', 'error')
        throw error
      }
    },
    async login({ commit, dispatch }, credentials) {
      commit('SET_STATUS', 'loading')
      await authApi.initializeCsrf()
      await authApi.login(credentials)
      await authApi.initializeCsrf()
      const user = await authApi.getCurrentUser()
      commit('SET_USER', user)
      // Navigation is session-scoped. Refresh it after a successful login so a
      // previously cached menu can never leak into a new user's view.
      await dispatch('navigation/refresh', null, { root: true }).catch(() => null)
      return user
    },
    async changePassword({ commit }, data) {
      await authApi.changePassword(data)
      commit('CLEAR_SESSION')
      commit('navigation/CLEAR_NAVIGATION', null, { root: true })
      await authApi.initializeCsrf().catch(() => null)
    },
    async logout({ commit }) {
      try {
        await authApi.logout()
      } finally {
        commit('CLEAR_SESSION')
        commit('navigation/CLEAR_NAVIGATION', null, { root: true })
        await authApi.initializeCsrf().catch(() => null)
      }
    }
  }
}
