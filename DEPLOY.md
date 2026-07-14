# 🚀 Elevator Monitor — 服务器部署完整教程

---

## 📋 目录

1. [项目架构概览](#1-项目架构概览)
2. [部署前准备](#2-部署前准备)
3. [方式一：Docker Compose 一键部署（推荐）](#3-方式一docker-compose-一键部署推荐)
4. [方式二：手动分步部署](#4-方式二手动分步部署)
5. [上传项目到服务器](#5-上传项目到服务器)
6. [配置 Nginx 反向代理](#6-配置-nginx-反向代理可选)
7. [配置 SSL/HTTPS](#7-配置-sslhttps可选)
8. [设置开机自启](#8-设置开机自启)
9. [常用运维命令](#9-常用运维命令)
10. [故障排查](#10-故障排查)

---

## 1. 项目架构概览

```
┌──────────────────────────────────────────────────────────┐
│                      服务器                               │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌───────────┐  │
│  │  MySQL  │  │  Redis  │  │ 后端 API │  │ Go 前端   │  │
│  │  :3306  │  │  :6379  │  │ :10008   │  │ :8080     │  │
│  └─────────┘  └─────────┘  └──────────┘  └───────────┘  │
│       ↑            ↑            ↑              ↑         │
│       └────────────┴────────────┴──────────────┘         │
│                     Docker Network                       │
└──────────────────────────────────────────────────────────┘
         ↑                                      ↑
    MQTT Broker                          浏览器访问
  (远程 EMQX)                          http://你的IP:8080
```

| 服务 | 端口 | 技术栈 | 说明 |
|------|------|--------|------|
| MySQL | 3306 (映射3307) | MySQL 8.0 | 历史数据 + 告警持久化 |
| Redis | 6379 | Redis 7 Alpine | 实时状态缓存 + 消息队列 |
| 后端 | 10008 | Spring Boot 2.7 + Java 8 | 协议解析、告警引擎、API |
| 前端 | 8080 | Go 1.19 + Gin + WebSocket | Web 页面 + 实时推送 |
| MQTT桥接 | — | Python + Paho | 连接远程 EMQX Broker |

---

## 2. 部署前准备

### 2.1 服务器最低配置

| 配置项 | 最低要求 | 推荐配置 |
|--------|----------|----------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 20 GB | 50 GB+ (SSD) |
| 系统 | Ubuntu 20.04+ / CentOS 7+ / Debian 11+ | Ubuntu 22.04 |
| 网络 | 可访问外网 (连接 EMQX Broker) | 固定公网 IP |

### 2.2 安装 Docker 和 Docker Compose

**Ubuntu/Debian:**
```bash
# 1. 安装 Docker
curl -fsSL https://get.docker.com | bash

# 2. 将当前用户加入 docker 组（免 sudo）
sudo usermod -aG docker $USER
newgrp docker

# 3. 验证安装
docker --version
docker compose version
```

**CentOS/RHEL:**
```bash
# 1. 安装 Docker
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 2. 启动 Docker
sudo systemctl enable docker
sudo systemctl start docker

# 3. 验证
docker --version
docker compose version
```

### 2.3 开放防火墙端口

```bash
# Ubuntu (UFW)
sudo ufw allow 8080/tcp   # 前端页面
sudo ufw allow 10008/tcp  # 后端 API（如需外部访问）
sudo ufw reload

# CentOS (firewalld)
sudo firewall-cmd --add-port=8080/tcp --permanent
sudo firewall-cmd --add-port=10008/tcp --permanent
sudo firewall-cmd --reload
```

> ⚠️ **安全提示**: 如果使用 Nginx 反向代理，只需开放 80/443 端口，不要直接暴露 8080 和 10008。

---

## 3. 方式一：Docker Compose 一键部署（推荐）

这是最简单的方式，一条命令搞定全部服务。

### 3.1 上传项目

```bash
# 在本地电脑执行，将项目上传到服务器
# 方式A：使用 scp
scp -r C:\Users\12074\Desktop\new\elevator_monitor root@你的服务器IP:/opt/

# 方式B：使用 Git
# 先在 GitHub/Gitee 创建仓库，推上去后服务器 git clone
```

### 3.2 修改配置

```bash
# SSH 登录服务器
ssh root@你的服务器IP

# 进入项目目录
cd /opt/elevator_monitor

# 编辑环境变量（按需修改密码等敏感信息）
vim .env
```

> 🔑 **重要**: 务必修改 `.env` 中的 `MYSQL_ROOT_PASSWORD` 等密码！

### 3.3 一键启动

```bash
cd /opt/elevator_monitor

# 给部署脚本执行权限
chmod +x deploy.sh

# 一键部署
./deploy.sh

# 如果后端代码有更新，强制重新构建
./deploy.sh --build
```

部署脚本会自动完成：
1. ✅ 检查 Docker 环境
2. ✅ 编译后端 JAR 包
3. ✅ 构建 Docker 镜像
4. ✅ 初始化数据库
5. ✅ 启动所有服务
6. ✅ 验证服务健康状态

### 3.4 或者手动使用 Docker Compose

```bash
cd /opt/elevator_monitor

# 先编译后端 JAR（在服务器上需要安装 Java 和 Maven）
cd backend
./mvnw package -DskipTests   # Linux/Mac
# 或在本地编译好 JAR 后直接上传 target 目录
cd ..

# 构建并启动
docker compose up -d

# 查看运行状态
docker compose ps

# 查看日志
docker compose logs -f
```

### 3.5 访问验证

```bash
# 健康检查
curl http://localhost:8080/health
curl http://localhost:10008/api/v2/status/00000001

# 浏览器访问（替换为你的服务器 IP）
# http://你的服务器IP:8080/show?id=00000200
# http://你的服务器IP:8080/monitor
```

---

## 4. 方式二：手动分步部署

如果你不想用 Docker Compose，可以手动一步步来。

### 4.1 安装运行环境

```bash
# ---- Java 8 ----
# Ubuntu
sudo apt install -y openjdk-8-jdk

# 或者用 Eclipse Temurin
wget https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u372-b07/OpenJDK8U-jdk_x64_linux_hotspot_8u372b07.tar.gz
tar -xzf OpenJDK8U-jdk_x64_linux_hotspot_8u372b07.tar.gz -C /usr/local/
export JAVA_HOME=/usr/local/jdk8u372-b07

# ---- Go 1.19 ----
wget https://go.dev/dl/go1.19.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.19.linux-amd64.tar.gz
export PATH=$PATH:/usr/local/go/bin

# ---- Python 3 ----
sudo apt install -y python3 python3-pip
pip3 install paho-mqtt
```

### 4.2 启动 MySQL

```bash
docker run -d --name elevator-mysql \
  -e MYSQL_ROOT_PASSWORD=SZTUbdi@1005 \
  -e MYSQL_DATABASE=elevator_monitor \
  -p 3307:3306 \
  --restart always \
  mysql:8.0 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

### 4.3 启动 Redis

```bash
docker run -d --name elevator-redis \
  -p 6379:6379 \
  --restart always \
  redis:7-alpine \
  redis-server --appendonly yes
```

### 4.4 启动后端

```bash
cd /opt/elevator_monitor/backend

# 编译
./mvnw package -DskipTests

# 启动
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3307
export MYSQL_PASSWORD=SZTUbdi@1005

java -jar target/elevator-monitor-0.1.4-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url="jdbc:mysql://127.0.0.1:3307/elevator_monitor?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true"
```

### 4.5 启动前端

```bash
cd /opt/elevator_monitor/frontend

# 编译
go build -o elevator-frontend main.go

# 启动
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
./elevator-frontend 8080 &
```

### 4.6 启动 MQTT 桥接器

```bash
cd /opt/elevator_monitor
python3 mqtt_bridge.py &
```

---

## 5. 上传项目到服务器

### 方式A：SCP 直接上传（最简单）

```powershell
# 在本地 Windows PowerShell 中执行
# 上传整个项目
scp -r C:\Users\12074\Desktop\new\elevator_monitor root@你的服务器IP:/opt/

# 如果只需要更新某个文件
scp C:\Users\12074\Desktop\new\elevator_monitor\backend\target\*.jar root@你的服务器IP:/opt/elevator_monitor/backend/target/
```

### 方式B：Git 仓库（推荐，方便持续更新）

```bash
# 1. 在项目根目录初始化 Git（如果还没有）
cd C:\Users\12074\Desktop\new\elevator_monitor
git init
git add .
git commit -m "Initial commit"

# 2. 推送到 GitHub/Gitee
git remote add origin https://github.com/你的用户名/elevator_monitor.git
git push -u origin main

# 3. 服务器上克隆
ssh root@你的服务器IP
cd /opt
git clone https://github.com/你的用户名/elevator_monitor.git

# 4. 后续更新
git pull
docker compose up -d --build   # 重新构建并启动
```

### 方式C：Rsync（增量同步，适合大项目）

```bash
# 在本地执行（需要安装 rsync 或 WSL）
rsync -avz --progress \
  --exclude 'target/' \
  --exclude 'node_modules/' \
  --exclude '.git/' \
  /c/Users/12074/Desktop/new/elevator_monitor/ \
  root@你的服务器IP:/opt/elevator_monitor/
```

---

## 6. 配置 Nginx 反向代理（可选）

如果你有域名，建议用 Nginx 做反向代理，统一入口。

### 6.1 安装 Nginx

```bash
sudo apt install -y nginx
```

### 6.2 创建配置文件

```bash
sudo vim /etc/nginx/sites-available/elevator
```

```nginx
# /etc/nginx/sites-available/elevator
server {
    listen 80;
    server_name 你的域名.com;   # 或者用服务器 IP

    # 前端页面（Go 服务）
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持（重要！）
        proxy_read_timeout 86400;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:10008;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 健康检查端点
    location /health {
        proxy_pass http://127.0.0.1:8080;
    }

    # WebSocket 端点
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }

    location /ws2 {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
```

### 6.3 启用配置

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/elevator /etc/nginx/sites-enabled/

# 删除默认配置
sudo rm /etc/nginx/sites-enabled/default

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo systemctl reload nginx
```

配置完成后，通过 `http://你的域名` 即可访问，无需加端口号。

---

## 7. 配置 SSL/HTTPS（可选）

使用 Let's Encrypt 免费证书：

```bash
# 安装 certbot
sudo apt install -y certbot python3-certbot-nginx

# 自动获取证书并配置 Nginx
sudo certbot --nginx -d 你的域名.com

# 设置自动续期
sudo certbot renew --dry-run
```

---

## 8. 设置开机自启

Docker Compose 服务已经配置了 `restart: always`，只需确保 Docker 开机自启：

```bash
# Docker 开机自启
sudo systemctl enable docker

# 如果手动启动的服务，可以用 systemd
sudo vim /etc/systemd/system/elevator-backend.service
```

```ini
[Unit]
Description=Elevator Monitor Backend
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=/opt/elevator_monitor
ExecStart=/usr/bin/docker compose up
ExecStop=/usr/bin/docker compose down
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable elevator-backend
```

---

## 9. 常用运维命令

```bash
# ---- Docker Compose 命令 ----
cd /opt/elevator_monitor

# 查看所有服务状态
docker compose ps

# 查看实时日志（所有服务）
docker compose logs -f

# 查看特定服务日志
docker compose logs -f backend
docker compose logs -f frontend

# 重启单个服务
docker compose restart backend

# 停止所有服务
docker compose down

# 停止并删除数据卷（⚠️ 会清空数据库！）
docker compose down -v

# 重新构建并启动
docker compose up -d --build

# ---- 查看资源使用 ----
docker stats

# ---- 进入容器调试 ----
docker exec -it elevator-backend bash
docker exec -it elevator-mysql mysql -u root -p
docker exec -it elevator-redis redis-cli

# ---- 备份数据库 ----
docker exec elevator-mysql mysqldump -u root -pSZTUbdi@1005 elevator_monitor > backup.sql

# ---- 恢复数据库 ----
docker exec -i elevator-mysql mysql -u root -pSZTUbdi@1005 elevator_monitor < backup.sql
```

---

## 10. 故障排查

### 问题1：后端启动失败，连不上 MySQL

```bash
# 检查 MySQL 是否启动
docker ps | grep mysql

# 查看 MySQL 日志
docker logs elevator-mysql

# 检查后端日志
docker logs elevator-backend
```

常见原因：
- MySQL 还没完成初始化，后端就启动了 → 等 MySQL 健康检查通过后再重启后端
- 密码错误 → 检查 `.env` 中的 `MYSQL_ROOT_PASSWORD`

### 问题2：前端页面打开但看不到数据

```bash
# 检查 Redis 是否运行
docker exec elevator-redis redis-cli PING

# 检查 Redis 中是否有数据
docker exec elevator-redis redis-cli HGETALL elevator:status

# 检查 WebSocket 连接
curl http://localhost:8080/health
```

### 问题3：MQTT 设备数据收不到

```bash
# 检查后端是否正常订阅了 MQTT 主题
docker logs elevator-backend | grep -i mqtt

# 手动测试后端 API
curl -X POST http://localhost:10008/api/v2/mnk \
  -d "data=F2020/11/10 12:00:00/00000099/000000000000517c00000000000421b930090000000053c3d00063000000220e" \
  -d "time=12:00:00" \
  -d "elevatorID=00000099"
```

### 问题4：端口被占用

```bash
# 查看端口占用
sudo lsof -i :8080
sudo lsof -i :10008

# 或者用 ss
ss -tlnp | grep 8080
```

---

## 📁 项目部署文件清单

部署完成后，你的服务器上应该有这些文件：

```
/opt/elevator_monitor/
├── docker-compose.yml      # Docker 编排文件
├── .env                     # 环境变量（需要你手动配置）
├── deploy.sh                # 一键部署脚本
├── Dockerfile.bridge        # MQTT 桥接器镜像
├── mqtt_bridge.py           # MQTT 桥接器脚本
├── backend/
│   ├── Dockerfile           # 后端镜像
│   └── target/
│       └── elevator-monitor-0.1.4-SNAPSHOT.jar
├── frontend/
│   ├── Dockerfile           # 前端镜像
│   ├── main.go
│   ├── html/
│   └── static/
└── sql/
    └── init.sql             # 数据库初始化脚本
```

---

> 💡 **快速上手总结**: 
> 1. 服务器安装 Docker → 
> 2. 上传项目 → 
> 3. 修改 `.env` 密码 → 
> 4. 运行 `./deploy.sh` → 
> 5. 访问 `http://你的IP:8080/monitor` 🎉
