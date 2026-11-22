import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Cau hinh Vite cho giao dien CodeHub.
// - npm run dev   : chay dev server, moi request /api duoc chuyen sang may chu
//                   Java o cong 8080 (nho vay khong can bat CORS).
// - npm run build : xuat file tinh vao frontend/dist, chinh la thu muc ma
//                   ApiServer.java phuc vu khi chay che do --serve.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: false,
      },
    },
  },
});
