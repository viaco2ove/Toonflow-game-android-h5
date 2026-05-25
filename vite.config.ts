import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  base: "./",
  plugins: [vue()],
  server: {
    port: 5174, // H5专用端口，避免和 web 项目冲突
    host: true,
    watch: {
      usePolling: true,
      interval: 200,
    },
  },
  build: {
    target: "es2015",
    rollupOptions: {
      output: {
        manualChunks: undefined,
      },
    },
  },
});
