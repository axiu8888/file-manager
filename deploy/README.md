# kiftd Docker 部署说明（Ubuntu 宿主机）

单机三容器方案：**PostgreSQL** + **Spring Boot (jar)** + **Nginx（静态前端 + 反代）**。  
不需要把 jar / nginx / postgres 打进一个巨型 Ubuntu 容器；一律用 `docker compose` 编排。

推荐宿主机根目录：`/opt/apps/kiftd`

## 架构

```
浏览器 → nginx:80 ─┬─ /          → 前端静态资源 (Vite dist, base=/)
                   ├─ /api/      → app:8080
                   └─ /webdav/   → app:8080
app → postgres:5432
数据卷 → /opt/apps/kiftd/data
```

## 目录布局

```
/opt/apps/kiftd/
  docker-compose.yml
  Dockerfile.app
  Dockerfile.nginx
  nginx.conf
  application-prod.yml      # 构建上下文用
  .env                      # 密钥与口令（勿提交）
  config/
    application-prod.yml    # 挂载进 app 容器
  dist/
    app.jar
    front/                  # 前端构建产物
  data/
    filenodes/              # 文件块
    temp/
    postgres/               # PG 数据
  logs/
  scripts/
    build.sh / install.sh / start.sh / stop.sh
```

也可在仓库 `deploy/` 目录直接 `docker compose`，不必先拷到 `/opt/apps/kiftd`（此时设置 `KIFTD_HOST_ROOT` 指向该目录）。

## 环境要求（Ubuntu）

- Docker 24+ / Docker Compose v2
- 构建机（可与宿主机相同）：JDK 21、Gradle 8.14+、Node.js 20+
- （可选）本机已安装 FFmpeg；镜像内已带 ffmpeg 供视频预览

## 快速部署

### 1. 构建产物

在仓库根目录（Linux / macOS / WSL）：

```bash
chmod +x deploy/scripts/*.sh
./deploy/scripts/build.sh
```

Windows（PowerShell）可先构建产物，再拷到 Ubuntu 安装：

```powershell
pwsh -File deploy/scripts/build.ps1
```

产物：

- `deploy/dist/app.jar`
- `deploy/dist/front/`

若本机 `gradlew` 依赖 `GRADLE_HOME`，可先导出：

```bash
export GRADLE_HOME=/path/to/gradle-8.14
```

### 2. 安装到 /opt/apps/kiftd

```bash
sudo ./deploy/scripts/install.sh
# 或：KIFTD_HOST_ROOT=/opt/apps/kiftd sudo -E ./deploy/scripts/install.sh
```

脚本会创建数据目录、复制 compose / Dockerfile / nginx 配置、`.env` 与 `dist/`。

### 3. 修改密钥（必做）

编辑 `/opt/apps/kiftd/.env`：

| 变量 | 说明 | 默认（务必改） |
|------|------|----------------|
| `POSTGRES_PASSWORD` | 数据库密码 | `kiftd` |
| `KIFTD_JWT_SECRET` | JWT 密钥 | `change-me` |
| `KIFTD_ADMIN_PASSWORD` | 初始管理员密码 | `change-me` |
| `HTTP_PORT` | 对外端口 | `80` |
| `KIFTD_CORS_ALLOWED_ORIGINS` | CORS；同域可留空 | 空 |

对应 Spring 环境变量（compose 已注入）：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/kiftd
SPRING_DATASOURCE_USERNAME=kiftd
SPRING_DATASOURCE_PASSWORD=...
KIFTD_EMBEDDED_PG=false
KIFTD_STORAGE_ROOT=/opt/apps/kiftd/data/filenodes
KIFTD_JWT_SECRET=...
SERVER_PORT=8080
```

### 4. 启动 / 停止

```bash
/opt/apps/kiftd/scripts/start.sh
/opt/apps/kiftd/scripts/stop.sh
```

或：

```bash
cd /opt/apps/kiftd
docker compose --env-file .env up -d --build
docker compose down
```

访问：`http://<主机>/`  
API：`http://<主机>/api/`  
WebDAV：`http://<主机>/webdav/`

应用容器内监听 **8080**，仅由 nginx 对外暴露 **80**。

## 仅用仓库 deploy/ 一键启动（不装到 /opt）

适合开发机试跑：

```bash
./deploy/scripts/build.sh
cp deploy/.env.example deploy/.env
# 数据写在 deploy 旁或自定义
export KIFTD_HOST_ROOT="$(pwd)/deploy"
mkdir -p deploy/data/{filenodes,temp,postgres} deploy/logs deploy/config
cp deploy/application-prod.yml deploy/config/
cd deploy
docker compose --env-file .env up -d --build
```

`start.sh` 在 `deploy/scripts/` 下执行时，会自动以 `deploy/` 为 compose 根目录。

## 更新版本

```bash
./deploy/scripts/build.sh
sudo ./deploy/scripts/install.sh
/opt/apps/kiftd/scripts/start.sh
```

数据在 `data/` 卷中，一般不会因重建镜像丢失。

## 配置说明

- 生产配置：`deploy/application-prod.yml`（禁用嵌入式 PG，存储路径指向容器内 `/opt/apps/kiftd/data/...`）
- CORS：经 nginx 同域访问时保持 `KIFTD_CORS_ALLOWED_ORIGINS` 为空即可
- `client_max_body_size` 已设为不限制；已转发 `Range` / `Accept-Ranges` 以支持视频拖拽
- 前端 Vite `base` 保持 `/`，由 nginx 托管 `dist` 并做 SPA `try_files` 回退

## 常见问题

1. **app 启动失败 / 连不上库**  
   `docker compose logs app`；确认 `postgres` healthy，且 `SPRING_DATASOURCE_URL` 主机名为 `postgres`。

2. **上传大文件失败**  
   检查反向代理超时；本仓库 nginx 已放宽超时与 body 大小。

3. **权限不足写 data**  
   `sudo chmod -R a+rwX /opt/apps/kiftd/data /opt/apps/kiftd/logs`

4. **健康检查失败**  
   app 镜像会请求 `/api/system/ping`；确认该接口可匿名访问。
