#!/usr/bin/env node

import assert from 'node:assert/strict'
import { randomBytes, randomUUID } from 'node:crypto'

const REQUIRED_NODE_MAJOR = 22
const nodeMajor = Number.parseInt(process.versions.node.split('.')[0], 10)
if (nodeMajor !== REQUIRED_NODE_MAJOR) {
  throw new Error(`runtime-smoke.mjs requires Node ${REQUIRED_NODE_MAJOR}.x, got ${process.versions.node}`)
}

function requiredEnv(name, { secret = false } = {}) {
  const value = process.env[name]
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return secret ? value : value.trim()
}

function validatePassword(name, value) {
  if (value.length < 12 || value.length > 128) {
    throw new Error(`${name} must contain 12-128 characters`)
  }
  if (!/[a-z]/.test(value) || !/[A-Z]/.test(value) || !/\d/.test(value) || !/[^A-Za-z0-9]/.test(value)) {
    throw new Error(`${name} must contain upper-case, lower-case, numeric and special characters`)
  }
}

function normalizeApiBase(rawBaseUrl) {
  let parsed
  try {
    parsed = new URL(rawBaseUrl)
  } catch {
    throw new Error('SMOKE_BASE_URL must be an absolute HTTP(S) URL')
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('SMOKE_BASE_URL must use HTTP or HTTPS')
  }
  parsed.search = ''
  parsed.hash = ''
  parsed.pathname = parsed.pathname.replace(/\/+$/, '')
  if (!parsed.pathname.endsWith('/api')) {
    parsed.pathname = `${parsed.pathname}/api`.replace(/\/{2,}/g, '/')
  }
  return parsed.toString().replace(/\/$/, '')
}

function assertIsolatedHttpTarget(apiBase) {
  const parsed = new URL(apiBase)
  const loopbackHosts = new Set(['127.0.0.1', 'localhost', '::1', '[::1]'])
  if (loopbackHosts.has(parsed.hostname)) return

  const approvedHost = (process.env.SMOKE_ALLOWED_REMOTE_HOST || '').trim().toLowerCase()
    .replace(/^\[|\]$/g, '')
  const targetHost = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, '')
  if (process.env.SMOKE_ALLOW_REMOTE_TARGET !== 'YES' ||
      process.env.SMOKE_CONFIRM_REMOTE_DISPOSABLE !== 'YES' ||
      approvedHost !== targetHost) {
    throw new Error(
      'Remote SMOKE_BASE_URL requires SMOKE_ALLOWED_REMOTE_HOST to exactly match the target, ' +
      'plus SMOKE_ALLOW_REMOTE_TARGET=YES and SMOKE_CONFIRM_REMOTE_DISPOSABLE=YES before any request'
    )
  }
}

function makeUniqueUsername(prefix, tag, runKey) {
  if (!/^[A-Za-z0-9_.-]{3,64}$/.test(prefix)) {
    throw new Error('SMOKE_WORKER_USERNAME must be a 3-64 character username prefix using letters, digits, dot, underscore or hyphen')
  }
  const suffix = `-${tag}-${runKey}`
  const head = prefix.slice(0, 64 - suffix.length)
  if (head.length < 3) {
    throw new Error('SMOKE_WORKER_USERNAME leaves insufficient room for a unique run suffix')
  }
  return `${head}${suffix}`
}

function query(path, values) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const encoded = params.toString()
  return encoded ? `${path}?${encoded}` : path
}

function splitCombinedSetCookie(value) {
  if (!value) return []
  return value.split(/,(?=\s*[^;,=\s]+=[^;,]*)/g)
}

class CookieJar {
  constructor() {
    this.cookies = new Map()
  }

  absorb(headers) {
    const setCookies = typeof headers.getSetCookie === 'function'
      ? headers.getSetCookie()
      : splitCombinedSetCookie(headers.get('set-cookie'))

    for (const setCookie of setCookies) {
      const [pair, ...attributes] = setCookie.split(';')
      const separator = pair.indexOf('=')
      if (separator <= 0) continue
      const name = pair.slice(0, separator).trim()
      const value = pair.slice(separator + 1).trim()
      const deleted = value === '' || attributes.some(attribute => /^\s*max-age\s*=\s*0\s*$/i.test(attribute))
      if (deleted) {
        this.cookies.delete(name)
      } else {
        this.cookies.set(name, value)
      }
    }
  }

  headerValue() {
    return [...this.cookies.entries()]
      .map(([name, value]) => `${name}=${value}`)
      .join('; ')
  }

  value(name) {
    return this.cookies.get(name)
  }
}

class ApiClient {
  constructor(apiBase, label) {
    this.apiBase = apiBase
    this.label = label
    this.cookieJar = new CookieJar()
    this.csrfToken = null
    this.csrfHeaderName = 'X-XSRF-TOKEN'
  }

  async refreshCsrf() {
    const data = await this.request('/auth/csrf')
    assert.equal(typeof data.token, 'string', `${this.label}: CSRF token must be a string`)
    assert.ok(data.token.length > 0, `${this.label}: CSRF token must not be empty`)
    assert.equal(typeof data.headerName, 'string', `${this.label}: CSRF headerName must be a string`)
    const cookieToken = this.cookieJar.value('XSRF-TOKEN')
    assert.equal(typeof cookieToken, 'string', `${this.label}: XSRF-TOKEN cookie must be present`)
    // Spring Security's SPA handler expects the plain cookie token in the header;
    // the token exposed as a request attribute may be BREACH-masked.
    this.csrfToken = decodeURIComponent(cookieToken)
    this.csrfHeaderName = data.headerName
    return data
  }

  async login(username, password) {
    await this.refreshCsrf()
    const user = await this.request('/auth/login', {
      method: 'POST',
      body: { username, password }
    })
    // Authentication rotates the session ID and invalidates the pre-login CSRF token.
    await this.refreshCsrf()
    return user
  }

  async request(path, { method = 'GET', body, withMeta = false } = {}) {
    const normalizedMethod = method.toUpperCase()
    const headers = { Accept: 'application/json' }
    const cookie = this.cookieJar.headerValue()
    if (cookie) headers.Cookie = cookie
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (!['GET', 'HEAD', 'OPTIONS'].includes(normalizedMethod)) {
      if (!this.csrfToken) {
        throw new Error(`${this.label}: refusing ${normalizedMethod} without a CSRF token`)
      }
      headers[this.csrfHeaderName] = this.csrfToken
    }

    let response
    try {
      response = await fetch(`${this.apiBase}${path}`, {
        method: normalizedMethod,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: 'manual',
        signal: AbortSignal.timeout(20_000)
      })
    } catch (error) {
      throw new Error(`${this.label}: ${normalizedMethod} ${path} failed to connect (${error.message})`)
    }

    this.cookieJar.absorb(response.headers)
    const raw = await response.text()
    let envelope
    try {
      envelope = raw ? JSON.parse(raw) : null
    } catch {
      throw new Error(`${this.label}: ${normalizedMethod} ${path} returned non-JSON HTTP ${response.status}`)
    }

    if (response.status !== 200) {
      const code = envelope && typeof envelope.code === 'string' ? envelope.code : 'UNKNOWN'
      const message = envelope && typeof envelope.message === 'string' ? envelope.message : 'no message'
      throw new Error(`${this.label}: ${normalizedMethod} ${path} returned HTTP ${response.status}, code=${code}, message=${message}`)
    }
    if (!envelope || envelope.code !== 'OK') {
      const code = envelope && typeof envelope.code === 'string' ? envelope.code : 'INVALID_RESPONSE'
      throw new Error(`${this.label}: ${normalizedMethod} ${path} returned unexpected business code ${code}`)
    }
    assert.ok(Object.hasOwn(envelope, 'data'), `${this.label}: response envelope must contain data`)
    return withMeta
      ? {
          data: envelope.data,
          cacheControl: response.headers.get('cache-control') || ''
        }
      : envelope.data
  }

  async uploadFile(path, { bytes, filename, contentType, requestToken = randomUUID() }) {
    if (!this.csrfToken) {
      throw new Error(`${this.label}: refusing file upload without a CSRF token`)
    }
    const form = new FormData()
    form.append('file', new Blob([bytes], { type: contentType }), filename)
    form.append('requestToken', requestToken)
    const headers = {
      Accept: 'application/json',
      [this.csrfHeaderName]: this.csrfToken
    }
    const cookie = this.cookieJar.headerValue()
    if (cookie) headers.Cookie = cookie

    const response = await fetch(`${this.apiBase}${path}`, {
      method: 'POST',
      headers,
      body: form,
      redirect: 'manual',
      signal: AbortSignal.timeout(20_000)
    })
    this.cookieJar.absorb(response.headers)
    const raw = await response.text()
    let envelope
    try {
      envelope = raw ? JSON.parse(raw) : null
    } catch {
      throw new Error(`${this.label}: POST ${path} returned non-JSON HTTP ${response.status}`)
    }
    if (response.status !== 200 || !envelope || envelope.code !== 'OK') {
      const code = envelope && envelope.code ? envelope.code : 'UNKNOWN'
      const message = envelope && envelope.message ? envelope.message : 'no message'
      throw new Error(`${this.label}: POST ${path} returned HTTP ${response.status}, code=${code}, message=${message}`)
    }
    return envelope.data
  }

  async downloadFile(path) {
    const headers = { Accept: '*/*' }
    const cookie = this.cookieJar.headerValue()
    if (cookie) headers.Cookie = cookie
    const response = await fetch(`${this.apiBase}${path}`, {
      headers,
      redirect: 'manual',
      signal: AbortSignal.timeout(20_000)
    })
    this.cookieJar.absorb(response.headers)
    if (response.status !== 200) {
      const raw = await response.text()
      let envelope = null
      try {
        envelope = raw ? JSON.parse(raw) : null
      } catch {
        // The status and path remain sufficient for the failure message.
      }
      const code = envelope && envelope.code ? envelope.code : 'UNKNOWN'
      throw new Error(`${this.label}: GET ${path} returned HTTP ${response.status}, code=${code}`)
    }
    return {
      bytes: new Uint8Array(await response.arrayBuffer()),
      contentType: response.headers.get('content-type'),
      contentDisposition: response.headers.get('content-disposition'),
      cacheControl: response.headers.get('cache-control') || ''
    }
  }
}

function assertStatus(entity, expected, label) {
  assert.equal(entity.status, expected, `${label}: expected status ${expected}, got ${entity.status}`)
  assert.equal(typeof entity.id, 'string', `${label}: id must be a string`)
  assert.ok(entity.id.length > 0, `${label}: id must not be empty`)
  assert.ok(Number.isInteger(entity.version), `${label}: version must be an integer`)
}

function assertPageContains(page, predicate, label) {
  assert.ok(page && Array.isArray(page.items), `${label}: expected a page response`)
  assert.ok(page.items.some(predicate), `${label}: expected item was not present`)
}

function assertActions(flows, expectedActions, label) {
  assert.ok(Array.isArray(flows), `${label}: flows must be an array`)
  const actions = new Set(flows.map(flow => flow.action))
  for (const action of expectedActions) {
    assert.ok(actions.has(action), `${label}: missing ${action} flow`)
  }
}

function assertFlowSequence(flows, expectedActions, label) {
  assert.ok(Array.isArray(flows), `${label}: flows must be an array`)
  assert.deepEqual(
    flows.map(flow => flow.action),
    expectedActions,
    `${label}: flow action order mismatch`
  )
}

function assertNavigation(items, label) {
  assert.ok(Array.isArray(items), `${label}: navigation must be an array`)
  for (const item of items) {
    for (const field of ['id', 'code', 'name', 'routePath', 'icon', 'sortNo']) {
      assert.ok(Object.hasOwn(item, field), `${label}: navigation item is missing ${field}`)
    }
    assert.ok(typeof item.id === 'string' && item.id.length > 0, `${label}: navigation id is invalid`)
    assert.ok(typeof item.code === 'string' && item.code.length > 0, `${label}: navigation code is invalid`)
    assert.ok(typeof item.name === 'string' && item.name.length > 0, `${label}: navigation name is invalid`)
    assert.ok(typeof item.routePath === 'string' && item.routePath.startsWith('/'), `${label}: navigation routePath is invalid`)
    assert.ok(Number.isInteger(item.sortNo), `${label}: navigation sortNo is invalid`)
  }
  assert.deepEqual(
    items.map(item => item.sortNo),
    [...items.map(item => item.sortNo)].sort((left, right) => left - right),
    `${label}: navigation must be ordered by sortNo`
  )
}

function assertDashboardOverview(overview, label) {
  assert.ok(overview && typeof overview === 'object', `${label}: dashboard overview must be an object`)
  for (const field of ['gridEventStats', 'categoryStats', 'recentEvents']) {
    assert.ok(Array.isArray(overview[field]), `${label}: ${field} must be an array`)
  }
  for (const item of overview.gridEventStats) {
    for (const field of [
      'gridId', 'gridCode', 'gridName', 'eventCount', 'completedWithDeadlineCount',
      'onTimeClosedCount', 'onTimeCompletionRate'
    ]) {
      assert.ok(Object.hasOwn(item, field), `${label}: gridEventStats is missing ${field}`)
    }
    assert.ok(Number(item.onTimeCompletionRate) >= 0 && Number(item.onTimeCompletionRate) <= 100,
      `${label}: onTimeCompletionRate must be a 0..100 percentage`)
  }
  for (const item of overview.categoryStats) {
    for (const field of ['categoryId', 'categoryName', 'eventCount', 'percentage']) {
      assert.ok(Object.hasOwn(item, field), `${label}: categoryStats is missing ${field}`)
    }
    assert.ok(Number(item.percentage) >= 0 && Number(item.percentage) <= 100,
      `${label}: category percentage must be a 0..100 percentage`)
  }
  assert.ok(overview.recentEvents.length <= 10, `${label}: recentEvents must be limited to 10 rows`)
  for (const item of overview.recentEvents) {
    for (const field of ['id', 'eventNo', 'title', 'categoryName', 'gridName', 'status', 'severity', 'reportedAt']) {
      assert.ok(Object.hasOwn(item, field), `${label}: recentEvents is missing ${field}`)
    }
  }
}

async function assertDeleteCors(apiBase, origin, path) {
  const response = await fetch(`${apiBase}${path}`, {
    method: 'OPTIONS',
    headers: {
      Origin: origin,
      'Access-Control-Request-Method': 'DELETE'
    },
    redirect: 'manual',
    signal: AbortSignal.timeout(20_000)
  })
  assert.ok(response.ok, `DELETE CORS preflight returned HTTP ${response.status}`)
  assert.match(response.headers.get('access-control-allow-methods') || '', /DELETE/i,
    'DELETE CORS preflight does not allow DELETE')
  assert.equal(response.headers.get('access-control-allow-origin'), origin,
    'DELETE CORS preflight does not echo the configured frontend origin')
}

async function pass(label, operation) {
  const result = await operation()
  console.log(`PASS ${label}`)
  return result
}

const isolatedConfirmation = requiredEnv('SMOKE_CONFIRM_ISOLATED')
if (isolatedConfirmation !== 'YES') {
  throw new Error('SMOKE_CONFIRM_ISOLATED must be exactly YES before any request')
}

const baseUrl = requiredEnv('SMOKE_BASE_URL')
const adminUsername = requiredEnv('SMOKE_ADMIN_USERNAME')
const adminPassword = requiredEnv('SMOKE_ADMIN_PASSWORD', { secret: true })
const workerUsernamePrefix = requiredEnv('SMOKE_WORKER_USERNAME')
const configuredWorkerUsername = (process.env.SMOKE_WORKER_FIXED_USERNAME || '').trim()
const workerPassword = requiredEnv('SMOKE_WORKER_PASSWORD', { secret: true })
let categoryId = (process.env.SMOKE_EVENT_CATEGORY_ID || '1').trim()
const corsOrigin = (process.env.SMOKE_CORS_ORIGIN || 'http://localhost:5173').trim()
const configuredResidentUsername = (process.env.SMOKE_RESIDENT_USERNAME || '').trim()
const configuredResidentPassword = process.env.SMOKE_RESIDENT_PASSWORD || ''
const configuredCommunityUsername = (process.env.SMOKE_COMMUNITY_USERNAME || '').trim()
const configuredCommunityPassword = process.env.SMOKE_COMMUNITY_PASSWORD || ''
const configuredCommunityName = (process.env.SMOKE_COMMUNITY_NAME || '').trim()
const configuredGridName = (process.env.SMOKE_GRID_NAME || '').trim()
const configuredResidentName = (process.env.SMOKE_RESIDENT_NAME || '').trim()
const configuredUnscopedWorkerUsername = (process.env.SMOKE_UNSCOPED_WORKER_USERNAME || '').trim()
const configuredSyntheticIdCard = (process.env.SMOKE_SYNTHETIC_ID_CARD || '').trim()
const configuredSyntheticPhone = (process.env.SMOKE_SYNTHETIC_PHONE || '').trim()

validatePassword('SMOKE_ADMIN_PASSWORD', adminPassword)
validatePassword('SMOKE_WORKER_PASSWORD', workerPassword)
if (configuredResidentPassword) validatePassword('SMOKE_RESIDENT_PASSWORD', configuredResidentPassword)
if (configuredCommunityPassword) validatePassword('SMOKE_COMMUNITY_PASSWORD', configuredCommunityPassword)
if (configuredSyntheticIdCard && !/^\d{17}[\dX]$/.test(configuredSyntheticIdCard)) {
  throw new Error('SMOKE_SYNTHETIC_ID_CARD must contain 18 synthetic ID-card characters')
}
if (configuredSyntheticPhone && !/^1[3-9]\d{9}$/.test(configuredSyntheticPhone)) {
  throw new Error('SMOKE_SYNTHETIC_PHONE must contain an 11-digit synthetic mobile number')
}
if (!/^[1-9]\d*$/.test(categoryId)) {
  throw new Error('SMOKE_EVENT_CATEGORY_ID must be a positive integer string')
}
if (!/^https?:\/\//.test(corsOrigin)) {
  throw new Error('SMOKE_CORS_ORIGIN must be an absolute HTTP(S) origin')
}

const apiBase = normalizeApiBase(baseUrl)
assertIsolatedHttpTarget(apiBase)
const runKey = `${Date.now()}-${randomBytes(3).toString('hex')}`
const shortKey = runKey.slice(-12)
const dispatcherPassword = `${randomBytes(24).toString('base64url')}Aa1!`
const residentPassword = configuredResidentPassword || `${randomBytes(24).toString('base64url')}Rr1!`
const lifecyclePassword = configuredCommunityPassword || workerPassword
const residentIdCard = configuredSyntheticIdCard ||
  `${Date.now()}${String(randomBytes(4).readUInt32BE(0) % 100_000).padStart(5, '0')}`
const idCardLast4 = residentIdCard.slice(-4)
const phoneLast8 = configuredSyntheticPhone
  ? configuredSyntheticPhone.slice(-8)
  : String(randomBytes(4).readUInt32BE(0) % 100_000_000).padStart(8, '0')
const auditPurpose = `运行时审计用途-${shortKey}`

const demoProfileCode = (process.env.SMOKE_DEMO_PROFILE || '').trim()
const demoProfiles = {
  xingfu: {
    community: '幸福里社区',
    grid: '幸福里第一网格',
    address: '梧桐路88号',
    resident: '张建国',
    dispatcher: '王芳',
    worker: '李明',
    lifecycle: '赵静',
    event: '消防通道占用处置',
    rejectedEvent: '重复上报的消防隐患',
    cancelledEvent: '居民自行协调撤回',
    eventTask: '清理消防通道占用',
    independentTask: '重点区域日常巡查',
    cancelledTask: '临时夜间巡查',
    openEvent: '楼道杂物待核实',
    openTask: '核查楼道杂物',
    openStage: 'reported',
    residentGender: 'MALE',
    residentBirthDate: '1952-06-18',
    specialGroupTags: ['独居老人', '重点关怀']
  },
  heyuan: {
    community: '和苑社区',
    grid: '和苑第二网格',
    address: '桂花巷16号',
    resident: '陈秀兰',
    dispatcher: '刘洋',
    worker: '周敏',
    lifecycle: '孙悦',
    event: '破损井盖应急处置',
    rejectedEvent: '重复上报的路灯故障',
    cancelledEvent: '施工方已现场修复',
    eventTask: '设置围挡并更换井盖',
    independentTask: '公共设施专项巡查',
    cancelledTask: '临时照明检查',
    openEvent: '电梯故障等待接单',
    openTask: '排查电梯运行故障',
    openStage: 'assigned',
    residentGender: 'FEMALE',
    residentBirthDate: '1948-11-03',
    specialGroupTags: ['高龄老人']
  },
  qinghe: {
    community: '清河社区',
    grid: '清河第三网格',
    address: '清河路126号',
    resident: '马国强',
    dispatcher: '高晨',
    worker: '何军',
    lifecycle: '蒋雯',
    event: '沿街垃圾清运协调',
    rejectedEvent: '非辖区垃圾堆放反馈',
    cancelledEvent: '商户已完成门前清理',
    eventTask: '协调清运卫生死角',
    independentTask: '背街小巷卫生巡查',
    cancelledTask: '雨后保洁复查',
    openEvent: '商铺噪声现场处置',
    openTask: '协调商铺降低噪声',
    openStage: 'processing',
    residentGender: 'MALE',
    residentBirthDate: '1966-04-12',
    specialGroupTags: ['重点关怀']
  },
  donghu: {
    community: '东湖社区',
    grid: '东湖第四网格',
    address: '湖滨路39号',
    resident: '林晓梅',
    dispatcher: '谢宁',
    worker: '郑磊',
    lifecycle: '许佳',
    event: '健身设施安全隐患',
    rejectedEvent: '无效的重复隐患上报',
    cancelledEvent: '物业已提前封闭设施',
    eventTask: '维修小区健身设施',
    independentTask: '公共空间安全巡查',
    cancelledTask: '节前设施复查',
    openEvent: '雨后路面积水待复核',
    openTask: '排查并疏通积水点',
    openStage: 'pending-review',
    residentGender: 'FEMALE',
    residentBirthDate: '1973-09-25',
    specialGroupTags: ['志愿者']
  }
}
if (demoProfileCode && !demoProfiles[demoProfileCode]) {
  throw new Error(`Unknown SMOKE_DEMO_PROFILE: ${demoProfileCode}`)
}

const names = demoProfiles[demoProfileCode] || {
  community: configuredCommunityName || `验证社区-${runKey}`,
  grid: configuredGridName || `验证网格-${runKey}`,
  address: `验证路${shortKey}号`,
  resident: configuredResidentName || `验证居民-${shortKey}`,
  dispatcher: `验证派发员-${shortKey}`,
  worker: `验证网格员-${shortKey}`,
  lifecycle: `验证生命周期用户-${shortKey}`,
  event: `验证事件-${runKey}`,
  rejectedEvent: `验证驳回事件-${runKey}`,
  cancelledEvent: `验证撤销事件-${runKey}`,
  eventTask: `事件处置-${runKey}`,
  independentTask: `独立巡查-${runKey}`,
  cancelledTask: `取消巡查-${runKey}`,
  openEvent: '',
  openTask: '',
  openStage: '',
  residentGender: 'OTHER',
  residentBirthDate: '1990-01-01',
  specialGroupTags: ['SMOKE_VALIDATION']
}
const workerUsername = demoProfileCode
  ? `grid-${demoProfileCode}`
  : configuredWorkerUsername || makeUniqueUsername(workerUsernamePrefix, 'w', runKey)
const dispatcherUsername = demoProfileCode
  ? `dispatcher-${demoProfileCode}`
  : makeUniqueUsername('smoke-dispatcher', 'd', runKey)
const lifecycleUsername = demoProfileCode
  ? `community-${demoProfileCode}`
  : configuredCommunityUsername || makeUniqueUsername('smoke-lifecycle', 'u', runKey)
const residentUsername = configuredResidentUsername || makeUniqueUsername('smoke-resident', 'r', runKey)
const unscopedWorkerUsername = demoProfileCode
  ? `grid-standby-${demoProfileCode}`
  : configuredUnscopedWorkerUsername || makeUniqueUsername('smoke-outscope', 'x', runKey)
if (configuredResidentUsername && !/^[A-Za-z0-9_.-]{3,64}$/.test(configuredResidentUsername)) {
  throw new Error('SMOKE_RESIDENT_USERNAME must be a 3-64 character username using letters, digits, dot, underscore or hyphen')
}
if (configuredWorkerUsername && !/^[A-Za-z0-9_.-]{3,64}$/.test(configuredWorkerUsername)) {
  throw new Error('SMOKE_WORKER_FIXED_USERNAME must be a 3-64 character username using letters, digits, dot, underscore or hyphen')
}

async function main() {
  const admin = new ApiClient(apiBase, 'bootstrap-admin')
  const dispatcher = new ApiClient(apiBase, 'dispatcher-admin')
  const worker = new ApiClient(apiBase, 'grid-worker')
  const unscopedWorker = new ApiClient(apiBase, 'unassigned-grid-worker')
  const lifecycleClient = new ApiClient(apiBase, 'community-staff-session')

  const adminMe = await pass('引导管理员登录与会话轮换', () => admin.login(adminUsername, adminPassword))
  assert.equal(adminMe.username, adminUsername, '引导管理员用户名不匹配')
  assert.ok(adminMe.roles.includes('SYSTEM_ADMIN'), '引导管理员缺少 SYSTEM_ADMIN 角色')
  assert.ok(adminMe.permissions.includes('system:user:manage'), '引导管理员缺少用户管理权限')
  assert.ok(adminMe.permissions.includes('task:review'), '引导管理员缺少任务复核权限')

  const adminNavigation = await pass('系统管理员动态导航', () => admin.request('/auth/navigation'))
  assertNavigation(adminNavigation, '系统管理员动态导航')
  assert.ok(adminNavigation.some(item => item.code === 'DASHBOARD'), '系统管理员动态导航缺少 DASHBOARD')
  assert.ok(adminNavigation.some(item => item.code === 'EVENT_CATEGORY'), '系统管理员动态导航缺少事件类别菜单')

  const roles = await pass('系统角色目录', () => admin.request('/system/roles'))
  assert.ok(Array.isArray(roles), '角色目录必须为数组')
  const roleCodes = new Set(roles.map(role => role.code))
  assert.ok(roleCodes.has('SYSTEM_ADMIN'), '角色目录缺少 SYSTEM_ADMIN')
  assert.ok(roleCodes.has('GRID_WORKER'), '角色目录缺少 GRID_WORKER')
  const systemAdminRole = roles.find(role => role.code === 'SYSTEM_ADMIN')
  assert.ok(systemAdminRole && Array.isArray(systemAdminRole.menuIds), '系统管理员角色缺少权限配置')
  assert.ok(Number.isInteger(systemAdminRole.version), '角色目录缺少乐观锁版本')

  const configuredAdminRole = await pass('固定核心角色权限配置', () => admin.request('/system/roles/SYSTEM_ADMIN', {
    method: 'PUT',
    body: {
      name: systemAdminRole.name,
      description: systemAdminRole.description || null,
      status: systemAdminRole.status,
      menuIds: systemAdminRole.menuIds,
      version: systemAdminRole.version
    }
  }))
  assert.deepEqual(configuredAdminRole.menuIds, systemAdminRole.menuIds, '角色权限配置不应漂移')

  const menus = await admin.request('/system/menus')
  const roleMenu = menus.find(menu => menu.code === 'SYSTEM_ROLE')
  assert.ok(roleMenu && Number.isInteger(roleMenu.version), '菜单目录缺少核心角色入口或版本')
  const dynamicRoleMenuName = `角色管理-${shortKey}`
  const configuredRoleMenu = await pass('固定菜单展示配置', () => admin.request(`/system/menus/${roleMenu.id}`, {
    method: 'PUT',
    body: {
      name: dynamicRoleMenuName,
      icon: roleMenu.icon || null,
      sortNo: roleMenu.sortNo,
      status: roleMenu.status,
      version: roleMenu.version
    }
  }))
  assert.equal(configuredRoleMenu.code, 'SYSTEM_ROLE', '菜单配置不得改变固定编码')
  const refreshedAdminNavigation = await pass('菜单展示配置驱动动态导航', () => admin.request('/auth/navigation'))
  assertNavigation(refreshedAdminNavigation, '菜单展示配置后的系统管理员动态导航')
  assert.equal(
    refreshedAdminNavigation.find(item => item.code === 'SYSTEM_ROLE')?.name,
    dynamicRoleMenuName,
    '动态导航未返回已更新的菜单展示名称'
  )
  const restoredRoleMenu = await pass('恢复固定菜单展示配置', () => admin.request(`/system/menus/${roleMenu.id}`, {
    method: 'PUT',
    body: {
      name: roleMenu.name,
      icon: roleMenu.icon || null,
      sortNo: roleMenu.sortNo,
      status: roleMenu.status,
      version: configuredRoleMenu.version
    }
  }))
  assert.equal(restoredRoleMenu.name, roleMenu.name, '菜单展示名称验证后必须恢复')
  const restoredAdminNavigation = await pass('恢复后的动态导航', () => admin.request('/auth/navigation'))
  assertNavigation(restoredAdminNavigation, '恢复菜单展示名称后的系统管理员动态导航')
  assert.equal(
    restoredAdminNavigation.find(item => item.code === 'SYSTEM_ROLE')?.name,
    roleMenu.name,
    '动态导航未返回已恢复的菜单展示名称'
  )

  const dispatcherUser = await pass('创建独立派发管理员', () => admin.request('/system/users', {
    method: 'POST',
    body: {
      username: dispatcherUsername,
      password: dispatcherPassword,
      realName: names.dispatcher,
      roleCodes: ['SYSTEM_ADMIN']
    }
  }))
  assertStatus(dispatcherUser, 'ENABLED', '派发管理员')
  assert.deepEqual(dispatcherUser.roles, ['SYSTEM_ADMIN'], '派发管理员角色不匹配')

  const workerUser = await pass('创建网格员用户及角色', () => admin.request('/system/users', {
    method: 'POST',
    body: {
      username: workerUsername,
      password: workerPassword,
      realName: names.worker,
      roleCodes: ['GRID_WORKER']
    }
  }))
  assertStatus(workerUser, 'ENABLED', '网格员')
  assert.deepEqual(workerUser.roles, ['GRID_WORKER'], '网格员角色不匹配')

  const unscopedWorkerUser = await pass('创建未分配网格员用于范围拒绝验证', () => admin.request('/system/users', {
    method: 'POST',
    body: {
      username: unscopedWorkerUsername,
      password: workerPassword,
      realName: demoProfileCode
        ? `${names.community.replace(/社区$/, '')}备用网格员`
        : `范围外网格员-${shortKey}`,
      roleCodes: ['GRID_WORKER']
    }
  }))
  assertStatus(unscopedWorkerUser, 'ENABLED', '未分配网格员')

  let lifecycleUser = await pass('创建用户生命周期样本', () => admin.request('/system/users', {
    method: 'POST',
    body: {
      username: lifecycleUsername,
      password: lifecyclePassword,
      realName: names.lifecycle,
      roleCodes: ['COMMUNITY_STAFF']
    }
  }))
  assertStatus(lifecycleUser, 'ENABLED', '用户生命周期样本')

  lifecycleUser = await pass('编辑用户资料', () => admin.request(`/system/users/${lifecycleUser.id}`, {
    method: 'PUT',
    body: {
      realName: `${names.lifecycle}（社区工作人员）`,
      phone: `137${phoneLast8}`,
      version: lifecycleUser.version
    }
  }))
  assertStatus(lifecycleUser, 'ENABLED', '编辑后用户')
  assert.equal(lifecycleUser.realName, `${names.lifecycle}（社区工作人员）`, '用户资料更新未生效')

  await pass('社区工作人员登录以验证旧会话失效', () =>
    lifecycleClient.login(lifecycleUsername, lifecyclePassword))

  lifecycleUser = await pass('替换并重新激活用户角色', async () => {
    const gridWorkerRole = await admin.request(`/system/users/${lifecycleUser.id}/roles`, {
      method: 'PUT',
      body: { roleCodes: ['GRID_WORKER'], version: lifecycleUser.version }
    })
    assert.deepEqual(gridWorkerRole.roles, ['GRID_WORKER'], '用户角色替换为 GRID_WORKER 失败')
    await assert.rejects(
      () => lifecycleClient.request('/auth/me'),
      /HTTP 401/,
      '角色变化后旧 Session 必须立即失效'
    )
    const communityRole = await admin.request(`/system/users/${lifecycleUser.id}/roles`, {
      method: 'PUT',
      body: { roleCodes: ['COMMUNITY_STAFF'], version: gridWorkerRole.version }
    })
    assert.deepEqual(communityRole.roles, ['COMMUNITY_STAFF'], '用户角色重新激活 COMMUNITY_STAFF 失败')
    return communityRole
  })

  lifecycleUser = await pass('停用并重新启用用户', async () => {
    const disabled = await admin.request(`/system/users/${lifecycleUser.id}/status`, {
      method: 'PATCH',
      body: { enabled: false, version: lifecycleUser.version }
    })
    assertStatus(disabled, 'DISABLED', '停用用户')
    const enabled = await admin.request(`/system/users/${lifecycleUser.id}/status`, {
      method: 'PATCH',
      body: { enabled: true, version: disabled.version }
    })
    assertStatus(enabled, 'ENABLED', '重新启用用户')
    return enabled
  })

  const userPage = await admin.request(query('/system/users', {
    keyword: workerUsername,
    page: 1,
    size: 20
  }))
  assertPageContains(userPage, item => item.id === workerUser.id, '用户列表搜索')

  let community = await pass('创建社区', () => admin.request('/grids', {
    method: 'POST',
    body: {
      areaType: 'COMMUNITY',
      areaName: names.community,
      address: names.address
    }
  }))
  assertStatus(community, 'ENABLED', '社区')
  assert.equal(community.areaType, 'COMMUNITY', '社区 areaType 不匹配')
  assert.equal(community.communityId, null, '社区不应有上级社区')

  community = await pass('编辑并验证社区启停', async () => {
    const updated = await admin.request(`/grids/${community.id}`, {
      method: 'PUT',
      body: {
        areaName: names.community,
        address: `${names.address}社区服务中心`,
        centerLongitude: 120.1234567,
        centerLatitude: 30.1234567,
        boundaryGeojson: '{"type":"Polygon","coordinates":[]}',
        version: community.version
      }
    })
    assert.equal(updated.address, `${names.address}社区服务中心`, '社区资料更新未生效')
    const disabled = await admin.request(`/grids/${community.id}/status`, {
      method: 'PATCH',
      body: { status: 'DISABLED', version: updated.version }
    })
    assertStatus(disabled, 'DISABLED', '停用社区')
    const enabled = await admin.request(`/grids/${community.id}/status`, {
      method: 'PATCH',
      body: { status: 'ENABLED', version: disabled.version }
    })
    assertStatus(enabled, 'ENABLED', '重新启用社区')
    return enabled
  })

  const communityPage = await admin.request(query('/grids', {
    areaType: 'COMMUNITY',
    keyword: names.community,
    status: 'ENABLED',
    page: 1,
    size: 20
  }))
  assertPageContains(communityPage, item => item.id === community.id && item.areaType === 'COMMUNITY', '社区列表类型筛选')

  community = await pass('分配社区工作人员主负责人', () => admin.request(`/grids/${community.id}/assignments`, {
    method: 'PUT',
    body: {
      version: community.version,
      assignments: [{ userId: lifecycleUser.id, isPrimary: true }]
    }
  }))
  assert.equal(community.assignments.length, 1, '社区应只有一个验证责任人')
  assert.equal(community.assignments[0].userId, lifecycleUser.id, '社区责任人不匹配')
  assert.equal(community.assignments[0].primary, true, '社区责任人应为主负责人')

  let grid = await pass('创建网格', () => admin.request('/grids', {
    method: 'POST',
    body: {
      areaType: 'GRID',
      communityId: community.id,
      areaName: names.grid,
      address: names.address
    }
  }))
  assertStatus(grid, 'ENABLED', '网格')
  assert.equal(grid.areaType, 'GRID', '网格 areaType 不匹配')
  assert.equal(grid.communityId, community.id, '网格所属社区不匹配')

  grid = await pass('编辑并验证网格启停', async () => {
    const updated = await admin.request(`/grids/${grid.id}`, {
      method: 'PUT',
      body: {
        areaName: names.grid,
        address: `${names.address}网格工作站`,
        centerLongitude: 120.2234567,
        centerLatitude: 30.2234567,
        boundaryGeojson: null,
        version: grid.version
      }
    })
    assert.equal(updated.address, `${names.address}网格工作站`, '网格资料更新未生效')
    const disabled = await admin.request(`/grids/${grid.id}/status`, {
      method: 'PATCH',
      body: { status: 'DISABLED', version: updated.version }
    })
    assertStatus(disabled, 'DISABLED', '停用网格')
    const enabled = await admin.request(`/grids/${grid.id}/status`, {
      method: 'PATCH',
      body: { status: 'ENABLED', version: disabled.version }
    })
    assertStatus(enabled, 'ENABLED', '重新启用网格')
    return enabled
  })

  const assignedGrid = await pass('分配网格主负责人', () => admin.request(`/grids/${grid.id}/assignments`, {
    method: 'PUT',
    body: {
      version: grid.version,
      assignments: [{ userId: workerUser.id, isPrimary: true }]
    }
  }))
  assertStatus(assignedGrid, 'ENABLED', '分配后网格')
  assert.equal(assignedGrid.assignments.length, 1, '网格应只有一个验证责任人')
  assert.equal(assignedGrid.assignments[0].userId, workerUser.id, '网格责任人不匹配')
  assert.equal(assignedGrid.assignments[0].primary, true, '网格责任人应为主负责人')

  const gridPage = await admin.request(query('/grids', {
    areaType: 'GRID',
    keyword: names.grid,
    status: 'ENABLED',
    page: 1,
    size: 20
  }))
  assertPageContains(gridPage, item => item.id === grid.id && item.communityId === community.id, '网格列表类型筛选')

  let managedCategory = await pass('创建动态事件类别并默认启用', () => admin.request('/system/event-categories', {
    method: 'POST',
    body: {
      code: `SMOKE_${randomBytes(5).toString('hex').toUpperCase()}`,
      name: `运行时类别-${shortKey}`,
      description: '用于类别管理与事件闭环验证',
      sortNo: 90
    }
  }))
  assert.equal(managedCategory.status, 'ENABLED', '省略 status 的事件类别必须默认启用')
  assert.ok(Number.isInteger(managedCategory.version), '事件类别必须返回乐观锁版本')
  const staleCategoryVersion = managedCategory.version
  managedCategory = await pass('更新动态事件类别', () => admin.request(`/system/event-categories/${managedCategory.id}`, {
    method: 'PUT',
    body: {
      name: `${managedCategory.name}-已更新`,
      description: '用于类别管理、乐观锁和在用保护验证',
      sortNo: 91,
      status: 'ENABLED',
      version: managedCategory.version
    }
  }))
  await assert.rejects(
    () => admin.request(`/system/event-categories/${managedCategory.id}`, {
      method: 'PUT',
      body: {
        name: '陈旧版本写入', description: null, sortNo: 91,
        status: 'ENABLED', version: staleCategoryVersion
      }
    }),
    /HTTP 409/,
    '事件类别陈旧 version 必须被拒绝'
  )
  categoryId = managedCategory.id
  const enabledCategories = await admin.request('/events/categories')
  assert.ok(enabledCategories.some(item => item.id === categoryId && item.name === managedCategory.name),
    '事件上报类别选项未读取动态启用类别')

  const dueAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 19)
  const dashboardAcceptedEvent = await pass('创建看板已受理聚合样本', async () => {
    const reported = await admin.request('/events', {
      method: 'POST',
      body: {
        categoryId, gridId: grid.id, title: `看板已受理-${shortKey}`,
        description: '用于 D2 聚合口径验证', reportChannel: 'WEB', severity: 'LOW',
        address: names.address, reporterName: `看板样本-${shortKey}`
      }
    })
    return admin.request(`/events/${reported.id}/accept`, {
      method: 'POST', body: { version: reported.version, remark: '看板聚合样本受理' }
    })
  })
  assertStatus(dashboardAcceptedEvent, 'ACCEPTED', '看板已受理聚合样本')
  const dashboardAssignedEvent = await pass('创建看板已派发聚合样本', async () => {
    const reported = await admin.request('/events', {
      method: 'POST',
      body: {
        categoryId, gridId: grid.id, title: `看板已派发-${shortKey}`,
        description: '用于 D2 聚合口径验证', reportChannel: 'WEB', severity: 'LOW',
        address: names.address, reporterName: `看板样本-${shortKey}`
      }
    })
    const accepted = await admin.request(`/events/${reported.id}/accept`, {
      method: 'POST', body: { version: reported.version, remark: '看板聚合样本受理' }
    })
    return admin.request(`/events/${reported.id}/assign`, {
      method: 'POST',
      body: {
        version: accepted.version, assigneeUserId: workerUser.id,
        taskTitle: `看板派发任务-${shortKey}`, taskDescription: '用于看板聚合口径验证',
        priority: 'LOW', dueAt, remark: '看板聚合样本派发'
      }
    })
  })
  assertStatus(dashboardAssignedEvent, 'ASSIGNED', '看板已派发聚合样本')
  const dashboardD2Overview = await pass('看板 D2 受理派发处理中聚合', () => admin.request('/dashboard/overview'))
  assertDashboardOverview(dashboardD2Overview, '系统管理员看板')
  const [acceptedTotal, assignedTotal, processingTotal] = await Promise.all(
    ['ACCEPTED', 'ASSIGNED', 'PROCESSING'].map(status => admin.request(query('/events', { status, page: 1, size: 1 })))
  )
  assert.equal(
    dashboardD2Overview.processingEventCount,
    acceptedTotal.total + assignedTotal.total + processingTotal.total,
    'D2 处理中事件必须聚合 ACCEPTED、ASSIGNED 和 PROCESSING'
  )

  let household = await pass('创建家庭户', () => admin.request('/households', {
    method: 'POST',
    body: {
      gridId: grid.id,
      buildingNo: `B-${shortKey}`,
      unitNo: '1',
      roomNo: '101',
      address: names.address
    }
  }))
  assertStatus(household, 'ACTIVE', '家庭户')
  assert.equal(household.gridId, grid.id, '家庭户网格不匹配')

  household = await pass('编辑并验证家庭户状态', async () => {
    const updated = await admin.request(`/households/${household.id}`, {
      method: 'PUT',
      body: {
        buildingNo: `B-${shortKey}`,
        unitNo: '2',
        roomNo: '202',
        address: `${names.address}202室`,
        version: household.version
      }
    })
    assert.equal(updated.roomNo, '202', '家庭户资料更新未生效')
    const archived = await admin.request(`/households/${household.id}/status`, {
      method: 'PATCH',
      body: { status: 'ARCHIVED', version: updated.version }
    })
    assertStatus(archived, 'ARCHIVED', '归档家庭户')
    const active = await admin.request(`/households/${household.id}/status`, {
      method: 'PATCH',
      body: { status: 'ACTIVE', version: archived.version }
    })
    assertStatus(active, 'ACTIVE', '恢复家庭户')
    return active
  })

  const householdPage = await admin.request(query('/households', {
    keyword: '202室',
    gridId: grid.id,
    status: 'ACTIVE',
    page: 1,
    size: 20
  }))
  assertPageContains(householdPage, item => item.id === household.id, '家庭户列表组合筛选')

  let resident = await pass('创建居民并验证敏感字段脱敏', () => admin.request('/residents', {
    method: 'POST',
    body: {
      gridId: grid.id,
      householdId: household.id,
      realName: names.resident,
      gender: 'UNKNOWN',
      birthDate: names.residentBirthDate,
      idCard: residentIdCard,
      phone: `139${phoneLast8}`,
      address: names.address,
      isHouseholder: true,
      specialGroupTags: names.specialGroupTags,
      remark: `runtime smoke ${runKey}`
    }
  }))
  assertStatus(resident, 'ACTIVE', '居民')
  assert.equal(resident.gridId, grid.id, '居民网格不匹配')
  assert.equal(resident.householdId, household.id, '居民家庭户不匹配')
  assert.equal(resident.isHouseholder, true, '居民应为户主')
  assert.ok(resident.idCardMasked.endsWith(idCardLast4), '返回的身份证脱敏值不匹配')
  assert.ok(resident.phoneMasked.endsWith(phoneLast8.slice(-4)), '返回的手机号脱敏值不匹配')

  resident = await pass('编辑居民并验证状态与密文保留', async () => {
    const updated = await admin.request(`/residents/${resident.id}`, {
      method: 'PUT',
      body: {
        householdId: household.id,
        realName: names.resident,
        gender: names.residentGender,
        birthDate: names.residentBirthDate,
        idCard: '',
        phone: '',
        address: `${names.address}202室`,
        isHouseholder: true,
        specialGroupTags: [...names.specialGroupTags, '已核验'],
        remark: `runtime smoke updated ${runKey}`,
        version: resident.version
      }
    })
    assert.equal(updated.gender, names.residentGender, '居民资料更新未生效')
    assert.ok(updated.idCardMasked.endsWith(idCardLast4), '居民更新不应清除原身份证密文')
    assert.ok(updated.phoneMasked.endsWith(phoneLast8.slice(-4)), '居民更新不应清除原手机号密文')
    const archived = await admin.request(`/residents/${resident.id}/status`, {
      method: 'PATCH',
      body: { status: 'ARCHIVED', version: updated.version }
    })
    assertStatus(archived, 'ARCHIVED', '归档居民')
    const active = await admin.request(`/residents/${resident.id}/status`, {
      method: 'PATCH',
      body: { status: 'ACTIVE', version: archived.version }
    })
    assertStatus(active, 'ACTIVE', '恢复居民')
    return active
  })

  const residentPage = await admin.request(query('/residents', {
    keyword: names.resident,
    gridId: grid.id,
    status: 'ACTIVE',
    page: 1,
    size: 20
  }))
  assertPageContains(residentPage, item => item.id === resident.id, '居民列表组合筛选')

  const sensitiveSearch = await pass('居民敏感字段精确检索', () => admin.request(
    '/residents/sensitive-search',
    {
      method: 'POST',
      withMeta: true,
      body: {
        type: 'PHONE',
        value: `139-${phoneLast8.slice(0, 4)} ${phoneLast8.slice(4)}`,
        gridId: grid.id,
        status: 'ACTIVE',
        page: 1,
        size: 20
      }
    }
  ))
  assert.match(sensitiveSearch.cacheControl, /no-store/i, '敏感检索响应必须禁止缓存')
  assertPageContains(
    sensitiveSearch.data,
    item => item.id === resident.id && item.phoneMasked.endsWith(phoneLast8.slice(-4)),
    '居民敏感字段精确检索'
  )
  assert.ok(
    !JSON.stringify(sensitiveSearch.data).includes(`139${phoneLast8}`),
    '敏感检索结果不得返回完整手机号'
  )

  const sensitiveView = await pass('授权查看居民敏感字段', () => admin.request(
    `/residents/${resident.id}/sensitive-view`,
    {
      method: 'POST',
      withMeta: true,
      body: { purpose: auditPurpose }
    }
  ))
  assert.match(sensitiveView.cacheControl, /no-store/i, '敏感查看响应必须禁止缓存')
  assert.equal(sensitiveView.data.residentId, resident.id, '敏感查看居民不匹配')
  assert.equal(sensitiveView.data.idCard, residentIdCard, '敏感查看身份证号不匹配')
  assert.equal(sensitiveView.data.phone, `139${phoneLast8}`, '敏感查看手机号不匹配')

  const sensitiveAudit = await pass('敏感访问审计分页、用途检索与脱敏', () => admin.request(
    query('/residents/sensitive-access-logs', {
      action: 'VIEW', fieldType: 'BOTH', keyword: auditPurpose, page: 1, size: 20
    }),
    { withMeta: true }
  ))
  assert.match(sensitiveAudit.cacheControl, /no-store/i, '敏感访问审计响应必须禁止缓存')
  assertPageContains(
    sensitiveAudit.data,
    item => item.action === 'VIEW' && item.purpose === auditPurpose && item.residentId === resident.id,
    '敏感访问审计用途关键字检索'
  )
  const sensitiveAuditPayload = JSON.stringify(sensitiveAudit.data)
  assert.ok(!sensitiveAuditPayload.includes(residentIdCard) && !sensitiveAuditPayload.includes(`139${phoneLast8}`),
    '敏感访问审计不得返回敏感明文')
  assert.ok(!/ciphertext|hash/i.test(sensitiveAuditPayload), '敏感访问审计不得返回密文或哈希')

  await pass('管理员产生无责任网格的身份证敏感检索审计', () => admin.request(
    '/residents/sensitive-search',
    {
      method: 'POST',
      body: {
        type: 'ID_CARD', value: residentIdCard, status: 'ACTIVE', page: 1, size: 20
      }
    }
  ))
  const lifecycleMe = await pass('社区工作人员登录并查询范围内审计', () =>
    lifecycleClient.login(lifecycleUsername, lifecyclePassword))
  assert.ok(lifecycleMe.permissions.includes('resident:sensitive:audit:read'),
    '社区工作人员缺少敏感访问审计权限')
  const restrictedAudit = await lifecycleClient.request(
    query('/residents/sensitive-access-logs', {
      action: 'SEARCH', fieldType: 'ID_CARD', keyword: adminUsername, page: 1, size: 20
    }),
    { withMeta: true }
  )
  assert.match(restrictedAudit.cacheControl, /no-store/i, '范围内敏感审计响应必须禁止缓存')
  assert.ok(restrictedAudit.data.items.every(item => item.operatorUsername !== adminUsername),
    '受限社区工作人员不能读取其他用户的无责任网格审计记录')

  const residentClient = new ApiClient(apiBase, 'resident-user')
  await residentClient.refreshCsrf()
  const residentApplication = await pass('居民公开注册并匹配既有档案', () => residentClient.request('/auth/register', {
    method: 'POST',
    body: {
      accountType: 'RESIDENT',
      username: residentUsername,
      password: residentPassword,
      realName: names.resident,
      phone: `139-${phoneLast8.slice(0, 4)}-${phoneLast8.slice(4)}`,
      idCardNumber: residentIdCard,
      note: 'runtime smoke resident registration'
    }
  }))
  assert.equal(residentApplication.approvalStatus, 'PENDING', '居民注册必须等待审核')

  const registrationPage = await admin.request(query('/system/users', {
    keyword: residentUsername,
    page: 1,
    size: 20
  }))
  const pendingResidentUser = registrationPage.items.find(item => item.username === residentUsername)
  assert.ok(pendingResidentUser, '管理员用户列表中未找到居民注册申请')
  assert.equal(pendingResidentUser.accountType, 'RESIDENT', '居民注册账号类型不匹配')
  assert.equal(pendingResidentUser.approvalStatus, 'PENDING', '居民注册审核状态不匹配')
  const pendingResidentDetail = await admin.request(`/system/users/${pendingResidentUser.id}`)
  assert.equal(pendingResidentDetail.phone, null, '居民注册申请不得保存明文手机号')

  const approvedResidentUser = await pass('管理员批准居民注册并绑定档案', () => admin.request(
    `/system/users/${pendingResidentUser.id}/registration-review`,
    {
      method: 'POST',
      body: { decision: 'APPROVE', roleCodes: [], version: pendingResidentUser.version }
    }
  ))
  assertStatus(approvedResidentUser, 'ENABLED', '批准后的居民账号')
  assert.equal(approvedResidentUser.approvalStatus, 'APPROVED', '居民注册未进入已批准状态')
  assert.deepEqual(approvedResidentUser.roles, ['RESIDENT'], '居民账号只能获得 RESIDENT 角色')

  const residentMe = await pass('居民登录并获得隔离权限', () => residentClient.login(residentUsername, residentPassword))
  assert.deepEqual(residentMe.roles, ['RESIDENT'], '居民登录角色不匹配')
  assert.deepEqual(
    [...residentMe.permissions].sort(),
    [
      'announcement:read',
      'resident:portal',
      'service:application:apply',
      'service:application:cancel',
      'service:application:rate',
      'service:catalog:read',
      'workbench:resident:read'
    ].sort(),
    '居民权限必须收敛到本人服务中心能力'
  )
  const residentNavigation = await pass('居民服务台动态导航', () => residentClient.request('/auth/navigation'))
  assertNavigation(residentNavigation, '居民服务台动态导航')
  assert.deepEqual(residentNavigation.map(item => item.code), [
    'RESIDENT_PORTAL',
    'RESIDENT_REPORT',
    'RESIDENT_EVENTS',
    'RESIDENT_PROFILE',
    'RESIDENT_SERVICE',
    'RESIDENT_RATING',
    'ANNOUNCEMENT'
  ], '居民动态导航必须返回七个本人服务入口')
  const residentOverview = await residentClient.request('/resident-portal/overview')
  assert.equal(residentOverview.profile.id, resident.id, '居民服务台未返回本人绑定档案')
  assert.ok(Array.isArray(residentOverview.categories) && residentOverview.categories.length > 0, '居民服务台缺少事件类别')
  assert.ok(Array.isArray(residentOverview.events), '居民服务台本人事件必须为数组')
  await assert.rejects(
    () => residentClient.request('/residents/sensitive-search', {
      method: 'POST',
      body: {
        type: 'PHONE',
        value: `139${phoneLast8}`,
        gridId: grid.id,
        status: 'ACTIVE',
        page: 1,
        size: 20
      }
    }),
    /HTTP 403/,
    '居民账号不应检索居民敏感字段'
  )

  const communityAnnouncement = await pass('社区公告创建', () => lifecycleClient.request('/announcements', {
    method: 'POST',
    body: {
      audienceScope: 'COMMUNITY',
      communityId: community.id,
      title: `运行时社区公告-${shortKey}`,
      content: `社区公告闭环验证 ${runKey}`,
      pinned: false
    }
  }))
  assertStatus(communityAnnouncement, 'DRAFT', '社区公告草稿')
  const publishedAnnouncement = await pass('社区公告发布', () => lifecycleClient.request(
    `/announcements/${communityAnnouncement.id}/publish`,
    { method: 'POST', body: { version: communityAnnouncement.version, remark: '运行时公告发布' } }
  ))
  assertStatus(publishedAnnouncement, 'PUBLISHED', '已发布社区公告')
  const residentAnnouncements = await pass('居民可见社区公告', () => residentClient.request('/announcements'))
  assert.ok(
    residentAnnouncements.some(item => item.id === publishedAnnouncement.id && item.status === 'PUBLISHED'),
    '居民公告列表缺少已发布社区公告'
  )
  const withdrawnAnnouncement = await pass('社区公告撤回并对居民隐藏', () => lifecycleClient.request(
    `/announcements/${publishedAnnouncement.id}/withdraw`,
    { method: 'POST', body: { version: publishedAnnouncement.version, reason: '运行时撤回验证' } }
  ))
  assertStatus(withdrawnAnnouncement, 'WITHDRAWN', '已撤回社区公告')
  const hiddenAnnouncements = await residentClient.request('/announcements')
  assert.ok(!hiddenAnnouncements.some(item => item.id === withdrawnAnnouncement.id), '居民仍可看到已撤回公告')
  await assert.rejects(
    () => residentClient.request(`/announcements/${withdrawnAnnouncement.id}`),
    /HTTP 404/,
    '居民不能读取已撤回公告详情'
  )
  const announcementFlows = await lifecycleClient.request(`/announcements/${communityAnnouncement.id}/flows`)
  assertFlowSequence(announcementFlows, ['CREATE', 'PUBLISH', 'WITHDRAW'], '社区公告流转顺序')

  const serviceCatalogs = await pass('居民读取可申请服务目录', () => residentClient.request('/service-catalogs'))
  assert.ok(Array.isArray(serviceCatalogs) && serviceCatalogs.length > 0, '居民服务目录不能为空')
  const serviceCatalog = serviceCatalogs[0]
  assert.ok(serviceCatalog.id, '服务目录缺少真实 ID')
  const serviceRequestToken = randomUUID()
  const serviceApplicationPayload = {
    serviceCatalogId: serviceCatalog.id,
    requestContent: `运行时服务申请-${runKey}`,
    appointmentAt: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString().slice(0, 19),
    requestToken: serviceRequestToken
  }
  let serviceApplication = await pass('居民服务申请提交', () => residentClient.request(
    '/resident-portal/service-applications',
    { method: 'POST', body: serviceApplicationPayload }
  ))
  assertStatus(serviceApplication, 'SUBMITTED', '居民服务申请')
  assert.equal(serviceApplication.residentId, resident.id, '服务申请居民归属不匹配')
  assert.equal(serviceApplication.gridId, grid.id, '服务申请网格快照不匹配')
  const retriedServiceApplication = await pass('居民服务申请幂等令牌重试', () => residentClient.request(
    '/resident-portal/service-applications',
    { method: 'POST', body: serviceApplicationPayload }
  ))
  assert.deepEqual(
    [retriedServiceApplication.id, retriedServiceApplication.version, retriedServiceApplication.status],
    [serviceApplication.id, serviceApplication.version, serviceApplication.status],
    '相同 requestToken 重试不得创建第二条服务申请'
  )
  serviceApplication = await pass('社区受理服务申请', () => lifecycleClient.request(
    `/service-applications/${serviceApplication.id}/accept`,
    { method: 'POST', body: { version: serviceApplication.version, remark: '社区受理服务申请' } }
  ))
  assertStatus(serviceApplication, 'ACCEPTED', '已受理服务申请')
  assert.equal(serviceApplication.handlerUserId, lifecycleUser.id, '服务申请处理人不匹配')
  serviceApplication = await pass('社区开始处理服务申请', () => lifecycleClient.request(
    `/service-applications/${serviceApplication.id}/start`,
    { method: 'POST', body: { version: serviceApplication.version, remark: '社区开始处理服务申请' } }
  ))
  assertStatus(serviceApplication, 'PROCESSING', '处理中服务申请')
  serviceApplication = await pass('社区办结服务申请', () => lifecycleClient.request(
    `/service-applications/${serviceApplication.id}/complete`,
    {
      method: 'POST',
      body: {
        version: serviceApplication.version,
        resultSummary: `服务申请已办结-${runKey}`,
        remark: '社区完成服务申请'
      }
    }
  ))
  assertStatus(serviceApplication, 'COMPLETED', '已办结服务申请')
  const ratedServiceApplication = await pass('居民服务申请评分', () => residentClient.request(
    `/resident-portal/service-applications/${serviceApplication.id}/rate`,
    { method: 'POST', body: { version: serviceApplication.version, rating: 5, remark: '运行时服务评分' } }
  ))
  assertStatus(ratedServiceApplication, 'COMPLETED', '评分后服务申请')
  assert.equal(ratedServiceApplication.rating, 5, '服务申请评分未保存')
  const serviceApplicationFlows = await lifecycleClient.request(`/service-applications/${serviceApplication.id}/flows`)
  assertFlowSequence(
    serviceApplicationFlows,
    ['APPLY', 'ACCEPT', 'START', 'COMPLETE', 'RATE'],
    '服务申请流转顺序'
  )

  const residentReportedEvent = await pass('居民从本人网格上报事项', () => residentClient.request('/resident-portal/events', {
    method: 'POST',
    body: {
      categoryId,
      title: `居民自助上报-${runKey}`,
      description: `居民服务台闭环验证 ${runKey}`,
      severity: 'MEDIUM',
      address: names.address
    }
  }))
  assertStatus(residentReportedEvent, 'REPORTED', '居民上报事件')
  assert.equal(residentReportedEvent.gridId, grid.id, '居民事项必须自动归入本人网格')
  const residentAttachmentBytes = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 9, 8, 7, 6])
  const removableResidentAttachment = await pass('居民上传本人待受理事件附件', () => residentClient.uploadFile(
    `/resident-portal/events/${residentReportedEvent.id}/attachments`,
    { bytes: residentAttachmentBytes, filename: `居民待删-${shortKey}.jpg`, contentType: 'image/jpeg' }
  ))
  const residentAttachment = await residentClient.uploadFile(
    `/resident-portal/events/${residentReportedEvent.id}/attachments`,
    { bytes: residentAttachmentBytes, filename: `居民保留-${shortKey}.jpg`, contentType: 'image/jpeg' }
  )
  let residentAttachments = await residentClient.request(
    `/resident-portal/events/${residentReportedEvent.id}/attachments`
  )
  assert.ok(residentAttachments.some(item => item.id === residentAttachment.id), '居民附件列表缺少本人附件')
  const downloadedResidentAttachment = await residentClient.downloadFile(
    `/resident-portal/events/${residentReportedEvent.id}/attachments/${residentAttachment.id}/content`
  )
  assert.deepEqual(downloadedResidentAttachment.bytes, residentAttachmentBytes, '居民嵌套附件下载内容不一致')
  assert.match(downloadedResidentAttachment.cacheControl, /no-store/i, '居民附件下载必须禁止缓存')
  await pass('居民删除本人待受理事件附件', () => residentClient.request(
    `/resident-portal/events/${residentReportedEvent.id}/attachments/${removableResidentAttachment.id}`,
    { method: 'DELETE' }
  ))
  residentAttachments = await residentClient.request(`/resident-portal/events/${residentReportedEvent.id}/attachments`)
  assert.ok(!residentAttachments.some(item => item.id === removableResidentAttachment.id),
    '居民已删除附件仍出现在活动列表中')

  await assert.rejects(
    () => admin.request(`/system/event-categories/${managedCategory.id}`, {
      method: 'PUT',
      body: {
        name: managedCategory.name, description: managedCategory.description, sortNo: managedCategory.sortNo,
        status: 'DISABLED', version: managedCategory.version
      }
    }),
    /HTTP 409/,
    '仍被待受理事件引用的类别不能停用'
  )
  const refreshedResidentOverview = await residentClient.request('/resident-portal/overview')
  assert.ok(
    refreshedResidentOverview.events.some(item => item.id === residentReportedEvent.id),
    '居民服务台未返回本人刚上报的事项'
  )
  await assert.rejects(
    () => residentClient.request('/events'),
    /HTTP 403/,
    '居民账号不应访问后台全量事件列表'
  )

  const dispatcherMe = await pass('独立派发管理员登录', () => dispatcher.login(dispatcherUsername, dispatcherPassword))
  assert.equal(dispatcherMe.id, dispatcherUser.id, '派发管理员会话用户不匹配')
  assert.ok(dispatcherMe.roles.includes('SYSTEM_ADMIN'), '派发管理员缺少 SYSTEM_ADMIN 角色')

  const reportedEvent = await pass('事件上报', () => dispatcher.request('/events', {
    method: 'POST',
    body: {
      categoryId,
      gridId: grid.id,
      title: names.event,
      description: `可复现冒烟事件 ${runKey}`,
      reportChannel: 'WEB',
      severity: 'HIGH',
      address: names.address,
      reporterName: `冒烟上报人-${shortKey}`
    }
  }))
  assertStatus(reportedEvent, 'REPORTED', '上报事件')

  const attachmentBytes = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 0, 1, 2, 3])
  const eventAttachmentRequestToken = randomUUID()
  const attachment = await pass('事件附件上传与内容校验', () => dispatcher.uploadFile(
    `/events/${reportedEvent.id}/attachments`,
    {
      bytes: attachmentBytes, filename: `现场照片-${shortKey}.jpg`, contentType: 'image/jpeg',
      requestToken: eventAttachmentRequestToken
    }
  ))
  assert.equal(attachment.eventId, reportedEvent.id, '附件事件关联不匹配')
  assert.equal(attachment.contentType, 'image/jpeg', '附件 MIME 类型不匹配')
  assert.equal(attachment.fileSize, attachmentBytes.length, '附件大小不匹配')
  assert.match(attachment.sha256, /^[0-9a-f]{64}$/, '附件 SHA-256 格式不正确')
  const attachmentList = await dispatcher.request(`/events/${reportedEvent.id}/attachments`)
  assert.ok(attachmentList.some(item => item.id === attachment.id), '事件附件列表缺少刚上传的文件')
  const retryAttachment = await pass('事件附件相同请求令牌重试幂等', () => dispatcher.uploadFile(
    `/events/${reportedEvent.id}/attachments`,
    {
      bytes: attachmentBytes, filename: `现场照片-${shortKey}.jpg`, contentType: 'image/jpeg',
      requestToken: eventAttachmentRequestToken
    }
  ))
  assert.equal(retryAttachment.id, attachment.id, '相同 requestToken 重试必须返回原事件附件')
  const idempotentAttachmentList = await dispatcher.request(`/events/${reportedEvent.id}/attachments`)
  assert.equal(idempotentAttachmentList.length, attachmentList.length, '相同 requestToken 重试不得增加事件附件记录')
  const removableEventAttachment = await dispatcher.uploadFile(
    `/events/${reportedEvent.id}/attachments`,
    { bytes: attachmentBytes, filename: `现场待删-${shortKey}.jpg`, contentType: 'image/jpeg' }
  )
  await pass('后台删除待受理事件附件', () => dispatcher.request(
    `/events/${reportedEvent.id}/attachments/${removableEventAttachment.id}`,
    { method: 'DELETE' }
  ))
  const activeAttachmentList = await dispatcher.request(`/events/${reportedEvent.id}/attachments`)
  assert.ok(!activeAttachmentList.some(item => item.id === removableEventAttachment.id),
    '后台已删除事件附件仍出现在活动列表中')
  await assert.rejects(
    () => dispatcher.downloadFile(`/files/${removableEventAttachment.id}`),
    /HTTP 404/,
    '软删除事件附件不应继续下载'
  )
  await assert.rejects(
    () => dispatcher.uploadFile(`/events/${reportedEvent.id}/attachments`, {
      bytes: Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      filename: '伪装图片.jpg',
      contentType: 'image/jpeg'
    }),
    /HTTP 400/,
    '内容签名与 MIME 不一致的附件必须被拒绝'
  )

  const acceptedEvent = await pass('事件受理', () => dispatcher.request(`/events/${reportedEvent.id}/accept`, {
    method: 'POST',
    body: { version: reportedEvent.version, remark: '冒烟验证受理' }
  }))
  assertStatus(acceptedEvent, 'ACCEPTED', '受理事件')

  const assignedEvent = await pass('事件派单并派生任务', () => dispatcher.request(`/events/${reportedEvent.id}/assign`, {
    method: 'POST',
    body: {
      version: acceptedEvent.version,
      assigneeUserId: workerUser.id,
      taskTitle: names.eventTask,
      taskDescription: `处置冒烟事件 ${runKey}`,
      priority: 'HIGH',
      dueAt,
      remark: '冒烟验证派单'
    }
  }))
  assertStatus(assignedEvent, 'ASSIGNED', '派单事件')
  assert.equal(assignedEvent.assignedToUserId, workerUser.id, '事件执行人不匹配')
  await assert.rejects(
    () => dispatcher.request(`/events/${reportedEvent.id}/attachments/${attachment.id}`, { method: 'DELETE' }),
    /HTTP 409/,
    '派单后事件不允许删除附件'
  )
  await assert.rejects(
    () => residentClient.request(`/resident-portal/events/${reportedEvent.id}/attachments`),
    /HTTP 403/,
    '居民不能借同网格读取他人事件附件'
  )
  await assert.rejects(
    () => residentClient.request(
      `/resident-portal/events/${reportedEvent.id}/attachments/${attachment.id}`,
      { method: 'DELETE' }
    ),
    /HTTP 403/,
    '居民不能删除工作人员事件附件'
  )

  const eventTaskPage = await dispatcher.request(query('/tasks', {
    keyword: names.eventTask,
    status: 'PENDING_ACCEPT',
    page: 1,
    size: 20
  }))
  assert.ok(eventTaskPage && Array.isArray(eventTaskPage.items), '事件派生任务列表格式不正确')
  const matchingEventTasks = eventTaskPage.items.filter(item => item.sourceEventId === reportedEvent.id)
  assert.equal(matchingEventTasks.length, 1, '应找到唯一的事件派生任务')
  const eventTask = matchingEventTasks[0]
  assertStatus(eventTask, 'PENDING_ACCEPT', '事件派生任务')
  assert.equal(eventTask.assigneeUserId, workerUser.id, '事件任务执行人不匹配')

  const unscopedMe = await pass('未分配网格员登录', () => unscopedWorker.login(unscopedWorkerUsername, workerPassword))
  assert.equal(unscopedMe.id, unscopedWorkerUser.id, '未分配网格员会话用户不匹配')
  await assert.rejects(
    () => unscopedWorker.request(`/tasks/${eventTask.id}`),
    /HTTP 403/,
    '未分配网格员不能跨数据范围读取任务'
  )
  await pass('DELETE 跨域预检', () => assertDeleteCors(
    apiBase, corsOrigin, `/tasks/${eventTask.id}/attachments/1`
  ))

  const workerMe = await pass('网格员登录与数据权限', () => worker.login(workerUsername, workerPassword))
  assert.equal(workerMe.id, workerUser.id, '网格员会话用户不匹配')
  assert.ok(workerMe.roles.includes('GRID_WORKER'), '网格员缺少 GRID_WORKER 角色')
  assert.ok(workerMe.permissions.includes('task:accept'), '网格员缺少接单权限')
  assert.ok(workerMe.permissions.includes('task:handle'), '网格员缺少处置权限')
  const workerGrid = await worker.request(`/grids/${grid.id}`)
  assert.equal(workerGrid.id, grid.id, '网格员无法读取分配网格')

  const patrolScheduledAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 19)
  const patrolDueAt = new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString().slice(0, 19)
  const patrolPlan = await pass('创建巡查计划并原子生成任务', () => lifecycleClient.request('/patrol-plans', {
    method: 'POST',
    body: {
      gridId: grid.id,
      title: `运行时巡查计划-${shortKey}`,
      inspectionContent: `巡查计划原子任务验证 ${runKey}`,
      scheduledAt: patrolScheduledAt,
      dueAt: patrolDueAt,
      assigneeUserId: workerUser.id,
      priority: 'MEDIUM'
    }
  }))
  assertStatus(patrolPlan, 'ACTIVE', '巡查计划')
  assert.equal(patrolPlan.gridId, grid.id, '巡查计划网格归属不匹配')
  assert.equal(patrolPlan.assigneeUserId, workerUser.id, '巡查计划执行人不匹配')
  assert.ok(patrolPlan.taskId && Number.isInteger(patrolPlan.taskVersion), '巡查计划未原子生成关联任务')
  const patrolTask = await worker.request(`/tasks/${patrolPlan.taskId}`)
  assertStatus(patrolTask, 'PENDING_ACCEPT', '巡查计划关联任务')
  assert.equal(patrolTask.taskType, 'ROUTINE_INSPECTION', '巡查计划任务类型不匹配')
  assert.equal(patrolTask.dispatcherUserId, lifecycleUser.id, '巡查任务派发人应为社区工作人员')
  assert.equal(patrolTask.assigneeUserId, workerUser.id, '巡查任务执行人不匹配')
  const acceptedPatrolTask = await pass('巡查任务接单', () => worker.request(
    `/tasks/${patrolTask.id}/accept`,
    { method: 'POST', body: { version: patrolTask.version, remark: '网格员接受巡查计划' } }
  ))
  assertStatus(acceptedPatrolTask, 'PROCESSING', '接单后巡查任务')
  const pendingPatrolTask = await pass('巡查任务提交复核', () => worker.request(
    `/tasks/${acceptedPatrolTask.id}/submit-review`,
    {
      method: 'POST',
      body: {
        version: acceptedPatrolTask.version,
        handlingResult: `巡查计划已完成-${runKey}`,
        attachmentIds: [],
        remark: '提交社区复核'
      }
    }
  ))
  assertStatus(pendingPatrolTask, 'PENDING_REVIEW', '待复核巡查任务')
  assert.notEqual(adminMe.id, patrolTask.dispatcherUserId, '巡查任务复核人不得是派发人')
  const completedPatrolTask = await pass('非派发管理员复核巡查任务', () => admin.request(
    `/tasks/${pendingPatrolTask.id}/review`,
    {
      method: 'POST',
      body: { version: pendingPatrolTask.version, approved: true, remark: '管理员复核巡查通过' }
    }
  ))
  assertStatus(completedPatrolTask, 'COMPLETED', '已完成巡查任务')
  const patrolPage = await admin.request('/patrol-plans?page=1&size=100')
  const completedPatrolPlan = patrolPage.items.find(item => item.id === patrolPlan.id)
  assert.ok(completedPatrolPlan, '巡查计划列表缺少刚创建的计划')
  assert.equal(completedPatrolPlan.status, 'COMPLETED', '巡查计划未随任务复核完成')
  assert.equal(completedPatrolPlan.taskStatus, 'COMPLETED', '巡查计划关联任务未完成')
  const patrolTaskFlows = await admin.request(`/tasks/${patrolPlan.taskId}/flows`)
  assertFlowSequence(patrolTaskFlows, ['ASSIGN', 'ACCEPT', 'SUBMIT_REVIEW', 'APPROVE'], '巡查任务流转顺序')

  const downloadedAttachment = await pass('责任网格员授权下载事件附件', () =>
    worker.downloadFile(`/files/${attachment.id}`))
  assert.deepEqual(downloadedAttachment.bytes, attachmentBytes, '下载附件内容不一致')
  assert.equal(downloadedAttachment.contentType, 'image/jpeg', '下载附件 MIME 不一致')
  assert.match(downloadedAttachment.contentDisposition || '', /attachment/i, '附件下载响应缺少 disposition')

  const processingEventTask = await pass('网格员接受事件任务', () => worker.request(`/tasks/${eventTask.id}/accept`, {
    method: 'POST',
    body: { version: eventTask.version, remark: '网格员接单' }
  }))
  assertStatus(processingEventTask, 'PROCESSING', '接受后事件任务')
  const processingEvent = await worker.request(`/events/${reportedEvent.id}`)
  assertStatus(processingEvent, 'PROCESSING', '接单后事件')

  await assert.rejects(
    () => dispatcher.uploadFile(`/tasks/${eventTask.id}/attachments`, {
      bytes: attachmentBytes, filename: `非执行人-${shortKey}.jpg`, contentType: 'image/jpeg'
    }),
    /HTTP 403/,
    '非当前任务执行人不能上传任务附件'
  )
  const validTaskAttachment = await pass('网格员上传并下载本人处理中任务附件', () => worker.uploadFile(
    `/tasks/${eventTask.id}/attachments`,
    { bytes: attachmentBytes, filename: `处置凭证-${shortKey}.jpg`, contentType: 'image/jpeg' }
  ))
  assert.equal(validTaskAttachment.taskId, eventTask.id, '任务附件关联任务不匹配')
  let taskAttachments = await worker.request(`/tasks/${eventTask.id}/attachments`)
  assert.ok(taskAttachments.some(item => item.id === validTaskAttachment.id), '任务附件列表缺少刚上传附件')
  const downloadedTaskAttachment = await worker.downloadFile(
    `/tasks/${eventTask.id}/attachments/${validTaskAttachment.id}/content`
  )
  assert.deepEqual(downloadedTaskAttachment.bytes, attachmentBytes, '任务附件下载内容不一致')
  assert.match(downloadedTaskAttachment.cacheControl, /no-store/i, '任务附件下载必须禁止缓存')

  const foreignTask = await dispatcher.request('/tasks', {
    method: 'POST',
    body: {
      gridId: grid.id, taskType: 'OTHER', title: `附件归属样本-${shortKey}`,
      description: '用于验证任务附件不能跨任务引用', priority: 'LOW',
      assigneeUserId: workerUser.id, dueAt
    }
  })
  const processingForeignTask = await worker.request(`/tasks/${foreignTask.id}/accept`, {
    method: 'POST', body: { version: foreignTask.version, remark: '附件归属样本接单' }
  })
  assertStatus(processingForeignTask, 'PROCESSING', '附件归属样本任务')
  const foreignTaskAttachment = await worker.uploadFile(`/tasks/${foreignTask.id}/attachments`, {
    bytes: attachmentBytes, filename: `其他任务-${shortKey}.jpg`, contentType: 'image/jpeg'
  })
  await assert.rejects(
    () => worker.request(`/tasks/${eventTask.id}/submit-review`, {
      method: 'POST',
      body: {
        version: processingEventTask.version, handlingResult: `跨任务附件拒绝 ${runKey}`,
        attachmentIds: [foreignTaskAttachment.id], remark: '不允许跨任务引用'
      }
    }),
    /HTTP 400/,
    '提交复核不能引用其他任务附件'
  )
  const deletedTaskAttachment = await worker.uploadFile(`/tasks/${eventTask.id}/attachments`, {
    bytes: attachmentBytes, filename: `已删除凭证-${shortKey}.jpg`, contentType: 'image/jpeg'
  })
  await pass('网格员删除本人处理中任务附件', () => worker.request(
    `/tasks/${eventTask.id}/attachments/${deletedTaskAttachment.id}`, { method: 'DELETE' }
  ))
  taskAttachments = await worker.request(`/tasks/${eventTask.id}/attachments`)
  assert.ok(!taskAttachments.some(item => item.id === deletedTaskAttachment.id), '已删除任务附件仍出现在活动列表中')
  await assert.rejects(
    () => worker.request(`/tasks/${eventTask.id}/submit-review`, {
      method: 'POST',
      body: {
        version: processingEventTask.version, handlingResult: `已删除附件拒绝 ${runKey}`,
        attachmentIds: [deletedTaskAttachment.id], remark: '不允许引用已删除附件'
      }
    }),
    /HTTP 400/,
    '提交复核不能引用已删除任务附件'
  )

  const pendingEventTask = await pass('网格员提交事件任务复核', () => worker.request(`/tasks/${eventTask.id}/submit-review`, {
    method: 'POST',
    body: {
      version: processingEventTask.version,
      handlingResult: `冒烟处置完成 ${runKey}`,
      attachmentIds: [validTaskAttachment.id],
      remark: '提交复核'
    }
  }))
  assertStatus(pendingEventTask, 'PENDING_REVIEW', '待复核事件任务')
  const pendingEvent = await admin.request(`/events/${reportedEvent.id}`)
  assertStatus(pendingEvent, 'PENDING_REVIEW', '待复核事件')

  const completedEventTask = await pass('引导管理员复核事件任务', () => admin.request(`/tasks/${eventTask.id}/review`, {
    method: 'POST',
    body: {
      version: pendingEventTask.version,
      eventVersion: pendingEvent.version,
      approved: true,
      remark: '冒烟验证复核通过'
    }
  }))
  assertStatus(completedEventTask, 'COMPLETED', '已完成事件任务')
  const closedEvent = await admin.request(`/events/${reportedEvent.id}`)
  assertStatus(closedEvent, 'CLOSED', '已关闭事件')
  assert.equal(closedEvent.resultSummary, pendingEventTask.handlingResult, '事件办结结果不匹配')
  await assert.rejects(
    () => admin.request(`/tasks/${eventTask.id}/attachments/${validTaskAttachment.id}`, { method: 'DELETE' }),
    /HTTP 409/,
    '终态任务不允许删除附件'
  )

  const completedDashboard = await pass('看板 D3/D4 与数据范围一致性', () => admin.request('/dashboard/overview'))
  assertDashboardOverview(completedDashboard, '办结后系统管理员看板')
  const completedGridStat = completedDashboard.gridEventStats.find(item => item.gridId === grid.id)
  assert.ok(completedGridStat, 'D3 缺少当前网格统计')
  assert.ok(completedGridStat.completedWithDeadlineCount >= 1,
    'D3 的分母必须包含已完成且有期限的事件派生任务')
  assert.ok(completedGridStat.onTimeClosedCount >= 1, 'D3 缺少按期办结事件派生任务')
  assert.equal(Number(completedGridStat.onTimeCompletionRate), 100,
    'D3 按期办结率必须以 0..100 数值表达，1 表示 1% 而非比例小数')
  const categoryPercentageTotal = completedDashboard.categoryStats
    .reduce((total, item) => total + Number(item.percentage), 0)
  assert.ok(categoryPercentageTotal >= 99.99 && categoryPercentageTotal <= 100.01,
    'D4 类别百分比必须按 0..100 汇总')
  for (let index = 1; index < completedDashboard.recentEvents.length; index += 1) {
    const previous = completedDashboard.recentEvents[index - 1]
    const current = completedDashboard.recentEvents[index]
    const previousOrder = `${previous.reportedAt}|${String(previous.id).padStart(20, '0')}`
    const currentOrder = `${current.reportedAt}|${String(current.id).padStart(20, '0')}`
    assert.ok(previousOrder >= currentOrder, 'D4 最近事件必须按上报时间和 ID 倒序')
  }
  const workerWorkbench = await worker.request('/workbenches/grid/summary')
  assert.equal(workerWorkbench.role, 'GRID_WORKER', '网格员工作台角色标识不匹配')
  assert.ok(workerWorkbench.scopeLabel.includes('本人'), '网格员工作台必须明确按本人执行范围汇总')
  assert.ok(workerWorkbench.metrics && typeof workerWorkbench.metrics === 'object',
    '网格员工作台必须返回真实统计指标')
  for (const key of ['pendingAccept', 'processing', 'pendingReview', 'overdue', 'activePatrolPlans', 'reportsLast7Days']) {
    assert.ok(Number.isInteger(Number(workerWorkbench.metrics[key])) && Number(workerWorkbench.metrics[key]) >= 0,
      `网格员工作台指标 ${key} 必须是非负整数`)
  }
  assert.ok(Array.isArray(workerWorkbench.focusItems) && Array.isArray(workerWorkbench.recentItems),
    '网格员工作台必须返回待办与最近记录')
  const workerEvents = await worker.request(query('/events', { page: 1, size: 100 }))
  assert.ok(workerEvents.items.every(item => item.gridId === grid.id),
    '网格员事件台账不得返回责任范围以外的数据')

  const latestGrid = await admin.request(`/grids/${grid.id}`)
  await assert.rejects(
    () => admin.request(`/grids/${grid.id}/status`, {
      method: 'PATCH', body: { status: 'DISABLED', version: latestGrid.version }
    }),
    /HTTP 409/,
    '仍有居民、未办结事件或任务依赖时不能停用网格'
  )

  const eventFlows = await admin.request(`/events/${reportedEvent.id}/flows`)
  assertActions(eventFlows, ['REPORT', 'ACCEPT', 'ASSIGN', 'START', 'SUBMIT_REVIEW', 'APPROVE'], '事件流转')
  const eventTaskFlows = await admin.request(`/tasks/${eventTask.id}/flows`)
  assertActions(eventTaskFlows, ['ASSIGN', 'ACCEPT', 'SUBMIT_REVIEW', 'APPROVE'], '事件任务流转')

  const closedEventPage = await admin.request(query('/events', {
    keyword: names.event,
    status: 'CLOSED',
    page: 1,
    size: 20
  }))
  assertPageContains(closedEventPage, item => item.id === reportedEvent.id, '已关闭事件列表')

  const rejectedEvent = await pass('上报并驳回事件', async () => {
    const reported = await dispatcher.request('/events', {
      method: 'POST',
      body: {
        categoryId,
        gridId: grid.id,
        title: names.rejectedEvent,
        description: `验证事件驳回 ${runKey}`,
        reportChannel: 'PHONE',
        severity: 'LOW',
        address: names.address,
        reporterName: `驳回验证-${shortKey}`
      }
    })
    const rejected = await dispatcher.request(`/events/${reported.id}/reject`, {
      method: 'POST',
      body: { version: reported.version, reason: '冒烟验证驳回原因' }
    })
    assertStatus(rejected, 'REJECTED', '已驳回事件')
    return rejected
  })
  assertActions(
    await admin.request(`/events/${rejectedEvent.id}/flows`),
    ['REPORT', 'REJECT'],
    '驳回事件流转'
  )

  const cancelledEvent = await pass('上报并撤销事件', async () => {
    const reported = await dispatcher.request('/events', {
      method: 'POST',
      body: {
        categoryId,
        gridId: grid.id,
        title: names.cancelledEvent,
        description: `验证事件撤销 ${runKey}`,
        reportChannel: 'OTHER',
        severity: 'MEDIUM',
        address: names.address,
        reporterName: `撤销验证-${shortKey}`
      }
    })
    const cancelled = await dispatcher.request(`/events/${reported.id}/cancel`, {
      method: 'POST',
      body: { version: reported.version, reason: '冒烟验证撤销原因' }
    })
    assertStatus(cancelled, 'CANCELLED', '已撤销事件')
    return cancelled
  })
  assertActions(
    await admin.request(`/events/${cancelledEvent.id}/flows`),
    ['REPORT', 'CANCEL'],
    '撤销事件流转'
  )

  const independentTask = await pass('派发员创建独立任务', () => dispatcher.request('/tasks', {
    method: 'POST',
    body: {
      gridId: grid.id,
      taskType: 'ROUTINE_INSPECTION',
      title: names.independentTask,
      description: `独立任务冒烟验证 ${runKey}`,
      priority: 'MEDIUM',
      assigneeUserId: workerUser.id
    }
  }))
  assertStatus(independentTask, 'PENDING_ACCEPT', '独立任务')
  assert.equal(independentTask.sourceEventId, null, '独立任务不应有来源事件')

  const processingIndependentTask = await pass('网格员接受独立任务', () => worker.request(`/tasks/${independentTask.id}/accept`, {
    method: 'POST',
    body: { version: independentTask.version, remark: '接受独立巡查' }
  }))
  assertStatus(processingIndependentTask, 'PROCESSING', '处理中独立任务')

  let pendingIndependentTask = await pass('网格员提交独立任务复核', () => worker.request(`/tasks/${independentTask.id}/submit-review`, {
    method: 'POST',
    body: {
      version: processingIndependentTask.version,
      handlingResult: `独立巡查完成 ${runKey}`,
      attachmentIds: [],
      remark: '独立任务提交复核'
    }
  }))
  assertStatus(pendingIndependentTask, 'PENDING_REVIEW', '待复核独立任务')

  const returnedIndependentTask = await pass('引导管理员退回独立任务', () => admin.request(`/tasks/${independentTask.id}/review`, {
    method: 'POST',
    body: {
      version: pendingIndependentTask.version,
      approved: false,
      remark: '请补充巡查处置说明'
    }
  }))
  assertStatus(returnedIndependentTask, 'PROCESSING', '已退回独立任务')

  pendingIndependentTask = await pass('网格员整改后再次提交复核', () => worker.request(`/tasks/${independentTask.id}/submit-review`, {
    method: 'POST',
    body: {
      version: returnedIndependentTask.version,
      handlingResult: `独立巡查整改完成 ${runKey}`,
      attachmentIds: [],
      remark: '整改后再次提交'
    }
  }))
  assertStatus(pendingIndependentTask, 'PENDING_REVIEW', '再次待复核独立任务')

  const completedIndependentTask = await pass('引导管理员复核独立任务', () => admin.request(`/tasks/${independentTask.id}/review`, {
    method: 'POST',
    body: {
      version: pendingIndependentTask.version,
      approved: true,
      remark: '独立任务复核通过'
    }
  }))
  assertStatus(completedIndependentTask, 'COMPLETED', '已完成独立任务')

  const independentFlows = await admin.request(`/tasks/${independentTask.id}/flows`)
  assertActions(independentFlows, ['ASSIGN', 'ACCEPT', 'SUBMIT_REVIEW', 'RETURN', 'APPROVE'], '独立任务流转')
  const completedTaskPage = await admin.request(query('/tasks', {
    keyword: names.independentTask,
    status: 'COMPLETED',
    page: 1,
    size: 20
  }))
  assertPageContains(completedTaskPage, item => item.id === independentTask.id, '已完成独立任务列表')

  const cancelledTask = await pass('创建并取消独立任务', async () => {
    const created = await dispatcher.request('/tasks', {
      method: 'POST',
      body: {
        gridId: grid.id,
        taskType: 'OTHER',
        title: names.cancelledTask,
        description: `验证独立任务取消 ${runKey}`,
        priority: 'LOW',
        assigneeUserId: workerUser.id
      }
    })
    const cancelled = await dispatcher.request(`/tasks/${created.id}/cancel`, {
      method: 'POST',
      body: { version: created.version, reason: '冒烟验证取消原因' }
    })
    assertStatus(cancelled, 'CANCELLED', '已取消独立任务')
    return cancelled
  })
  assertActions(
    await admin.request(`/tasks/${cancelledTask.id}/flows`),
    ['ASSIGN', 'CANCEL'],
    '取消任务流转'
  )

  let stagedEvent = null
  let stagedTask = null
  if (names.openStage) {
    stagedEvent = await pass(`创建演示中的${names.openEvent}`, () => dispatcher.request('/events', {
      method: 'POST',
      body: {
        categoryId,
        gridId: grid.id,
        title: names.openEvent,
        description: `${names.openEvent}，用于展示跨阶段事项分布。`,
        reportChannel: 'ONSITE',
        severity: 'MEDIUM',
        address: names.address,
        reporterName: names.resident
      }
    }))
    assertStatus(stagedEvent, 'REPORTED', '演示待受理事件')

    if (names.openStage !== 'reported') {
      stagedEvent = await dispatcher.request(`/events/${stagedEvent.id}/accept`, {
        method: 'POST',
        body: { version: stagedEvent.version, remark: '演示数据受理' }
      })
      stagedEvent = await dispatcher.request(`/events/${stagedEvent.id}/assign`, {
        method: 'POST',
        body: {
          version: stagedEvent.version,
          assigneeUserId: workerUser.id,
          taskTitle: names.openTask,
          taskDescription: `${names.openTask}，完成后提交社区复核。`,
          priority: 'MEDIUM',
          remark: '演示数据派单'
        }
      })
      assertStatus(stagedEvent, 'ASSIGNED', '演示已派单事件')
      const stagedTaskPage = await dispatcher.request(query('/tasks', {
        keyword: names.openTask,
        page: 1,
        size: 20
      }))
      stagedTask = stagedTaskPage.items.find(item => item.sourceEventId === stagedEvent.id)
      assert.ok(stagedTask, '未找到演示阶段任务')
      assertStatus(stagedTask, 'PENDING_ACCEPT', '演示待接单任务')

      if (names.openStage === 'processing' || names.openStage === 'pending-review') {
        stagedTask = await worker.request(`/tasks/${stagedTask.id}/accept`, {
          method: 'POST',
          body: { version: stagedTask.version, remark: '演示网格员接单' }
        })
        assertStatus(stagedTask, 'PROCESSING', '演示处理中任务')
        stagedEvent = await worker.request(`/events/${stagedEvent.id}`)
        assertStatus(stagedEvent, 'PROCESSING', '演示处理中事件')
      }

      if (names.openStage === 'pending-review') {
        stagedTask = await worker.request(`/tasks/${stagedTask.id}/submit-review`, {
          method: 'POST',
          body: {
            version: stagedTask.version,
            handlingResult: `${names.openTask}已完成，等待社区复核。`,
            attachmentIds: [],
            remark: '演示数据提交复核'
          }
        })
        assertStatus(stagedTask, 'PENDING_REVIEW', '演示待复核任务')
        stagedEvent = await admin.request(`/events/${stagedEvent.id}`)
        assertStatus(stagedEvent, 'PENDING_REVIEW', '演示待复核事件')
      }
    }
  }

  console.log('SMOKE PASS')
  console.log(`runKey=${runKey}`)
  console.log(`community=${community.id} grid=${grid.id} household=${household.id} resident=${resident.id}`)
  console.log(`residentUser=${approvedResidentUser.id} residentEvent=${residentReportedEvent.id}:${residentReportedEvent.status}`)
  console.log(`event=${reportedEvent.id}:${closedEvent.status} eventTask=${eventTask.id}:${completedEventTask.status}`)
  console.log(`independentTask=${independentTask.id}:${completedIndependentTask.status}`)
  console.log(`rejectedEvent=${rejectedEvent.id}:${rejectedEvent.status} cancelledEvent=${cancelledEvent.id}:${cancelledEvent.status}`)
  console.log(`cancelledTask=${cancelledTask.id}:${cancelledTask.status}`)
  console.log(`announcement=${communityAnnouncement.id}:${withdrawnAnnouncement.status}`)
  console.log(`serviceApplication=${serviceApplication.id}:${ratedServiceApplication.status}:${ratedServiceApplication.rating}`)
  console.log(`patrolPlan=${patrolPlan.id}:${completedPatrolPlan.status} patrolTask=${patrolPlan.taskId}:${completedPatrolTask.status}`)
  console.log(`workerUsername=${workerUsername} dispatcherUsername=${dispatcherUsername}`)
  if (demoProfileCode) {
    console.log(`demoProfile=${demoProfileCode} stagedEvent=${stagedEvent.id}:${stagedEvent.status}`)
    if (stagedTask) console.log(`stagedTask=${stagedTask.id}:${stagedTask.status}`)
  }
}

main().catch(error => {
  console.error(`SMOKE FAIL: ${error.message}`)
  process.exitCode = 1
})
