import http from '../utils/http'

export function listUsers(params) {
  return http.get('/system/users', { params })
}

export function getUser(id) {
  return http.get(`/system/users/${String(id)}`)
}

export function listRoles(params) {
  return http.get('/system/roles', { params })
}

export function listMenus() {
  return http.get('/system/menus')
}

export function updateRole(code, data) {
  return http.put(`/system/roles/${encodeURIComponent(String(code))}`, data)
}

export function updateMenu(id, data) {
  return http.put(`/system/menus/${String(id)}`, data)
}

export function createUser(data) {
  return http.post('/system/users', data)
}

export function updateUser(id, data) {
  return http.put(`/system/users/${String(id)}`, data)
}

export function updateUserStatus(id, enabled, version) {
  return http.patch(`/system/users/${String(id)}/status`, { enabled, version })
}

export function resetUserPassword(id, temporaryPassword, version) {
  return http.post(`/system/users/${String(id)}/password-reset`, { temporaryPassword, version })
}

export function assignRoles(id, roleCodes, version) {
  return http.put(`/system/users/${String(id)}/roles`, { roleCodes, version })
}

export function reviewRegistration(id, data) {
  return http.post(`/system/users/${String(id)}/registration-review`, data)
}

export function listSystemEventCategories() {
  return http.get('/system/event-categories')
}

export function createEventCategory(data) {
  return http.post('/system/event-categories', data)
}

export function updateEventCategory(id, data) {
  return http.put(`/system/event-categories/${String(id)}`, data)
}
