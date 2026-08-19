import http from '../utils/http'

function summary(path) {
  return http.get(path)
}

export function getAdminSummary() {
  return summary('/workbenches/admin/summary')
}

export function getCommunitySummary() {
  return summary('/workbenches/community/summary')
}

export function getGridSummary() {
  return summary('/workbenches/grid/summary')
}

export function getResidentSummary() {
  return summary('/workbenches/resident/summary')
}
