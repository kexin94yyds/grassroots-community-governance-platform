#!/usr/bin/env node

import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import process from 'node:process'

function required(name, secret = false) {
  const value = process.env[name]
  if (!value) throw new Error(`Missing required environment variable: ${name}`)
  return secret ? value : value.trim()
}

if (Number(process.versions.node.split('.')[0]) !== 22) {
  throw new Error(`p1-lifecycle-smoke.mjs requires Node 22.x, got ${process.versions.node}`)
}
if (required('SMOKE_CONFIRM_ISOLATED') !== 'YES') {
  throw new Error('SMOKE_CONFIRM_ISOLATED must be exactly YES')
}

const baseUrl = required('SMOKE_BASE_URL')
const parsedBase = new URL(baseUrl)
if (!['127.0.0.1', 'localhost', '::1'].includes(parsedBase.hostname)) {
  throw new Error('P1 lifecycle smoke only accepts loopback targets')
}
const apiBase = parsedBase.pathname.replace(/\/+$/, '').endsWith('/api')
  ? baseUrl.replace(/\/+$/, '')
  : `${baseUrl.replace(/\/+$/, '')}/api`
const adminUsername = required('SMOKE_ADMIN_USERNAME')
const adminPassword = required('SMOKE_ADMIN_PASSWORD', true)
const residentUsername = process.env.SMOKE_P1_RESIDENT_USERNAME || 'resident-xingfu'
const residentName = process.env.SMOKE_P1_RESIDENT_NAME || '张建国'
const standbyUsername = process.env.SMOKE_P1_STANDBY_USERNAME || 'grid-standby-heyuan'
const gridName = process.env.SMOKE_P1_GRID_NAME || '和苑第二网格'
const temporaryPassword = `${randomBytes(24).toString('base64url')}Aa1!`
const finalPassword = `${randomBytes(24).toString('base64url')}Bb2!`

class Client {
  constructor(label) {
    this.label = label
    this.cookies = new Map()
    this.csrf = null
  }

  absorb(headers) {
    const values = typeof headers.getSetCookie === 'function' ? headers.getSetCookie() : []
    for (const value of values) {
      const [pair, ...attributes] = value.split(';')
      const index = pair.indexOf('=')
      if (index < 1) continue
      const name = pair.slice(0, index).trim()
      const content = pair.slice(index + 1).trim()
      const deleted = !content || attributes.some(item => /^\s*max-age\s*=\s*0\s*$/i.test(item))
      if (deleted) this.cookies.delete(name)
      else this.cookies.set(name, content)
    }
  }

  cookieHeader() {
    return [...this.cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ')
  }

  async request(path, { method = 'GET', body, status = 200, code = 'OK' } = {}) {
    const headers = { Accept: 'application/json' }
    const cookie = this.cookieHeader()
    if (cookie) headers.Cookie = cookie
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && this.csrf) headers['X-XSRF-TOKEN'] = this.csrf
    const response = await fetch(`${apiBase}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual',
      signal: AbortSignal.timeout(20_000)
    })
    this.absorb(response.headers)
    const envelope = JSON.parse(await response.text())
    assert.equal(response.status, status, `${this.label} ${method} ${path} HTTP`)
    assert.equal(envelope.code, code, `${this.label} ${method} ${path} code`)
    return envelope.data
  }

  async refreshCsrf() {
    await this.request('/auth/csrf')
    const token = this.cookies.get('XSRF-TOKEN')
    assert.ok(token, `${this.label} missing XSRF-TOKEN`)
    this.csrf = decodeURIComponent(token)
  }

  async login(username, password, expected = { status: 200, code: 'OK' }) {
    await this.refreshCsrf()
    const user = await this.request('/auth/login', {
      method: 'POST', body: { username, password }, ...expected
    })
    if (expected.status === 200) await this.refreshCsrf()
    return user
  }
}

function query(path, values) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) params.set(key, String(value))
  return `${path}?${params}`
}

async function findSingle(client, path, keyword, predicate) {
  const page = await client.request(query(path, { keyword, page: 1, size: 50 }))
  const item = page.items.find(predicate)
  assert.ok(item, `Missing expected item for ${keyword}`)
  return item
}

async function main() {
  const admin = new Client('admin')
  await admin.login(adminUsername, adminPassword)

  let residentUser = await findSingle(admin, '/system/users', residentUsername,
    item => item.username === residentUsername)
  await admin.request(`/system/users/${residentUser.id}/password-reset`, {
    method: 'POST',
    body: { temporaryPassword, version: residentUser.version }
  })

  const forced = new Client('forced-resident')
  const forcedUser = await forced.login(residentUsername, temporaryPassword)
  assert.equal(forcedUser.passwordChangeRequired, true, 'Reset login must require password change')
  await forced.request('/dashboard/overview', {
    status: 403, code: 'PASSWORD_CHANGE_REQUIRED'
  })
  await forced.request('/auth/password', {
    method: 'POST', body: { oldPassword: temporaryPassword, newPassword: finalPassword }
  })

  const stale = new Client('stale-password')
  await stale.login(residentUsername, temporaryPassword, {
    status: 401, code: 'INVALID_CREDENTIALS'
  })
  const resident = new Client('resident')
  const residentSession = await resident.login(residentUsername, finalPassword)
  assert.equal(residentSession.passwordChangeRequired, false, 'Completed change must clear flag')

  let residentRecord = await findSingle(admin, '/residents', residentName,
    item => item.realName === residentName)
  residentRecord = await admin.request(`/residents/${residentRecord.id}/status`, {
    method: 'PATCH', body: { status: 'MOVED', version: residentRecord.version }
  })
  await resident.request('/auth/me', { status: 401, code: 'UNAUTHENTICATED' })
  residentRecord = await admin.request(`/residents/${residentRecord.id}/status`, {
    method: 'PATCH', body: { status: 'ACTIVE', version: residentRecord.version }
  })
  residentUser = await findSingle(admin, '/system/users', residentUsername,
    item => item.username === residentUsername)
  assert.equal(residentUser.status, 'DISABLED', 'Restoring resident must not enable account')
  await admin.request(`/system/users/${residentUser.id}/status`, {
    method: 'PATCH', body: { enabled: true, version: residentUser.version }
  })
  const restored = new Client('restored-resident')
  await restored.login(residentUsername, finalPassword)

  const standby = await findSingle(admin, '/system/users', standbyUsername,
    item => item.username === standbyUsername)
  const gridPage = await admin.request(query('/grids', {
    keyword: gridName, areaType: 'GRID', page: 1, size: 20
  }))
  const gridSummary = gridPage.items.find(item => item.areaName === gridName)
  assert.ok(gridSummary, 'Missing heyuan grid')
  const before = await admin.request(`/grids/${gridSummary.id}`)
  await admin.request(`/grids/${before.id}/assignments`, {
    method: 'PUT',
    body: { version: before.version, assignments: [{ userId: standby.id, isPrimary: true }] },
    status: 409,
    code: 'CONFLICT'
  })
  const after = await admin.request(`/grids/${before.id}`)
  assert.deepEqual(after.assignments, before.assignments, 'Rejected replacement must preserve assignments')

  console.log(JSON.stringify({
    result: 'P1 LIFECYCLE PASS',
    checks: ['grid-removal-guard', 'resident-account-lifecycle', 'password-reset-and-forced-change']
  }, null, 2))
}

main().catch(error => {
  console.error(`P1 LIFECYCLE FAIL: ${error.message}`)
  process.exitCode = 1
})
