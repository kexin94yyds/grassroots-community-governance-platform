import http from '../utils/http'

export function listResidents(params) {
  return http.get('/residents', { params })
}

export function getResident(id) {
  return http.get(`/residents/${String(id)}`)
}

export function createResident(data) {
  return http.post('/residents', data)
}

export function updateResident(id, data) {
  return http.put(`/residents/${String(id)}`, data)
}

export function updateResidentStatus(id, status, version) {
  return http.patch(`/residents/${String(id)}/status`, { status, version })
}

export function searchResidentsBySensitiveValue(data) {
  return http.post('/residents/sensitive-search', data)
}

export function viewResidentSensitiveData(id, purpose) {
  return http.post(`/residents/${String(id)}/sensitive-view`, { purpose })
}

export function listSensitiveAccessLogs(params) {
  return http.get('/residents/sensitive-access-logs', { params })
}

export function listHouseholds(params) {
  return http.get('/households', { params })
}

export function getHousehold(id) {
  return http.get(`/households/${String(id)}`)
}

export function createHousehold(data) {
  return http.post('/households', data)
}

export function updateHousehold(id, data) {
  return http.put(`/households/${String(id)}`, data)
}

export function updateHouseholdStatus(id, status, version) {
  return http.patch(`/households/${String(id)}/status`, { status, version })
}
