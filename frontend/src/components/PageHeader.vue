<template>
  <header class="page-heading">
    <div class="page-heading-copy">
      <div class="page-heading-meta" aria-hidden="true">
        <span class="page-kicker">{{ context.kicker }}</span>
        <span class="page-trace">{{ context.trace }}</span>
      </div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script>
export default {
  name: 'PageHeader',
  props: {
    title: { type: String, required: true },
    description: { type: String, required: true }
  },
  computed: {
    context() {
      const contexts = {
        dashboard: { kicker: '治理态势', trace: '社区 / 网格 / 事项' },
        'system-users': { kicker: '权限与组织', trace: '账号 / 角色 / 范围' },
        grids: { kicker: '空间责任', trace: '社区 / 网格 / 网格员' },
        residents: { kicker: '民生底册', trace: '家庭 / 居民 / 重点关注' },
        events: { kicker: '事项处置', trace: '上报 / 受理 / 派发' },
        tasks: { kicker: '执行闭环', trace: '接单 / 处置 / 复核' }
        , 'admin-home': { kicker: '管理驾驶舱', trace: '系统 / 权限 / 健康' }
        , 'community-home': { kicker: '社区治理中心', trace: '待办 / 服务 / 巡查' }
        , 'community-todo': { kicker: '社区待办', trace: '受理 / 处理 / 复核' }
        , 'community-service': { kicker: '社区服务', trace: '申请 / 处理 / 办结' }
        , 'community-patrol': { kicker: '社区巡查', trace: '计划 / 派发 / 完成' }
        , 'community-report': { kicker: '社区报表', trace: '范围 / 质量 / 闭环' }
        , 'grid-home': { kicker: '现场执行台', trace: '巡查 / 任务 / 上报' }
        , 'grid-patrol': { kicker: '我的巡查', trace: '计划 / 接单 / 结果' }
        , 'grid-event-report': { kicker: '现场上报', trace: '发现 / 记录 / 进入受理' }
        , 'grid-task': { kicker: '我的任务', trace: '接单 / 处置 / 提交' }
        , 'grid-map': { kicker: '责任地图', trace: '坐标 / 网格 / 范围' }
        , 'grid-history': { kicker: '工作历史', trace: '事件 / 任务 / 留痕' }
        , announcements: { kicker: '社区公告', trace: '草稿 / 发布 / 撤回' }
        , 'system-service-catalogs': { kicker: '服务目录', trace: '项目 / 说明 / 启停' }
        , 'system-operations': { kicker: '管理审计', trace: '操作 / 对象 / 结果' }
        , 'system-health': { kicker: '系统健康', trace: '数据库 / 迁移 / 一致性' }
        , 'resident-home': { kicker: '居民服务中心', trace: '本人 / 服务 / 进度' }
        , 'resident-report': { kicker: '事项上报', trace: '说明 / 附件 / 提交' }
        , 'resident-events': { kicker: '我的事项', trace: '上报 / 流转 / 办结' }
        , 'resident-profile': { kicker: '我的档案', trace: '身份 / 网格 / 家庭户' }
        , 'resident-service': { kicker: '服务申请', trace: '目录 / 申请 / 跟踪' }
        , 'resident-rating': { kicker: '服务评价', trace: '结果 / 体验 / 反馈' }
      }
      return contexts[this.$route.name] || { kicker: '治理工作台', trace: '职责 / 数据 / 行动' }
    }
  }
}
</script>
