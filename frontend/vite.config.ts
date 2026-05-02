import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(() => {
  const backendTarget = process.env.VITE_BACKEND_PROXY_TARGET || 'http://localhost:8080'
  const proxyTarget = {
    target: backendTarget,
    changeOrigin: true,
    configure(proxy) {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.removeHeader('origin')
      })
    },
  }

  return {
    plugins: [
      vue(),
      Components({
        dts: false,
        resolvers: [
          AntDesignVueResolver({
            importStyle: false,
          }),
        ],
      }),
    ],
    resolve: {
      alias: {
        '@': '/src',
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined
            }

            if (
              id.includes('/node_modules/vue/')
              || id.includes('/node_modules/vue-router/')
              || id.includes('/node_modules/pinia/')
            ) {
              return 'vue-core'
            }

            if (id.includes('/node_modules/lodash/') || id.includes('/node_modules/lodash-es/')) {
              return 'vendor-utils'
            }

            if (id.includes('/node_modules/@ant-design/icons-vue/')) {
              return 'antd-icons'
            }

            if (id.includes('/node_modules/echarts/') || id.includes('/node_modules/zrender/')) {
              return 'echarts-vendor'
            }

            return undefined
          },
        },
      },
      chunkSizeWarningLimit: 600,
    },
    server: {
      port: 5173,
      proxy: {
        '/api': proxyTarget,
        '/api-docs': proxyTarget,
        '/swagger-ui.html': proxyTarget,
        '/swagger-ui': proxyTarget,
      },
    },
  }
})
