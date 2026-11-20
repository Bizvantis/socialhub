import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// In development the app talks to the API gateway on :8080 directly (CORS allows :5173).
// In production Nginx serves the built app under /app/ and proxies /api to the gateway,
// so everything is same-origin and VITE_API_BASE stays empty.
export default defineConfig(({ command }) => ({
  plugins: [react(), tailwindcss()],
  base: command === "build" ? "/app/" : "/",
  server: { port: 5173, strictPort: true },
  // hls.js (~600 kB) is loaded lazily only on pages that play video, so it never blocks first paint.
  build: { chunkSizeWarningLimit: 700 },
}));
