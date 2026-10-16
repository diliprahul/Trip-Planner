
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    allowedHosts: ['culture-shiftless-germless.ngrok-free.dev'],
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
    },
  },
})
