import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// In dev the browser talks to /api same-origin and Vite proxies it to the
// Spring backend, so CORS never enters the picture (see docs/ARCHITECTURE §6.5).
// Set VITE_API_URL=/api/v1 in prod too - nginx rewrites the rest, no rebuild.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});