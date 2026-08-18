import http from '../utils/http'
import { uploadTokenFor } from '../utils/uploadToken'

export function listTasks(params) {
  return http.get('/tasks', { params })
}

export function createTask(data) {
  return http.post('/tasks', data)
}

export function getTask(id) {
  return http.get(`/tasks/${String(id)}`)
}

export function acceptTask(id, version) {
  return http.post(`/tasks/${String(id)}/accept`, { version })
}

export function submitTaskForReview(id, data) {
  return http.post(`/tasks/${String(id)}/submit-review`, data)
}

export function reviewTask(id, data) {
  return http.post(`/tasks/${String(id)}/review`, data)
}

export function cancelTask(id, version, reason) {
  return http.post(`/tasks/${String(id)}/cancel`, { version, reason })
}

export function listTaskFlows(id) {
  return http.get(`/tasks/${String(id)}/flows`)
}

export function listTaskAttachments(taskId) {
  return http.get(`/tasks/${String(taskId)}/attachments`)
}

export function uploadTaskAttachment(taskId, file, onUploadProgress) {
  const data = new FormData()
  data.append('file', file)
  data.append('requestToken', uploadTokenFor(file))
  return http.post(`/tasks/${String(taskId)}/attachments`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    timeout: 60000
  })
}

export function downloadTaskAttachment(taskId, attachmentId) {
  return http.get(`/tasks/${String(taskId)}/attachments/${String(attachmentId)}/content`, {
    responseType: 'blob',
    timeout: 60000
  })
}

export function deleteTaskAttachment(taskId, attachmentId) {
  return http.delete(`/tasks/${String(taskId)}/attachments/${String(attachmentId)}`)
}
