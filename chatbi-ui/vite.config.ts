import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  
  return {
    plugins: [react()],
    base: '/copilot/',
    server: {
      port: 3000,
      host: true
    },
    build: {
      outDir: 'dist'
    },
    define: {
      // 确保环境变量在构建时可用
      'import.meta.env.VITE_CHATBI_SERVER_ENDPOINT': JSON.stringify(env.VITE_CHATBI_SERVER_ENDPOINT)
    }
  }
})
