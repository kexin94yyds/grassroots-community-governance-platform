#!/usr/bin/env node

import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

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

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const matrixPath = path.join(projectRoot, 'scripts', 'role-workbench-matrix.json')
const matrix = JSON.parse(fs.readFileSync(matrixPath, 'utf8'))
const roleEnvNames = {
  SYSTEM_ADMIN: 'ADMIN',
  COMMUNITY_STAFF: 'COMMUNITY',
  GRID_WORKER: 'GRID',
  RESIDENT: 'RESIDENT'
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

  async probe(path, { method = 'GET', body } = {}) {
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
    const raw = await response.text()
    let envelope = null
    try {
      envelope = raw ? JSON.parse(raw) : null
    } catch {
      // The status remains useful for the permission probe.
    }
    return { status: response.status, code: envelope?.code || 'NON_JSON' }
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

const profiles = Object.entries(matrix.roles).map(([role, contract]) => {
  const envName = roleEnvNames[role]
  if (!envName) throw new Error(`Role matrix contains unsupported role: ${role}`)
  return {
    label: envName,
    role,
    username: required(`ROLE_SMOKE_${envName}_USERNAME`),
    password: required(`ROLE_SMOKE_${envName}_PASSWORD`, true),
    contract
  }
})

function apiPath(pathValue) {
  const value = String(pathValue || '')
  if (!value.startsWith('/api/')) throw new Error(`Role matrix API path must start with /api/: ${value}`)
  return value.slice(4)
}

function resolveProbePath(pathValue) {
  return String(pathValue).replace(/\{([^}]+)\}/g, (_match, name) => {
    const envName = `ROLE_SMOKE_${String(name).replace(/[^A-Za-z0-9]/g, '_').toUpperCase()}_ID`
    return process.env[envName] || (name === 'code' ? 'SYSTEM_ADMIN' : '0')
  })
}

function allOperations() {
  return Object.entries(matrix.roles).flatMap(([ownerRole, contract]) =>
    (contract.writeGroups || []).flatMap(group =>
      (group.operations || []).map(operation => ({ ownerRole, group, operation }))
    )
  )
}

function assertStatsPayload(payload, role) {
  assert.ok(payload && typeof payload === 'object', `${role} statistics must be an object`)
  for (const field of matrix.statsFields || []) {
    assert.ok(Object.hasOwn(payload, field), `${role} statistics missing ${field}`)
  }
  assert.ok(payload.metrics && typeof payload.metrics === 'object', `${role} metrics must be an object`)
  assert.ok(Array.isArray(payload.focusItems), `${role} focusItems must be an array`)
  assert.ok(Array.isArray(payload.recentItems), `${role} recentItems must be an array`)
}

async function main() {
  const results = []
  const clients = new Map()
  const permissionsByRole = new Map()
  for (const profile of profiles) {
    const client = new Client(profile.label)
    clients.set(profile.role, client)
    const user = await client.login(profile.username, profile.password)
    assert.deepEqual(user.roles, [profile.role], `${profile.label} role`)
    permissionsByRole.set(profile.role, new Set(user.permissions || []))
    const navigation = await client.request('/auth/navigation')
    const expectedCodes = profile.contract.navigation.map(item => item.code)
    assert.equal(expectedCodes.length, matrix.exactNavigationCounts[profile.role], `${profile.label} matrix count`)
    assert.ok(expectedCodes.length >= matrix.minimumNavigationEntries, `${profile.label} has fewer than six entries`)
    assert.deepEqual(navigation.map(item => item.code), expectedCodes, `${profile.label} navigation`)
    for (const entry of profile.contract.navigation) {
      const payload = await client.request(apiPath(entry.readApi))
      assert.ok(payload !== undefined, `${profile.label}/${entry.code} read returned no data`)
    }
    const stats = await client.request(apiPath(profile.contract.statsApi.path))
    assertStatsPayload(stats, profile.role)
    results.push({
      role: profile.role,
      navigation: expectedCodes,
      stats: { api: profile.contract.statsApi.path, focusItems: stats.focusItems.length, recentItems: stats.recentItems.length },
      writeGroups: profile.contract.writeGroups.length,
      stateTransitions: profile.contract.stateTransitions
    })
  }

  for (const { ownerRole, group, operation } of allOperations()) {
    assert.ok(permissionsByRole.get(ownerRole)?.has(operation.permission),
      `${ownerRole} is missing declared write permission ${operation.permission}`)
    const method = operation.method.toUpperCase()
    const probeBody = operation.probeBody === undefined ? null : operation.probeBody
    const supportsJsonProbe = operation.probeable !== false && probeBody !== null
    for (const forbiddenRole of operation.forbiddenRoles || []) {
      const forbiddenClient = clients.get(forbiddenRole)
      assert.ok(forbiddenClient, `Missing forbidden client for ${forbiddenRole}`)
      assert.ok(!permissionsByRole.get(forbiddenRole)?.has(operation.permission),
        `${forbiddenRole} unexpectedly owns ${operation.permission} for ${operation.id}`)
      if (supportsJsonProbe) {
        const forbiddenProbe = await forbiddenClient.probe(resolveProbePath(apiPath(operation.path)), {
          method,
          body: probeBody
        })
        assert.equal(forbiddenProbe.status, 403, `${forbiddenRole} must be denied ${operation.id}`)
        assert.equal(forbiddenProbe.code, 'FORBIDDEN', `${forbiddenRole} denial code for ${operation.id}`)
      }
    }
  }
  console.log(JSON.stringify({
    result: 'ROLE NAVIGATION PASS',
    contractVersion: matrix.contractVersion,
    roles: results,
    permissionProbes: allOperations().filter(({ operation }) => operation.probeable !== false && operation.probeBody !== null)
      .reduce((count, { operation }) => count + (operation.forbiddenRoles || []).length, 0)
  }, null, 2))
}

main().catch(error => {
  console.error(`ROLE NAVIGATION FAIL: ${error.message}`)
  process.exitCode = 1
})
