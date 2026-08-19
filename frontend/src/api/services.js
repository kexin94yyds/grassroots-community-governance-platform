import http from '../utils/http'

export function listServiceCatalogs(params) {
  return http.get('/service-catalogs', { params })
}

export function listSystemServiceCatalogs(params) {
  return http.get('/system/service-catalogs', { params })
}

export function createServiceCatalog(data) {
  return http.post('/system/service-catalogs', data)
}

export function updateServiceCatalog(id, data) {
  return http.put(`/system/service-catalogs/${String(id)}`, data)
}

export function listServiceApplications(params) {
  return http.get('/service-applications', { params })
}

export function getServiceApplication(id) {
  return http.get(`/service-applications/${String(id)}`)
}

export function listServiceApplicationFlows(id) {
  return http.get(`/service-applications/${String(id)}/flows`)
}

export function acceptServiceApplication(id, version, remark) {
  return http.post(`/service-applications/${String(id)}/accept`, { version, remark })
}

export function startServiceApplication(id, version, remark) {
  return http.post(`/service-applications/${String(id)}/start`, { version, remark })
}

export function completeServiceApplication(id, data) {
  return http.post(`/service-applications/${String(id)}/complete`, data)
}

export function rejectServiceApplication(id, version, reason) {
  return http.post(`/service-applications/${String(id)}/reject`, { version, remark: reason })
}

export function listResidentServiceApplications(params) {
  return http.get('/resident-portal/service-applications', { params })
}

export function applyResidentService(data) {
  return http.post('/resident-portal/service-applications', data)
}

export function cancelResidentServiceApplication(id, version, reason) {
  return http.post(`/resident-portal/service-applications/${String(id)}/cancel`, { version, remark: reason })
}

export function rateResidentServiceApplication(id, data) {
  return http.post(`/resident-portal/service-applications/${String(id)}/rate`, data)
}
