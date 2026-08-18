import http from '../utils/http'
import { uploadTokenFor } from '../utils/uploadToken'

export function uploadEventAttachment(eventId, file, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  data.append('requestToken', uploadTokenFor(file))
  return http.post(`/events/${String(eventId)}/attachments`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    timeout: 60000
  })
}

export function listEventAttachments(eventId) {
  return http.get(`/events/${String(eventId)}/attachments`)
}

export function deleteEventAttachment(eventId, attachmentId) {
  return http.delete(`/events/${String(eventId)}/attachments/${String(attachmentId)}`)
}

export function downloadAuthorizedFile(fileId) {
  return http.get(`/files/${encodeURIComponent(String(fileId))}`, {
    responseType: 'blob',
    timeout: 60000
  })
}
