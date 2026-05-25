# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Toonflow-game-android-h5 is a mobile app combining:
- **H5 Frontend**: Vue 3 + TypeScript + Vite web app
- **Android Wrapper**: WebView-based native Android app

The H5 app communicates with `toonflow-game-app` backend on port **60002**.

## Commands

### H5 Development
```bash
npm install        # First run
npm run dev        # Dev server on port 5174 (--host for mobile access)
npm run build      # Build → dist/
npm run preview    # Preview production build
npm run type-check # vue-tsc validation
```

### Android Development
```bash
# Open in Android Studio, or use Gradle directly:
cd app
./gradlew assembleDebug   # Build debug APK
./gradlew assembleRelease # Build release APK
```

## Project Structure

```
├── src/                    # Vue H5 source code
├── dist/                  # H5 production build output
├── app/
│   ├── src/main/
│   │   ├── java/com/toonflow/game/
│   │   │   └── MainActivity.kt      # WebView wrapper
│   │   ├── res/
│   │   │   ├── drawable/            # App icons
│   │   │   └── values/              # strings, themes, colors
│   │   └── assets/
│   │       └── dist/               # Bundled H5 (copied from dist/)
│   └── build.gradle
├── build.gradle           # Project-level config
├── settings.gradle
├── gradle.properties
└── local.properties       # Android SDK path (C:\Users\q\AppData\Local\Android\Sdk)
```

## Android App Details

- **Package**: `com.toonflow.game`
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Load URL**: `file:///android_asset/dist/index.html`
- **WebView Features**:
  - JavaScript enabled
  - DOM storage enabled
  - Hardware acceleration
  - Media playback without user gesture
  - Cleartext traffic allowed (for local API access)

## H5 Architecture

### Scene-based Navigation

Single-page app with **no Vue Router**. Tab state in `store.state.activeTab` controls rendering in `App.vue`:
```
home | hall | create | history | my | settings | play
```

### State Management (`src/composables/useToonflowStore.ts`)

Singleton reactive store pattern:
- `state.baseUrl` — backend URL, defaults `http://127.0.0.1:60002`, persists to localStorage
- `state.activeTab` — current scene
- `state.messages[]` — session messages
- `state.sessionDetail` — full session state from backend
- `state.debugMode` — toggles between **debug mode** (章节调试) and **session mode** (正式游玩)

### Two Game Modes

1. **Debug Mode** (`debugMode=true`): Chapter debugging, no persistent session
   ```
   initDebug() → orchestrateDebug() → streamDebugLines() → saveDebugRevisitPoint()
   ```

2. **Session Mode** (`debugMode=false`): Full gameplay with DB-backed history
   ```
   startSession() → addMessage() → orchestrateSession() → streamlines() → messages[]
   ```

### Game Loop States

```
sending → orchestrated → streaming → generated → revealing → voicing
→ waiting_player (await user input) / waiting_next (auto-advance) → ended
```

## Code Standards

- Vue 3 `<script setup>` Composition API throughout
- Kotlin for Android native code
- camelCase for functions/variables, PascalCase for types/interfaces
- Strict TypeScript — no `any` without justification

## API Configuration

Default: `http://127.0.0.1:60002` (configurable in Settings page). Backend must be running for the app to function.

## Building APK

See [md/build.md](md/build.md) for detailed build instructions.

Quick script (macOS/Linux):
```bash
npm run build && rm -rf app/src/main/assets/dist && cp -r dist app/src/main/assets/
cd app && ./gradlew assembleDebug
```

## Parent Project

Backend: `toonflow-game-app/CLAUDE.md`
- Database: `db.sqlite` (tables: `t_gameSession`, `t_sessionMessage`, `t_storyWorld`, `t_storyChapter`)
- Mini-games: battle, mining, alchemy, fishing, research_skill, upgrade_equipment

## 不允许进行修改的文件说明
- review_xxx.md 文件
  是用户自己验证功能的文件，ai 不允许进行修改
  ai 可以新增或者修改 review_xxx.answer.md

## 不允许ai 修改的标注
文件第一行: @no_modify
或者 # @no_modify

# 系统环境配置
[system.yml](system/system.yml)

## 前端开发

前端代码在 `Toonflow-game-web` 仓库，开发时直接运行 `yarn dev` 即可查看效果，不需要在当前仓库执行 `yarn build`。
 