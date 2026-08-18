#!/usr/bin/env node

import assert from 'node:assert/strict'
import process from 'node:process'

function required(name, secret = false) {
  const value = process.env[name]
  if (!value) throw new Error(`Missing required environment variable: ${name}`)
  return secret ? value : value.trim()
}

if (Number(process.versions.node.split('.')[0]) !== 22) {
  throw new Error(`role-navigation-smoke.mjs requires Node 22.x, got ${process.versions.node}`)
}
if (required('ROLE_SMOKE_CONFIRM_ISOLATED') !== 'YES') {
  throw new Error('ROLE_SMOKE_CONFIRM_ISOLATED must be exactly YES')
}

const rawBase = required('ROLE_SMOKE_BASE_URL').replace(/\/+$/, '')
const parsedBase = new URL(rawBase)
if (!['127.0.0.1', 'localhost', '::1'].includes(parsedBase.hostname)) {
  throw new Error('Role navigation smoke only accepts loopback targets')
}
const apiBase = parsedBase.pathname.endsWith('/api') ? rawBase : `${rawBase}/api`

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
    assert.equal(response.status, status, `${this.label} ${path} HTTP`)
    assert.equal(envelope.code, code, `${this.label} ${path} code`)
    return envelope.data
  }

  async login(username, password) {
    await this.request('/auth/csrf')
    const token = this.cookies.get('XSRF-TOKEN')
    assert.ok(token, `${this.label} missing CSRF cookie`)
    this.csrf = decodeURIComponent(token)
    const user = await this.request('/auth/login', { method: 'POST', body: { username, password } })
    await this.request('/auth/csrf')
    this.csrf = decodeURIComponent(this.cookies.get('XSRF-TOKEN'))
    return user
  }
}

const matrix = [
  {
    label: 'ADMIN',
    username: required('ROLE_SMOKE_ADMIN_USERNAME'),
    password: required('ROLE_SMOKE_ADMIN_PASSWORD', true),
    role: 'SYSTEM_ADMIN',
    navigation: ['DASHBOARD', 'SYSTEM_USER', 'SYSTEM_ROLE', 'SYSTEM_MENU', 'EVENT_CATEGORY', 'GRID', 'RESIDENT', 'EVENT', 'TASK'],
    statuses: [200, 200, 200, 200, 200, 200, 200, 200, 200, 403]
  },
  {
    label: 'COMMUNITY',
    username: required('ROLE_SMOKE_COMMUNITY_USERNAME'),
    password: required('ROLE_SMOKE_COMMUNITY_PASSWORD', true),
    role: 'COMMUNITY_STAFF',
    navigation: ['DASHBOARD', 'GRID', 'RESIDENT', 'EVENT', 'TASK'],
    statuses: [403, 403, 403, 403, 200, 200, 200, 200, 200, 403]
  },
  {
    label: 'GRID',
    username: required('ROLE_SMOKE_GRID_USERNAME'),
    password: required('ROLE_SMOKE_GRID_PASSWORD', true),
    role: 'GRID_WORKER',
    navigation: ['DASHBOARD', 'EVENT', 'TASK'],
    statuses: [403, 403, 403, 403, 200, 200, 200, 200, 200, 403]
  },
  {
    label: 'RESIDENT',
    username: required('ROLE_SMOKE_RESIDENT_USERNAME'),
    password: required('ROLE_SMOKE_RESIDENT_PASSWORD', true),
    role: 'RESIDENT',
    navigation: ['RESIDENT_PORTAL'],
    statuses: [403, 403, 403, 403, 403, 403, 403, 403, 403, 200]
  }
]

const endpoints = [
  '/system/users', '/system/roles', '/system/menus', '/system/event-categories',
  '/grids', '/residents', '/events', '/tasks', '/dashboard/overview', '/resident-portal/overview'
]

async function main() {
  const results = []
  for (const profile of matrix) {
    const client = new Client(profile.label)
    const user = await client.login(profile.username, profile.password)
    assert.deepEqual(user.roles, [profile.role], `${profile.label} role`)
    const navigation = await client.request('/auth/navigation')
    assert.deepEqual(navigation.map(item => item.code), profile.navigation, `${profile.label} navigation`)
    for (const [index, endpoint] of endpoints.entries()) {
      const expectedStatus = profile.statuses[index]
      await client.request(endpoint, {
        status: expectedStatus,
        code: expectedStatus === 200 ? 'OK' : 'FORBIDDEN'
      })
    }
    results.push({ role: profile.role, navigation: profile.navigation })
  }
  console.log(JSON.stringify({ result: 'ROLE NAVIGATION PASS', roles: results }, null, 2))
}

main().catch(error => {
  console.error(`ROLE NAVIGATION FAIL: ${error.message}`)
  process.exitCode = 1
})
