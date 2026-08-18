import http from '../utils/http'

export function getOverview() {
  return http.get('/dashboard/overview')
}
