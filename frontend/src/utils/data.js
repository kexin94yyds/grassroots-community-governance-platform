export function asPage(payload) {
  const source = payload || {}
  return {
    items: Array.isArray(source.items) ? source.items : [],
    total: Number(source.total) || 0,
    page: Number(source.page) || 1,
    size: Number(source.size) || 20
  }
}

export function errorMessage(error) {
  return (error && error.message) || '加载失败，请稍后重试'
}

export function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}
