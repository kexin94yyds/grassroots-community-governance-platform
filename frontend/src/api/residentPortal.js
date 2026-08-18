import http from '../utils/http'
import { uploadTokenFor } from '../utils/uploadToken'

export function getResidentOverview() {
  return http.get('/resident-portal/overview')
}

export function reportResidentEvent(data) {
  return http.post('/resident-portal/events', data)
}

export function listResidentEventAttachments(eventId) {
  return http.get(`/resident-portal/events/${String(eventId)}/attachments`)
}

export function uploadResidentEventAttachment(eventId, file, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  data.append('requestToken', uploadTokenFor(file))
  return http.post(`/resident-portal/events/${String(eventId)}/attachments`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    timeout: 60000
  })
}

export function downloadResidentEventAttachment(eventId, attachmentId) {
  return http.get(`/resident-portal/events/${String(eventId)}/attachments/${String(attachmentId)}/content`, {
    responseType: 'blob',
    timeout: 60000
  })
}

export function deleteResidentEventAttachment(eventId, attachmentId) {
  return http.delete(`/resident-portal/events/${String(eventId)}/attachments/${String(attachmentId)}`)
}
