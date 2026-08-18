import axios from 'axios'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = options.code || 'UNKNOWN'
    this.status = options.status || 0
    this.details = options.details
  }
}

const http = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '/api',
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
  headers: {
    Accept: 'application/json'
  }
})

http.interceptors.response.use(
  response => {
    if (response.config && response.config.responseType === 'blob') return response
    const envelope = response.data
    if (!envelope || typeof envelope !== 'object' || !Object.prototype.hasOwnProperty.call(envelope, 'code')) {
      throw new ApiError('服务端响应格式不正确', {
        code: 'INVALID_RESPONSE',
        status: response.status,
        details: envelope
      })
    }
    if (envelope.code !== 'OK') {
      throw new ApiError(envelope.message || '业务请求失败', {
        code: envelope.code,
        status: response.status,
        details: envelope.data
      })
    }
    return envelope.data
  },
  async error => {
    const status = error.response ? error.response.status : 0
    let envelope = error.response && error.response.data
    if (typeof Blob !== 'undefined' && envelope instanceof Blob) {
      try {
        envelope = JSON.parse(await envelope.text())
      } catch (ignored) {
        envelope = null
      }
    }
    const normalized = new ApiError(
      (envelope && envelope.message) || (status === 0 ? '网络连接失败，请稍后重试' : '请求失败'),
      {
        code: (envelope && envelope.code) || (status ? `HTTP_${status}` : 'NETWORK_ERROR'),
        status,
        details: envelope && envelope.data
      }
    )

    if (status === 401 && typeof unauthorizedHandler === 'function') {
      unauthorizedHandler()
    }
    return Promise.reject(normalized)
  }
)

export default http
