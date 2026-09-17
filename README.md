# kiftd 前后端分离版

基于原版 kiftd 能力重写的网盘系统：Spring Boot + PostgreSQL 后端，Vite + Vue3 + TypeScript 前端。

完整说明见：[系统需求 · 设计 · 实现](docs/系统需求设计与实现.md)

## 目录

- `kiftd/` 原版参考包（勿改）
- `kiftd-backend/` Spring Boot API
- `kiftd-front/` Vue3 前端
- `docs/` 需求 / 设计 / 实现文档
- `deploy/` **Docker 生产部署**（postgres + jar + nginx，推荐布局 `/opt/apps/kiftd`）
- `docker-compose.yml` 本地开发用 PostgreSQL（仅数据库）

生产部署请见：[deploy/README.md](deploy/README.md)（Ubuntu + docker compose 三服务）。

## 环境要求

- JDK 21+
- Gradle 8.14（本机：`D:\develop\env\gradles\8.14`）
- Node.js 20+
- （可选）PostgreSQL 16+ / Docker
- （可选）FFmpeg：视频转码预览

默认开启 **嵌入式 PostgreSQL**（`kiftd.embedded-pg=true`），无需本机安装数据库即可启动。

若使用外部 PostgreSQL：

1. `docker compose up -d` 或自建库 `kiftd/kiftd/kiftd`
2. 在 `kiftd-backend/src/main/resources/application.yml` 将 `kiftd.embedded-pg` 设为 `false`

## 启动

```bash
# 后端（请使用 JDK 21 + Gradle 8.14）
cd kiftd-backend
set JAVA_HOME=D:\develop\env\jdks\21
gradlew.bat bootRun
# 或：D:\develop\env\gradles\8.14\bin\gradle.bat bootRun
#（gradlew.bat 已固定指向本机 D:\develop\env\gradles\8.14）

# 前端
cd kiftd-front
npm install
npm run dev
```

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- WebDAV：http://localhost:8080/webdav/
- 默认管理员：`admin` / `admin`

## 已实现能力

- 登录 / 注册 / 改密（RSA 加密密码 + 验证码）
- 文件夹与文件 CRUD、搜索、批量删除
- 上传 / 下载 / 打包 zip、剪切复制粘贴
- 上传文件夹（按相对路径自动建目录）
- 大目录分段加载（view + remaining）
- 图片 / 音频 / 视频预览（视频可选 FFmpeg 转码）
- PDF / TXT / Word(docx) 预览
- 文件直链与外链下载密钥
- 公告、系统 OS 信息
- 基础 WebDAV（PROPFIND/GET/PUT/DELETE/MKCOL）

## 配置要点

见 `kiftd-backend/src/main/resources/application.yml`：

- `kiftd.jwt.*` JWT 密钥与过期
- `kiftd.storage.root` 文件块存储目录
- `kiftd.signup.enabled` 是否开放注册
- `kiftd.ffmpeg.path` ffmpeg 可执行文件
- `kiftd.cors.allowed-origins` 前端来源
