<template>
  <main class="error-page standalone-error">
    <i class="el-icon-document-delete" aria-hidden="true" />
    <h1>页面不存在</h1>
    <p>访问地址可能已变更，请返回平台首页继续操作。</p>
    <el-button type="primary" @click="$router.push('/')">返回平台首页</el-button>
  </main>
</template>

<script>
export default { name: 'NotFoundView' }
</script>
