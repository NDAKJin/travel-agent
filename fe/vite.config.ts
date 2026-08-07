import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/doc.html": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/v3/api-docs": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
