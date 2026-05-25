# 构建说明

## 1. 构建 H5

```bash
cd {Toonflow-game-web}
yarn build
```

输出到 `dist/` 目录

## 2. 复制到 Android Assets

```bash
# 删除旧资源
rm -rf app/src/main/assets/dist

# 复制新构建
cp -r {Toonflow-game-web}/dist app/src/main/assets/
```
效果是
app/src/main/assets/dist/index.html

## 3. 构建 APK

### Android Studio
打开项目 → Build → Make Project (或点击 Run ▶️)

### 命令行
```bash
cd app
./gradlew assembleDebug
```

APK 输出位置: `app/build/outputs/apk/debug/app-debug.apk`

## 一键构建脚本

Windows (PowerShell):
```powershell
npm run build; Remove-Item -Recurse -Force app/src/main/assets/dist; Copy-Item -Recurse dist app/src/main/assets/
```

macOS / Linux:
```bash
npm run build && rm -rf app/src/main/assets/dist && cp -r dist app/src/main/assets/
```

## 注意事项

- 确保 `local.properties` 中 sdk.dir 指向正确的 Android SDK 路径
- 首次打开 Android Studio 需要等待 Gradle 同步完成
- Release 构建需要配置签名密钥
