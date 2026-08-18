<template>
  <main class="error-page">
    <i class="el-icon-lock" aria-hidden="true" />
    <h1>无权访问此页面</h1>
    <p>当前账号没有所需权限。如需处理该事项，请联系系统管理员。</p>
    <el-button type="primary" @click="$router.push('/dashboard')">返回治理概览</el-button>
  </main>
</template>

<script>
export default { name: 'ForbiddenView' }
</script>
