import path from "node:path";
import { fileURLToPath } from "node:url";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ["vue", "vue-router", { "vue-i18n": ["useI18n"] }],
      resolvers: [ElementPlusResolver()],
      dts: "src/auto-imports.d.ts",
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: "src/components.d.ts",
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules/element-plus")) return "element-plus";
          if (id.includes("node_modules/markdown-it") || id.includes("node_modules/dompurify")) {
            return "markdown";
          }
          if (id.includes("node_modules/prismjs")) {
            return "prism";
          }
        },
      },
    },
  },
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
  },
  server: {
    host: true,
    /**
     * 花生壳等穿透：Host 为域名（非纯 IP）时 Vite 6 会 403。以 `.` 开头表示该后缀及子域（如 *.vicp.fun）。
     * 其它域名可设环境变量 `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`（见 Vite server.allowedHosts），或本地临时改为 `true`（勿提交）。
     */
    allowedHosts: [".vicp.fun", ".vicp.cc"],
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/open": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
  preview: {
    allowedHosts: [".vicp.fun", ".vicp.cc"],
  },
});
