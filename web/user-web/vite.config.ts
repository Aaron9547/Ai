import path from "node:path";
import { fileURLToPath } from "node:url";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite";
import { resolveViteAllowedHosts } from "../vite-allowed-hosts";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const allowedHosts = resolveViteAllowedHosts();

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
          if (id.includes("node_modules/three")) return "three";
          if (id.includes("node_modules/3d-force-graph")) return "force-graph-3d";
          if (id.includes("node_modules/force-graph")) return "force-graph";
          if (id.includes("node_modules/gsap")) return "gsap";
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
     * 花生壳 / 自定义域穿透：Host 为域名（非纯 IP）时 Vite 6 会 403。以 `.` 开头表示该后缀及子域。
     * 追加域名：`VITE_ADDITIONAL_ALLOWED_HOSTS` 或 `__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`；本地全放行：`VITE_ALLOWED_HOSTS_ALL=true`（勿提交）。
     */
    allowedHosts,
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
    allowedHosts,
  },
});
