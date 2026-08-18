#!/usr/bin/env node

import { randomBytes } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const REQUIRED_NODE_MAJOR = 22
const nodeMajor = Number.parseInt(process.versions.node.split('.')[0], 10)
if (nodeMajor !== REQUIRED_NODE_MAJOR) {
  throw new Error(`seed-demo.mjs requires Node ${REQUIRED_NODE_MAJOR}.x, got ${process.versions.node}`)
}

function requiredEnv(name, { secret = false } = {}) {
  const value = process.env[name]
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return secret ? value : value.trim()
}

const confirmation = requiredEnv('DEMO_CONFIRM_ISOLATED')
if (confirmation !== 'YES') {
  throw new Error('DEMO_CONFIRM_ISOLATED must be exactly YES; only load a new disposable demo database')
}
const smokeConfirmation = requiredEnv('SMOKE_CONFIRM_ISOLATED')
if (smokeConfirmation !== 'YES') {
  throw new Error('SMOKE_CONFIRM_ISOLATED must be exactly YES before invoking runtime-smoke.mjs')
}

const baseUrl = requiredEnv('DEMO_BASE_URL')
const adminUsername = requiredEnv('DEMO_ADMIN_USERNAME')
const adminPassword = requiredEnv('DEMO_ADMIN_PASSWORD', { secret: true })
const runtimeSmoke = path.join(path.dirname(fileURLToPath(import.meta.url)), 'runtime-smoke.mjs')
const workerPassword = `${randomBytes(30).toString('base64url')}Aa1!`

const profiles = [
  { code: 'xingfu', categoryId: '4', label: '幸福里社区' },
  { code: 'heyuan', categoryId: '3', label: '和苑社区' },
  { code: 'qinghe', categoryId: '2', label: '清河社区' },
  { code: 'donghu', categoryId: '4', label: '东湖社区' }
]

for (const [index, profile] of profiles.entries()) {
  console.log(`[demo-data] ${index + 1}/${profiles.length} ${profile.label}`)
  const child = spawnSync(process.execPath, [runtimeSmoke], {
    env: {
      ...process.env,
      SMOKE_CONFIRM_ISOLATED: smokeConfirmation,
      SMOKE_BASE_URL: baseUrl,
      SMOKE_ADMIN_USERNAME: adminUsername,
      SMOKE_ADMIN_PASSWORD: adminPassword,
      SMOKE_WORKER_USERNAME: `demo-${profile.code}`,
      SMOKE_WORKER_PASSWORD: workerPassword,
      SMOKE_RESIDENT_USERNAME: `resident-${profile.code}`,
      SMOKE_EVENT_CATEGORY_ID: profile.categoryId,
      SMOKE_DEMO_PROFILE: profile.code
    },
    stdio: 'inherit'
  })

  if (child.error) {
    throw child.error
  }
  if (child.signal) {
    throw new Error(`${profile.label} loader terminated by ${child.signal}`)
  }
  if (child.status !== 0) {
    process.exit(child.status ?? 1)
  }
}

console.log(JSON.stringify({
  result: 'DEMO DATA PASS',
  profiles: profiles.length,
  expectedMinimums: {
    communities: 4,
    grids: 4,
    residents: 4,
    events: 16,
    tasks: 15
  }
}, null, 2))
