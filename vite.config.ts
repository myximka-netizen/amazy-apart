import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  server: {
    host: "::",
    port: 8080,
  },
  plugins: [react(), mode === "development" && componentTagger()].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  ssr: { noExternal: ['react-helmet-async'] },
  assetsInclude: ["**/*.avifs"],
  
  // Настройки для правильного деплоя
  build: {
    outDir: 'dist',
    manifest: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          ui: ['@radix-ui/react-dialog', '@radix-ui/react-dropdown-menu'],
        }
      }
    },
    chunkSizeWarningLimit: 1000
  },
  
  // Базовый путь для статических ресурсов (удалено для правильной индексации)
  base: '/',
  
  // Превью настройки
  preview: {
    port: 8080,
    host: true
  }
}));
