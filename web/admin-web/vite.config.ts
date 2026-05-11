import path from "node:path";
import { fileURLToPath } from "node:url";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { defineConfig } from "vite";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ["vue", "vue-router"],
      resolvers: [ElementPlusResolver()],
      dts: "src/auto-imports.d.ts",
    }),
  ],
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
  },
  server: {
    host: true,
    /** 穿透域名放行，见 `web/user-web/vite.config.ts` 同条注释 */
    allowedHosts: [".vicp.fun", ".vicp.cc"],
    port: 5174,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      /** 与 axios 相对路径 /open/v1/... 对齐；未设置 VITE_API_BASE 时登录等须走代理 */
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
