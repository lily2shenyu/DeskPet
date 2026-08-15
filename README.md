# 🐾 AI 桌宠（DeskPet）

基于 [AI-Live-Overflow](https://github.com/Vael-KY/AI-Live-Overflow) 架构实现的 **Android 悬浮窗 AI 陪伴桌宠**。

核心设计：**大脑和身体分离**
- 🧠 大脑 = 你原来的 AI（对话、记忆、人格全在那边，不变）
- 🐾 身体 = 这个悬浮窗小生物（渲染 + 感知，180×240 像素）

## 目录

```
DeskPet/
├── app/src/main/
│   ├── java/com/lili/pet/
│   │   ├── MainActivity.kt             入口：授权引导 + 启动/停止
│   │   ├── service/OverlayService.kt   核心：悬浮窗 + 手势 + 感知 + 通知碎念
│   │   └── sensor/
│   │       ├── UsageTracker.kt         前台 App 检测（3 秒轮询）
│   │       ├── ScreenshotObserver.kt   截图检测（FileObserver）
│   │       └── BatteryWatcher.kt       充电/低电量感知
│   └── assets/pet.html                 桌宠本体：SVG + 表情状态机 + 行为引擎
├── preview/pet.html                    浏览器预览版（手机直接打开就能看）
└── .github/workflows/build.yml         push 即出 APK，无需电脑
```

## 功能

| 模块 | 说明 |
|------|------|
| 悬浮窗 | 前台服务 + WindowManager + 透明 WebView，趴在所有 app 上 |
| 手势 | 单击/双击/长按/拖拽/甩飞（甩飞会自己爬回来），2 秒内连戳 3/5/8 次层层递进 |
| 感知 | 前台 App（抖音吃醋/淘宝掏钱包/学习通加油…）、截图摆 pose、充电/低电量 |
| 表达 | 气泡 5 种风格、自言自语（日常/黏人/深夜催睡）、通知栏每小时碎念一句 |
| 表情 | idle/happy/angry/sleepy/sad/love/dizzy 七种状态，SVG + CSS 动画 |

## 怎么跑起来（两种方式）

### 方式一：手机浏览器直接预览（30 秒）
把 `preview/pet.html` 发到手机，用浏览器打开。
能看到完整的桌宠动画，支持拖动、单击、双击、长按，还有深夜催睡等行为。

### 方式二：装成真正的悬浮窗桌宠（推荐）
**1. 用 GitHub Actions 自动构建（不用电脑）**
- 把这个文件夹推到一个新建的 GitHub 仓库（main 分支）
- 打开 Actions 标签页 → Build APK → 等 2 分钟
- 在 Artifacts 里下载 `app-debug` 解压得到 `app-debug.apk`

**2. 或者用 Android Studio 本地构建**
- Android Studio → Open → 选 `DeskPet` 文件夹
- 同步后 Run，或 Build → Build APK

**3. 安装后授权**
1. 打开 App → 点「授予悬浮窗权限」
2. 点「授予使用情况访问」（可选，用于感知你在用什么 app）
3. 点「启动桌宠」→ 它就从屏幕上钻出来了

> ⚠️ 华为/小米等 ROM 可能杀后台：请在电池设置里允许后台运行、加入电池白名单，否则桌宠会被系统回收。

## 定制它

所有台词都在两个地方，改完就是你们自己的桌宠：
- `app/src/main/assets/pet.html` → `CFG`（名字/称呼）+ `LINES`（所有台词）
- `OverlayService.kt` → 通知栏碎念词库 + App 反应映射

改完 `assets/pet.html` 后：GitHub Actions 会自动重新出包；本地则重新 Build 一次即可。

## 进阶：让 AI 接管身体（双向通信）

`pet.html` 暴露了 `window.petEngine` 接口，AI（在聊天 App 里）可以通过后端推状态：

```js
window.petEngine.say("想你了。", "pink");   // 让桌宠立刻开口
window.petEngine.setState("love", 3000);     // 换表情
window.petEngine.setHeat(85);                // 热度值联动
```

桌宠侧（安卓）把手势/前台 App/截图上报到后端（Supabase 或任意 REST），AI 就能读到
「栗栗刚才戳了我 8 次」「她在刷抖音」。参考原仓库 `docs/supabase-sync.md`。

## 协议

代码部分学习自 [AI-Live-Overflow](https://github.com/Vael-KY/AI-Live-Overflow)（CC BY-NC-SA 4.0）。
本项目同样以 **CC BY-NC-SA 4.0** 发布，不可商用，标注来源。
