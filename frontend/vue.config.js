const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    port: Number(process.env.FRONTEND_PORT || 5173),
    proxy: {
      '^/api': {
        target: process.env.DEV_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
