<template>
  <main class="login-page">
    <section class="login-context" aria-labelledby="platform-title">
      <div class="login-context-inner">
        <span class="context-mark" aria-hidden="true">格</span>
        <p class="context-label">社区治理工作簿 · COMMUNITY LEDGER</p>
        <h1 id="platform-title">每一件民生事项，都有坐标、有回应、有结果。</h1>
        <p class="context-copy">让居民诉求落到责任网格，让每次处置留下清晰脉络。</p>

        <ol class="context-route" aria-label="平台治理路径">
          <li>
            <span class="route-index">01</span>
            <span><strong>社区入口</strong><small>汇集居民与事项信息</small></span>
          </li>
          <li>
            <span class="route-index">02</span>
            <span><strong>责任网格</strong><small>明确人员与处置边界</small></span>
          </li>
          <li>
            <span class="route-index">03</span>
            <span><strong>事项闭环</strong><small>受理、处置、复核留痕</small></span>
          </li>
        </ol>
      </div>
    </section>

    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-card">
        <p class="login-overline">基层社区网格化综合治理</p>
        <h2 id="login-title">进入治理工作台</h2>
        <p class="login-hint">使用平台分配的工作账号登录。</p>

        <el-alert
          v-if="$route.query.reason === 'unavailable'"
          class="login-alert"
          title="暂时无法连接服务，请确认后端已启动。"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="error"
          class="login-alert"
          :title="error"
          type="error"
          show-icon
          :closable="false"
        />

        <el-form
          ref="form"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.native.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model.trim="form.username"
              autocomplete="username"
              prefix-icon="el-icon-user"
              placeholder="请输入用户名"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              prefix-icon="el-icon-lock"
              placeholder="请输入密码"
              show-password
              @keyup.enter.native="submit"
            />
          </el-form-item>
          <el-button class="login-submit" type="primary" native-type="submit" :loading="submitting">
            进入工作台
          </el-button>
        </el-form>
        <div class="login-register-row">
          <span>还没有平台账号？</span>
          <router-link to="/register">提交注册申请</router-link>
        </div>
        <p class="login-security"><i class="el-icon-lock" /> 登录状态仅用于当前浏览器，本机不保存访问令牌。</p>
      </div>
    </section>
  </main>
</template>

<script>
import { initializeCsrf } from '../../api/auth'
import { errorMessage } from '../../utils/data'
import { isUsableNavigationPath, resolveHomePath } from '../../utils/navigation'

export default {
  name: 'LoginView',
  data() {
    return {
      submitting: false,
      error: '',
      form: { username: '', password: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  created() {
    initializeCsrf().catch(() => null)
  },
  methods: {
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid || this.submitting) return
        this.submitting = true
        this.error = ''
        try {
          const user = await this.$store.dispatch('session/login', this.form)
          if (user.passwordChangeRequired) {
            this.$router.replace('/change-password')
            return
          }
          const homePath = resolveHomePath(this.$router, this.$store)
          const requestedRedirect = this.$route.query.redirect
          const redirect = isUsableNavigationPath(this.$router, this.$store, requestedRedirect)
            ? requestedRedirect
            : homePath
          this.$router.replace(redirect)
        } catch (error) {
          this.error = errorMessage(error)
        } finally {
          this.submitting = false
        }
      })
    }
  }
}
</script>
