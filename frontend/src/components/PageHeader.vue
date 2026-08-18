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
      }
      return contexts[this.$route.name] || { kicker: '治理工作台', trace: '职责 / 数据 / 行动' }
    }
  }
}
</script>
