<template>
  <main class="registration-page">
    <section class="registration-route" aria-labelledby="registration-title">
      <router-link class="registration-brand" to="/login" aria-label="返回登录">
        <span aria-hidden="true">格</span>
        <strong>社区治理工作簿</strong>
      </router-link>
      <p class="route-overline">ACCOUNT ENTRY · 身份入口</p>
      <h1 id="registration-title">先确认你是谁，再把正确的工作与服务交给你。</h1>
      <p class="route-copy">公开注册不会直接获得后台权限。每个申请都保留身份类型、核验对象和审核结果。</p>

      <ol class="registration-steps" aria-label="注册审核路径">
        <li :class="{ active: step >= 1 }"><b>01</b><span><strong>提交身份</strong><small>选择工作人员申请或居民注册</small></span></li>
        <li :class="{ active: step >= 2 }"><b>02</b><span><strong>社区核验</strong><small>管理员确认角色或居民档案</small></span></li>
        <li :class="{ active: step >= 3 }"><b>03</b><span><strong>进入对应入口</strong><small>后台工作台或居民服务台</small></span></li>
      </ol>
    </section>

    <section class="registration-panel" aria-labelledby="form-title">
      <div class="registration-card">
        <template v-if="!result">
          <p class="card-overline">选择注册身份</p>
          <h2 id="form-title">创建平台账号</h2>
          <el-tabs v-model="form.accountType" stretch @tab-click="resetConditionalFields">
            <el-tab-pane label="工作人员申请" name="STAFF" />
            <el-tab-pane label="居民注册" name="RESIDENT" />
          </el-tabs>

          <el-alert
            class="registration-note"
            :title="identityHint"
            type="info"
            show-icon
            :closable="false"
          />
          <el-alert v-if="error" class="registration-note" :title="error" type="error" show-icon :closable="false" />

          <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.native.prevent="submit">
            <div class="form-grid">
              <el-form-item label="用户名" prop="username">
                <el-input v-model.trim="form.username" maxlength="64" autocomplete="username" placeholder="3-64 位字母、数字或 _ . -" />
              </el-form-item>
              <el-form-item label="真实姓名" prop="realName">
                <el-input v-model.trim="form.realName" maxlength="80" autocomplete="name" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model.trim="form.phone" maxlength="20" autocomplete="tel" placeholder="用于身份核验和联系" />
              </el-form-item>
              <el-form-item v-if="form.accountType === 'RESIDENT'" label="身份证号" prop="idCardNumber">
                <el-input v-model.trim="form.idCardNumber" maxlength="18" autocomplete="off" show-password placeholder="仅用于匹配既有居民档案" />
              </el-form-item>
              <el-form-item label="登录密码" prop="password">
                <el-input v-model="form.password" type="password" maxlength="128" autocomplete="new-password" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="form.confirmPassword" type="password" maxlength="128" autocomplete="new-password" show-password @keyup.enter.native="submit" />
              </el-form-item>
            </div>
            <el-form-item :label="form.accountType === 'STAFF' ? '申请说明（选填）' : '补充说明（选填）'" prop="note">
              <el-input v-model.trim="form.note" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="form.accountType === 'STAFF' ? '例如所属社区、拟承担的工作职责' : '身份信息无法匹配时，可填写联系说明'" />
            </el-form-item>
            <el-button class="registration-submit" type="primary" native-type="submit" :loading="submitting">提交注册申请</el-button>
          </el-form>
          <p class="back-login">已有账号？<router-link to="/login">返回登录</router-link></p>
        </template>

        <div v-else class="registration-success" role="status">
          <span class="success-mark"><i class="el-icon-check" /></span>
          <p class="card-overline">申请已进入审核队列</p>
          <h2>注册信息已安全提交</h2>
          <p>{{ result.message }}</p>
          <dl>
            <div><dt>账号</dt><dd>{{ result.username }}</dd></div>
            <div><dt>身份</dt><dd>{{ result.accountType === 'RESIDENT' ? '居民用户' : '工作人员' }}</dd></div>
            <div><dt>状态</dt><dd>等待审核</dd></div>
          </dl>
          <el-button type="primary" @click="$router.push('/login')">返回登录页</el-button>
        </div>
      </div>
    </section>
  </main>
</template>

<script>
import { initializeCsrf, register } from '../../api/auth'
import { errorMessage } from '../../utils/data'

export default {
  name: 'RegistrationView',
  data() {
    const confirmPassword = (rule, value, callback) => {
      if (value !== this.form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    }
    return {
      step: 1,
      submitting: false,
      error: '',
      result: null,
      form: {
        accountType: this.$route.query.type === 'RESIDENT' ? 'RESIDENT' : 'STAFF',
        username: '', realName: '', phone: '', idCardNumber: '', password: '', confirmPassword: '', note: ''
      },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          { pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '用户名需为 3-64 位字母、数字或 _ . -', trigger: 'blur' }
        ],
        realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^\+?[0-9-]{7,20}$/, message: '手机号格式不正确', trigger: 'blur' }
        ],
        idCardNumber: [{
          validator: (rule, value, callback) => {
            if (this.form.accountType !== 'RESIDENT') return callback()
            if (!/^([0-9]{15}|[0-9]{17}[0-9Xx])$/.test(value || '')) return callback(new Error('请输入正确的身份证号'))
            callback()
          },
          trigger: 'blur'
        }],
        password: [
          { required: true, message: '请输入登录密码', trigger: 'blur' },
          { min: 8, max: 128, message: '密码长度需为 8-128 位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请再次输入密码', trigger: 'blur' },
          { validator: confirmPassword, trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    identityHint() {
      return this.form.accountType === 'RESIDENT'
        ? '姓名、手机号和身份证号必须与社区已有居民档案一致；系统只保存不可逆匹配结果。'
        : '申请提交后，管理员会核验身份并分配社区工作人员或网格员角色。'
    }
  },
  created() {
    initializeCsrf().catch(() => null)
  },
  methods: {
    resetConditionalFields() {
      this.error = ''
      if (this.form.accountType === 'STAFF') this.form.idCardNumber = ''
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    submit() {
      if (this.submitting) return
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.submitting = true
        this.error = ''
        try {
          await initializeCsrf()
          this.result = await register({
            accountType: this.form.accountType,
            username: this.form.username,
            password: this.form.password,
            realName: this.form.realName,
            phone: this.form.phone,
            idCardNumber: this.form.accountType === 'RESIDENT' ? this.form.idCardNumber.toUpperCase() : null,
            note: this.form.note || null
          })
          this.step = 2
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
.registration-page { min-height: 100vh; display: grid; grid-template-columns: minmax(360px, 0.92fr) minmax(520px, 1.08fr); background: var(--surface); }
.registration-route { position: relative; display: flex; flex-direction: column; justify-content: center; padding: 64px clamp(42px, 6vw, 88px); color: #eef6f2; overflow: hidden; background: linear-gradient(145deg, var(--sidebar), var(--sidebar-deep)); }
.registration-route::after { content: ""; position: absolute; right: -18%; bottom: -16%; width: 420px; height: 420px; border: 1px solid rgba(226, 240, 233, .16); border-radius: 50%; box-shadow: 0 0 0 56px rgba(226, 240, 233, .035), 0 0 0 112px rgba(226, 240, 233, .025); }
.registration-brand { position: absolute; top: 30px; left: clamp(42px, 6vw, 88px); display: flex; align-items: center; gap: 12px; color: #fff; }
.registration-brand span { display: grid; width: 38px; height: 38px; place-items: center; color: var(--sidebar-deep); background: #dcebe4; border-radius: 4px; font-family: var(--font-display); font-size: 21px; font-weight: 700; }
.route-overline, .card-overline { margin: 0 0 16px; font-family: var(--font-utility); font-size: 12px; letter-spacing: .12em; text-transform: uppercase; }
.route-overline { color: #b9d3c9; }
.registration-route h1 { max-width: 620px; margin: 0; font-family: var(--font-display); font-size: clamp(34px, 4vw, 58px); font-weight: 700; line-height: 1.2; }
.route-copy { max-width: 580px; margin: 26px 0 40px; color: #bed1cb; font-size: 16px; line-height: 1.8; }
.registration-steps { position: relative; z-index: 1; display: grid; gap: 0; max-width: 560px; margin: 0; padding: 0; list-style: none; }
.registration-steps li { display: grid; grid-template-columns: 52px 1fr; gap: 16px; padding: 18px 0; border-top: 1px solid rgba(255,255,255,.13); opacity: .58; }
.registration-steps li.active { opacity: 1; }
.registration-steps b { color: #92b9ab; font-family: var(--font-utility); font-size: 13px; }
.registration-steps strong, .registration-steps small { display: block; }
.registration-steps strong { margin-bottom: 5px; font-size: 15px; }
.registration-steps small { color: #b9cbc5; font-size: 13px; }
.registration-panel { display: grid; align-items: center; padding: 48px clamp(34px, 7vw, 100px); background: radial-gradient(circle at 88% 8%, var(--accent-soft), transparent 28%), var(--paper); }
.registration-card { width: 100%; max-width: 720px; margin: 0 auto; padding: clamp(28px, 4vw, 48px); background: var(--surface); border: 1px solid var(--line); border-top: 3px solid var(--accent); border-radius: var(--radius-surface); box-shadow: var(--shadow-soft); }
.card-overline { color: var(--accent); }
.registration-card h2 { margin: 0 0 24px; font-family: var(--font-display); font-size: 32px; }
.registration-note { margin: 16px 0 22px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.registration-submit { width: 100%; margin-top: 4px; }
.back-login { margin: 20px 0 0; color: var(--muted); text-align: center; }
.back-login a { margin-left: 6px; font-weight: 700; }
.registration-success { text-align: center; }
.success-mark { display: grid; width: 72px; height: 72px; margin: 0 auto 24px; place-items: center; color: #fff; background: var(--accent); border-radius: 50%; font-size: 32px; }
.registration-success > p:not(.card-overline) { color: var(--muted); line-height: 1.7; }
.registration-success dl { margin: 30px 0; border-top: 1px solid var(--line); }
.registration-success dl div { display: flex; justify-content: space-between; padding: 14px 0; border-bottom: 1px solid var(--line); }
.registration-success dt { color: var(--muted); }
.registration-success dd { margin: 0; font-weight: 700; }
@media (max-width: 920px) { .registration-page { grid-template-columns: 1fr; } .registration-route { min-height: auto; padding: 112px 28px 38px; } .registration-brand { left: 28px; } .registration-route h1 { font-size: 36px; } .registration-steps { display: none; } .registration-panel { padding: 24px 16px 40px; } }
@media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; } .registration-card { padding: 26px 20px; } }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; transition: none !important; } }
</style>
