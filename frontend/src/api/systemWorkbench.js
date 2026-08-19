import http from '../utils/http'

export function listSystemOperations(params) {
  return http.get('/system/operations', { params })
}

export function getSystemHealth() {
  return http.get('/system/health')
}
