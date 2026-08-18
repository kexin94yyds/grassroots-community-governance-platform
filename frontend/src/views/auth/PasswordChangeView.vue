<template>
  <main class="password-page">
    <section class="password-card" aria-labelledby="password-title">
      <p class="password-overline">账号安全</p>
      <h1 id="password-title">{{ required ? '首次登录必须修改密码' : '修改登录密码' }}</h1>
      <p class="password-hint">
        {{ required ? '管理员已重置临时密码，完成修改后才能进入治理工作台。' : '修改成功后，所有已登录会话都会失效。' }}
      </p>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.native.prevent="submit">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password @keyup.enter.native="submit" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting">确认修改</el-button>
        <el-button v-if="!required" :disabled="submitting" @click="$router.back()">返回</el-button>
      </el-form>
    </section>
  </main>
</template>

<script>
import { errorMessage } from '../../utils/data'

export default {
  name: 'PasswordChangeView',
  data() {
    const confirmPassword = (rule, value, callback) => {
      if (value !== this.form.newPassword) callback(new Error('两次输入的新密码不一致'))
      else callback()
    }
    return {
      submitting: false,
      error: '',
      form: { oldPassword: '', newPassword: '', confirmPassword: '' },
      rules: {
        oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 8, max: 128, message: '密码长度需为 8—128 位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请再次输入新密码', trigger: 'blur' },
          { validator: confirmPassword, trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    required() {
      return Boolean(this.$store.state.session.user && this.$store.state.session.user.passwordChangeRequired)
    }
  },
  methods: {
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid || this.submitting) return
        this.submitting = true
        this.error = ''
        try {
          await this.$store.dispatch('session/changePassword', {
            oldPassword: this.form.oldPassword,
            newPassword: this.form.newPassword
          })
          this.$message.success('密码已修改，请使用新密码重新登录')
          this.$router.replace('/login')
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

<style scoped>
.password-page { min-height: 100vh; display: grid; place-items: center; padding: 32px; background: var(--canvas); }
.password-card { width: min(460px, 100%); padding: 36px; border: 1px solid var(--line); border-radius: var(--radius-panel); background: var(--paper); box-shadow: var(--shadow-panel); }
.password-overline { margin: 0 0 8px; color: var(--accent); font-family: var(--font-utility); font-weight: 700; letter-spacing: .08em; }
.password-card h1 { margin: 0; font-family: var(--font-display); font-size: 30px; }
.password-hint { margin: 12px 0 24px; color: var(--muted); line-height: 1.7; }
.password-card .el-alert { margin-bottom: 18px; }
</style>
