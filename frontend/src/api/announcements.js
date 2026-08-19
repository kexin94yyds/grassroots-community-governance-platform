import http from '../utils/http'

export function listAnnouncements(params) {
  return http.get('/announcements', { params })
}

export function createAnnouncement(data) {
  return http.post('/announcements', data)
}

export function updateAnnouncement(id, data) {
  return http.put(`/announcements/${String(id)}`, data)
}

export function publishAnnouncement(id, version) {
  return http.post(`/announcements/${String(id)}/publish`, { version })
}

export function withdrawAnnouncement(id, version, reason) {
  return http.post(`/announcements/${String(id)}/withdraw`, { version, reason })
}
