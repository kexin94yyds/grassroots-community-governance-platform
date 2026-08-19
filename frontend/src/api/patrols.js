import http from '../utils/http'

export function listPatrolPlans(params) {
  return http.get('/patrol-plans', { params })
}

export function listMyPatrolPlans(params) {
  return http.get('/patrol-plans/mine', { params })
}

export function createPatrolPlan(data) {
  return http.post('/patrol-plans', data)
}

export function cancelPatrolPlan(id, version, reason) {
  return http.post(`/patrol-plans/${String(id)}/cancel`, { version, reason })
}
