import { getNavigation } from '../../api/auth'

function normalizeNavigation(items) {
  return Array.isArray(items) ? items : []
}

export default {
  namespaced: true,
  state: {
    items: [],
    status: 'idle'
  },
  mutations: {
    SET_NAVIGATION(state, items) {
      state.items = normalizeNavigation(items)
      state.status = 'ready'
    },
    SET_STATUS(state, status) {
      state.status = status
    },
    CLEAR_NAVIGATION(state) {
      state.items = []
      state.status = 'idle'
    }
  },
  actions: {
    async refresh({ commit }) {
      commit('SET_STATUS', 'loading')
      try {
        const result = await getNavigation()
        const items = Array.isArray(result) ? result : (result && result.items) || []
        commit('SET_NAVIGATION', items)
        return items
      } catch (error) {
        // Only a failed request permits the layout to use its static fallback.
        // An empty successful result is an authoritative, intentionally empty menu.
        commit('SET_STATUS', 'error')
        throw error
      }
    }
  }
}
