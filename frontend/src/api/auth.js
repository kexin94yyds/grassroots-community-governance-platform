import http from '../utils/http'

export function initializeCsrf() {
  return http.get('/auth/csrf')
}

export function login(credentials) {
  return http.post('/auth/login', credentials)
}

export function register(data) {
  return http.post('/auth/register', data)
}

export function getCurrentUser() {
  return http.get('/auth/me')
}

export function changePassword(data) {
  return http.post('/auth/password', data)
}

export function getNavigation() {
  return http.get('/auth/navigation')
}

export function logout() {
  return http.post('/auth/logout')
}
