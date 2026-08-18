import Vue from 'vue'
import {
  Alert,
  Aside,
  Button,
  Col,
  Container,
  DatePicker,
  Dialog,
  Dropdown,
  DropdownItem,
  DropdownMenu,
  Form,
  FormItem,
  Header,
  Input,
  Loading,
  Main,
  Menu,
  MenuItem,
  Message,
  MessageBox,
  Option,
  Pagination,
  Radio,
  RadioButton,
  RadioGroup,
  Row,
  Select,
  Switch,
  TabPane,
  Table,
  TableColumn,
  Tabs,
  Tag,
  Upload
} from 'element-ui'
import App from './App.vue'
import router from './router'
import store from './store'
import { setUnauthorizedHandler } from './utils/http'
import './styles/index.css'

[
  Alert,
  Aside,
  Button,
  Col,
  Container,
  DatePicker,
  Dialog,
  Dropdown,
  DropdownItem,
  DropdownMenu,
  Form,
  FormItem,
  Header,
  Input,
  Main,
  Menu,
  MenuItem,
  Option,
  Pagination,
  Radio,
  RadioButton,
  RadioGroup,
  Row,
  Select,
  Switch,
  TabPane,
  Table,
  TableColumn,
  Tabs,
  Tag,
  Upload
].forEach(component => Vue.use(component))
Vue.use(Loading.directive)
Vue.prototype.$message = Message
Vue.prototype.$confirm = MessageBox.confirm
Vue.config.productionTip = false

setUnauthorizedHandler(() => {
  const guardIsResolving = store.state.session.status === 'loading'
  const redirect = router.currentRoute.fullPath
  store.commit('session/CLEAR_SESSION')
  if (!guardIsResolving && router.currentRoute.path !== '/login') {
    router.replace({ path: '/login', query: { redirect } }).catch(() => null)
  }
})

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
