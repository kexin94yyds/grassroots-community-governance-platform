export const EVENT_STATUS = {
  REPORTED: '待受理',
  ACCEPTED: '已受理',
  ASSIGNED: '已派发',
  PROCESSING: '处理中',
  PENDING_REVIEW: '待复核',
  CLOSED: '已办结',
  REJECTED: '已驳回',
  CANCELLED: '已撤销'
}

export const TASK_STATUS = {
  PENDING_ACCEPT: '待接单',
  PROCESSING: '处理中',
  PENDING_REVIEW: '待复核',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

export const STATUS_TAG_TYPE = {
  REPORTED: 'warning',
  ACCEPTED: '',
  ASSIGNED: '',
  PENDING_ACCEPT: 'warning',
  PROCESSING: '',
  PENDING_REVIEW: 'warning',
  CLOSED: 'success',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info'
}

export const ANNOUNCEMENT_STATUS = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  WITHDRAWN: '已撤回'
}

export const SERVICE_APPLICATION_STATUS = {
  SUBMITTED: '待受理',
  ACCEPTED: '已受理',
  PROCESSING: '处理中',
  COMPLETED: '已办结',
  REJECTED: '已驳回',
  CANCELLED: '已撤回'
}

export const PATROL_PLAN_STATUS = {
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}
