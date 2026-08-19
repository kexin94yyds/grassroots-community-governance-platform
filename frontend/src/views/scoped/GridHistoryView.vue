<template>
  <section>
    <PageHeader title="网格工作历史" description="按责任范围回看事件和任务的真实流转记录，方便复盘而不是只看当前待办。" />
    <el-tabs v-model="activeTab" class="module-tabs">
      <el-tab-pane label="事件记录" name="events"><EventListView /></el-tab-pane>
      <el-tab-pane label="任务记录" name="tasks"><TaskListView /></el-tab-pane>
    </el-tabs>
  </section>
</template>

<script>
import PageHeader from '../../components/PageHeader.vue'
import EventListView from '../events/EventListView.vue'
import TaskListView from '../tasks/TaskListView.vue'

export default { name: 'GridHistoryView', components: { PageHeader, EventListView, TaskListView }, data() { return { activeTab: 'events' } } }
</script>
