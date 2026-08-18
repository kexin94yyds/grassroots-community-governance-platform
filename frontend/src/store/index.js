import Vue from 'vue'
import Vuex from 'vuex'
import session from './modules/session'
import navigation from './modules/navigation'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    session,
    navigation
  }
})
