import http from '../utils/http'

export function listEvents(params) {
  return http.get('/events', { params })
}

export function listEventCategories() {
  return http.get('/events/categories')
}

export function getEvent(id) {
  return http.get(`/events/${String(id)}`)
}

export function reportEvent(data) {
  return http.post('/events', data)
}

export function acceptEvent(id, data) {
  return http.post(`/events/${String(id)}/accept`, data)
}

export function rejectEvent(id, version, reason) {
  return http.post(`/events/${String(id)}/reject`, { version, reason })
}

export function assignEvent(id, data) {
  return http.post(`/events/${String(id)}/assign`, data)
}

export function cancelEvent(id, version, reason) {
  return http.post(`/events/${String(id)}/cancel`, { version, reason })
}

export function listEventFlows(id) {
  return http.get(`/events/${String(id)}/flows`)
}
