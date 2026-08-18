import http from '../utils/http'

export function listGrids(params) {
  return http.get('/grids', { params })
}

export function listCommunities() {
  return http.get('/grids/communities')
}

export function listWorkerOptions() {
  return http.get('/grids/worker-options')
}

export function listCommunityStaffOptions() {
  return http.get('/grids/community-staff-options')
}

export function getGrid(id) {
  return http.get(`/grids/${String(id)}`)
}

export function createGrid(data) {
  return http.post('/grids', data)
}

export function updateGrid(id, data) {
  return http.put(`/grids/${String(id)}`, data)
}

export function assignWorkers(id, data) {
  return http.put(`/grids/${String(id)}/assignments`, data)
}

export function updateGridStatus(id, status, version) {
  return http.patch(`/grids/${String(id)}/status`, { status, version })
}
