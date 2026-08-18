import http from '../utils/http'

export function getUserInsight() {
  return http.get('/insights/users')
}

export function getGridInsight() {
  return http.get('/insights/grids')
}

export function getResidentInsight() {
  return http.get('/insights/residents')
}

export function getEventInsight() {
  return http.get('/insights/events')
}

export function getTaskInsight() {
  return http.get('/insights/tasks')
}
