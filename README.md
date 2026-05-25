# Toonflow-game-android-h5

Toonflow AI 剧场的 H5 前端 + Android WebView 封装

## 项目结构

```
├── src/                    # Vue H5 源码
├── dist/                   # H5 构建产物
├── app/                    # Android Studio 项目
│   └── src/main/assets/dist/   # 打包的 H5 资源
└── md/build.md            # 构建说明
```

## H5 开发

```bash
npm install   # 首次运行
npm run dev   # 启动开发服务器 (端口 5174)
npm run build # 构建生产版本
```

## Android APK 构建

详见 [md/build.md](md/build.md)

```bash
# 一键构建
npm run build && cp -r dist app/src/main/assets/
cd app && ./gradlew assembleDebug
```

## 技术栈

- **前端**: Vue 3 + TypeScript + Vite
- **移动端**: Android WebView
- **后端**: `toonflow-game-app` (默认 `http://127.0.0.1:60002`)

## API 配置

在设置页面配置后端地址，默认 `http://127.0.0.1:60002`