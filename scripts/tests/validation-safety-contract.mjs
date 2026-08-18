#!/usr/bin/env node

import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const REQUIRED_NODE_MAJOR = 22
const nodeMajor = Number.parseInt(process.versions.node.split('.')[0], 10)
if (nodeMajor !== REQUIRED_NODE_MAJOR) {
  throw new Error(`validation-safety-contract.mjs requires Node ${REQUIRED_NODE_MAJOR}.x`)
}

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const runtimeSmoke = path.join(projectRoot, 'scripts', 'runtime-smoke.mjs')
const smokeApi = path.join(projectRoot, 'scripts', 'smoke-api.sh')
const uiE2e = path.join(projectRoot, 'scripts', 'ui-e2e.py')
const seedDemo = path.join(projectRoot, 'scripts', 'seed-demo.mjs')
const thesisFigures = path.join(projectRoot, 'scripts', 'generate-thesis-figures.sh')
const pipeline = path.join(projectRoot, 'scripts', 'validation-pipeline.sh')
const uvBin = process.env.VALIDATION_UV_BIN || 'uv'
const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'community-governance-safety-contract-'))

function syntheticPassword() {
  return `${randomBytes(20).toString('base64url')}Aa1!`
}

function cleanEnvironment(overrides = {}) {
  const environment = { ...process.env }
  for (const name of Object.keys(environment)) {
    if (name.startsWith('SMOKE_') || name.startsWith('E2E_') || name.startsWith('PIPELINE_')) delete environment[name]
  }
  return { ...environment, ...overrides }
}

function run(command, args, environment) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: projectRoot,
      env: environment,
      stdio: ['ignore', 'pipe', 'pipe']
    })
    let stdout = ''
    let stderr = ''
    child.stdout.setEncoding('utf8')
    child.stderr.setEncoding('utf8')
    child.stdout.on('data', chunk => { stdout += chunk })
    child.stderr.on('data', chunk => { stderr += chunk })
    child.on('error', reject)
    child.on('close', (code, signal) => resolve({ code, signal, stdout, stderr }))
  })
}

let requestCount = 0
const server = http.createServer((_request, response) => {
  requestCount += 1
  response.writeHead(503, { 'Content-Type': 'application/json' })
  response.end('{"code":"UNEXPECTED_REQUEST"}')
})

await new Promise((resolve, reject) => {
  server.once('error', reject)
  server.listen(0, '127.0.0.1', resolve)
})
const address = server.address()
const localBaseUrl = `http://127.0.0.1:${address.port}`
const adminPassword = syntheticPassword()
const workerPassword = syntheticPassword()
const residentPassword = syntheticPassword()

async function expectRejectedWithoutRequest(label, command, args, environment) {
  const before = requestCount
  const result = await run(command, args, environment)
  await new Promise(resolve => setTimeout(resolve, 30))
  assert.notEqual(result.code, 0, `${label}: expected a non-zero exit`)
  assert.equal(requestCount, before, `${label}: emitted an HTTP request before failing closed`)
  console.log(`PASS ${label}: exit=${result.code ?? result.signal}, http_requests=0`)
}

try {
  const smokeBase = {
    SMOKE_BASE_URL: localBaseUrl,
    SMOKE_ADMIN_USERNAME: 'safety-admin',
    SMOKE_ADMIN_PASSWORD: adminPassword,
    SMOKE_WORKER_USERNAME: 'safety-worker',
    SMOKE_WORKER_PASSWORD: workerPassword
  }
  await expectRejectedWithoutRequest(
    'runtime smoke missing isolation confirmation',
    process.execPath,
    [runtimeSmoke],
    cleanEnvironment(smokeBase)
  )
  await expectRejectedWithoutRequest(
    'runtime smoke wrong isolation confirmation',
    process.execPath,
    [runtimeSmoke],
    cleanEnvironment({ ...smokeBase, SMOKE_CONFIRM_ISOLATED: 'yes' })
  )
  await expectRejectedWithoutRequest(
    'runtime smoke missing explicit worker password',
    process.execPath,
    [runtimeSmoke],
    cleanEnvironment({ ...smokeBase, SMOKE_CONFIRM_ISOLATED: 'YES', SMOKE_WORKER_PASSWORD: '' })
  )
  await expectRejectedWithoutRequest(
    'runtime smoke remote target lacks double authorization',
    process.execPath,
    [runtimeSmoke],
    cleanEnvironment({ ...smokeBase, SMOKE_CONFIRM_ISOLATED: 'YES', SMOKE_BASE_URL: 'http://192.0.2.1:9' })
  )
  await expectRejectedWithoutRequest(
    'runtime smoke remote authorization is bound to the exact host',
    process.execPath,
    [runtimeSmoke],
    cleanEnvironment({
      ...smokeBase,
      SMOKE_CONFIRM_ISOLATED: 'YES',
      SMOKE_BASE_URL: 'http://192.0.2.1:9',
      SMOKE_ALLOW_REMOTE_TARGET: 'YES',
      SMOKE_CONFIRM_REMOTE_DISPOSABLE: 'YES',
      SMOKE_ALLOWED_REMOTE_HOST: '198.51.100.1'
    })
  )

  const shellSmokeBase = {
    SMOKE_BASE_URL: localBaseUrl,
    SMOKE_ADMIN_USERNAME: 'safety-admin',
    SMOKE_ADMIN_PASSWORD: adminPassword,
    SMOKE_USER_PASSWORD: workerPassword
  }
  await expectRejectedWithoutRequest(
    'shell smoke missing isolation confirmation',
    'bash',
    [smokeApi],
    cleanEnvironment(shellSmokeBase)
  )
  await expectRejectedWithoutRequest(
    'shell smoke wrong isolation confirmation',
    'bash',
    [smokeApi],
    cleanEnvironment({ ...shellSmokeBase, SMOKE_CONFIRM_ISOLATED: 'yes' })
  )
  await expectRejectedWithoutRequest(
    'shell smoke missing explicit worker password',
    'bash',
    [smokeApi],
    cleanEnvironment({ ...shellSmokeBase, SMOKE_CONFIRM_ISOLATED: 'YES', SMOKE_USER_PASSWORD: '' })
  )
  await expectRejectedWithoutRequest(
    'shell smoke remote target lacks double authorization',
    'bash',
    [smokeApi],
    cleanEnvironment({ ...shellSmokeBase, SMOKE_CONFIRM_ISOLATED: 'YES', SMOKE_BASE_URL: 'http://192.0.2.1:9' })
  )
  await expectRejectedWithoutRequest(
    'shell smoke remote authorization is bound to the exact host',
    'bash',
    [smokeApi],
    cleanEnvironment({
      ...shellSmokeBase,
      SMOKE_CONFIRM_ISOLATED: 'YES',
      SMOKE_BASE_URL: 'http://192.0.2.1:9',
      SMOKE_ALLOW_REMOTE_TARGET: 'YES',
      SMOKE_CONFIRM_REMOTE_DISPOSABLE: 'YES',
      SMOKE_ALLOWED_REMOTE_HOST: '198.51.100.1'
    })
  )

  const e2eBase = {
    E2E_BASE_URL: localBaseUrl,
    E2E_USERNAME: 'safety-admin',
    E2E_PASSWORD: adminPassword,
    E2E_RESIDENT_USERNAME: 'safety-resident',
    E2E_RESIDENT_PASSWORD: residentPassword,
    E2E_ARTIFACT_DIR: path.join(temporaryRoot, 'e2e')
  }
  await expectRejectedWithoutRequest(
    'browser E2E missing isolation confirmation',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment(e2eBase)
  )
  await expectRejectedWithoutRequest(
    'browser E2E wrong isolation confirmation',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment({ ...e2eBase, E2E_CONFIRM_ISOLATED: 'yes' })
  )
  await expectRejectedWithoutRequest(
    'browser E2E missing explicit password',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment({ ...e2eBase, E2E_CONFIRM_ISOLATED: 'YES', E2E_PASSWORD: '' })
  )
  await expectRejectedWithoutRequest(
    'browser E2E remote target lacks double authorization',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment({ ...e2eBase, E2E_CONFIRM_ISOLATED: 'YES', E2E_BASE_URL: 'http://192.0.2.1:9' })
  )
  await expectRejectedWithoutRequest(
    'browser E2E remote authorization is bound to the exact host',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment({
      ...e2eBase,
      E2E_CONFIRM_ISOLATED: 'YES',
      E2E_BASE_URL: 'http://192.0.2.1:9',
      E2E_ALLOW_REMOTE_TARGET: 'YES',
      E2E_CONFIRM_REMOTE_DISPOSABLE: 'YES',
      E2E_ALLOWED_REMOTE_HOST: '198.51.100.1'
    })
  )
  await expectRejectedWithoutRequest(
    'browser E2E rejects broad artifact directory',
    uvBin,
    ['run', '--script', uiE2e],
    cleanEnvironment({ ...e2eBase, E2E_CONFIRM_ISOLATED: 'YES', E2E_ARTIFACT_DIR: os.tmpdir() })
  )

  let redirectedTargetRequests = 0
  const redirectedTarget = http.createServer((_request, response) => {
    redirectedTargetRequests += 1
    response.writeHead(503)
    response.end()
  })
  await new Promise((resolve, reject) => {
    redirectedTarget.once('error', reject)
    redirectedTarget.listen(0, '127.0.0.1', resolve)
  })
  const redirectedAddress = redirectedTarget.address()
  const redirector = http.createServer((_request, response) => {
    response.writeHead(302, { Location: `http://127.0.0.1:${redirectedAddress.port}/escaped` })
    response.end()
  })
  await new Promise((resolve, reject) => {
    redirector.once('error', reject)
    redirector.listen(0, '127.0.0.1', resolve)
  })
  const redirectorAddress = redirector.address()
  try {
    const result = await run(
      uvBin,
      ['run', '--script', uiE2e],
      cleanEnvironment({
        ...e2eBase,
        E2E_CONFIRM_ISOLATED: 'YES',
        E2E_BASE_URL: `http://127.0.0.1:${redirectorAddress.port}`,
        E2E_ARTIFACT_DIR: path.join(temporaryRoot, 'redirect-e2e')
      })
    )
    assert.notEqual(result.code, 0, 'browser E2E cross-origin redirect: expected a non-zero exit')
    assert.equal(redirectedTargetRequests, 0, 'browser E2E followed a redirect outside the approved origin')
    console.log('PASS browser E2E blocks cross-origin redirects: redirected_target_requests=0')
  } finally {
    await new Promise(resolve => redirector.close(resolve))
    await new Promise(resolve => redirectedTarget.close(resolve))
  }

  const pipelineArtifactRoot = path.join(temporaryRoot, 'pipeline-remote-rejection')
  const pipelineResult = await run(
    'bash',
    [pipeline],
    cleanEnvironment({
      PIPELINE_DB_HOST: '192.0.2.1',
      PIPELINE_ALLOW_REMOTE_DB: '1',
      PIPELINE_CONFIRM_REMOTE_DISPOSABLE: 'YES',
      PIPELINE_ALLOWED_REMOTE_DB_HOST: '198.51.100.1',
      PIPELINE_ARTIFACT_ROOT: pipelineArtifactRoot
    })
  )
  assert.notEqual(pipelineResult.code, 0, 'pipeline remote authorization mismatch must fail closed')
  assert.match(pipelineResult.stderr, /PIPELINE_ALLOWED_REMOTE_DB_HOST/)
  console.log('PASS pipeline remote authorization is bound to the exact host')

  const runtimeSource = fs.readFileSync(runtimeSmoke, 'utf8')
  const shellSource = fs.readFileSync(smokeApi, 'utf8')
  const uiSource = fs.readFileSync(uiE2e, 'utf8')
  const seedSource = fs.readFileSync(seedDemo, 'utf8')
  const thesisSource = fs.readFileSync(thesisFigures, 'utf8')
  const pipelineSource = fs.readFileSync(pipeline, 'utf8')
  assert.ok(!runtimeSource.includes('SmokeUser-2026!') && !shellSource.includes('SmokeUser-2026!'))
  assert.ok(shellSource.includes('--data-binary @-') && !shellSource.includes('--data "${payload}"'))
  assert.ok(!uiSource.includes('resident-sensitive-view.png'))
  assert.ok(!uiSource.includes('failure.png'))
  assert.ok(seedSource.includes('SMOKE_CONFIRM_ISOLATED: smokeConfirmation'))
  assert.ok(thesisSource.includes('SMOKE_CONFIRM_ISOLATED=YES'))
  assert.ok(pipelineSource.includes('SPRING_FLYWAY_USER="${migration_username}"'))
  assert.ok(pipelineSource.includes('DB_USERNAME="${runtime_username}"'))
  assert.ok(pipelineSource.includes('unset PIPELINE_DB_ADMIN_USERNAME PIPELINE_DB_ADMIN_PASSWORD'))
  assert.ok(!pipelineSource.includes('--execute="CREATE USER'))
  assert.ok(pipelineSource.includes('find "${attachment_dir}" -depth -delete'))
  assert.ok(pipelineSource.includes('["tesseract", str(path), "stdout", "--psm", "6"]'))
  assert.ok(!pipelineSource.includes('DB_USERNAME="${db_admin_username}"'))
  console.log('PASS static sensitive-artifact and least-privilege contracts')
} finally {
  await new Promise(resolve => server.close(resolve))
  fs.rmSync(temporaryRoot, { recursive: true, force: true })
}

console.log(`VALIDATION SAFETY CONTRACT PASS (${requestCount} unexpected HTTP requests)`)
