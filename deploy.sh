#!/bin/bash
# ============================================================
# Elevator Monitor — 一键部署脚本 (Linux/macOS)
# 用法:
#   chmod +x deploy.sh
#   ./deploy.sh              # 部署全部服务
#   ./deploy.sh --build      # 强制重新构建镜像
#   ./deploy.sh --down       # 停止并清理所有服务
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- 颜色输出 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step()  { echo -e "\n${BLUE}============================================================${NC}"; echo -e "${BLUE}▶ $1${NC}"; echo -e "${BLUE}============================================================${NC}"; }

# ---- 检查依赖 ----
check_deps() {
    step "1/6 检查环境依赖"

    if ! command -v docker &> /dev/null; then
        error "Docker 未安装，请先安装 Docker: https://docs.docker.com/engine/install/"
        exit 1
    fi
    info "✅ Docker 已安装: $(docker --version)"

    if ! docker compose version &> /dev/null; then
        error "Docker Compose 未安装，请安装 Docker Compose V2"
        exit 1
    fi
    info "✅ Docker Compose 已安装: $(docker compose version)"

    # 检查 .env 文件
    if [ ! -f ".env" ]; then
        warn ".env 文件不存在，将使用默认配置"
        cp .env.example .env 2>/dev/null || true
    else
        info "✅ .env 配置文件已就绪"
    fi
}

# ---- 构建后端 JAR ----
build_backend() {
    step "2/6 构建后端 JAR 包"

    cd "$SCRIPT_DIR/backend"

    # 检查是否已有 JAR
    if [ -f "target/elevator-monitor-0.1.4-SNAPSHOT.jar" ] && [ "$FORCE_BUILD" != "true" ]; then
        info "JAR 包已存在，跳过构建（使用 --build 强制重新构建）"
        return
    fi

    info "正在使用 Maven 构建..."
    if command -v mvn &> /dev/null; then
        mvn package -DskipTests -q
    elif [ -f "./mvnw" ]; then
        ./mvnw package -DskipTests -q
    else
        error "找不到 Maven，请安装 Maven 或确保 mvnw 可用"
        exit 1
    fi

    info "✅ 后端构建完成"
    cd "$SCRIPT_DIR"
}

# ---- 构建 Docker 镜像 ----
build_images() {
    step "3/6 构建 Docker 镜像"

    if [ "$FORCE_BUILD" = "true" ]; then
        info "强制重新构建所有镜像..."
        docker compose build --no-cache
    else
        docker compose build
    fi
    info "✅ 镜像构建完成"
}

# ---- 初始化数据库 ----
init_database() {
    step "4/6 初始化数据库"

    # 创建 SQL 初始化目录（如果不存在）
    mkdir -p "$SCRIPT_DIR/sql"

    # 如果 init.sql 不存在，创建一个空的（JPA 会自动建表）
    if [ ! -f "$SCRIPT_DIR/sql/init.sql" ]; then
        cat > "$SCRIPT_DIR/sql/init.sql" << 'EOF'
-- Elevator Monitor 数据库初始化脚本
-- JPA/Hibernate 会在应用启动时自动建表 (ddl-auto: update)
-- 此文件用于手动初始化时的 SQL 语句

CREATE DATABASE IF NOT EXISTS elevator_monitor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE elevator_monitor;

-- 如果需要预置数据，请在此添加 INSERT 语句
EOF
        info "已创建 sql/init.sql 初始化脚本"
    fi
}

# ---- 启动服务 ----
start_services() {
    step "5/6 启动服务"

    # 先拉取基础镜像（MySQL, Redis）
    info "拉取基础镜像..."
    docker compose pull mysql redis

    # 启动所有服务
    info "启动所有服务..."
    docker compose up -d

    info "✅ 服务已启动，等待健康检查..."
}

# ---- 验证部署 ----
verify_deployment() {
    step "6/6 验证部署状态"

    echo ""
    echo "服务状态:"
    docker compose ps

    echo ""
    echo "等待服务就绪..."
    sleep 10

    # 检查后端健康
    echo ""
    info "检查后端健康状态..."
    if curl -sf http://localhost:10008/api/v2/status/00000001 > /dev/null 2>&1; then
        info "✅ 后端运行正常 (端口 10008)"
    else
        warn "⚠️ 后端尚未就绪，请稍等后重试"
    fi

    # 检查前端健康
    echo ""
    info "检查前端健康状态..."
    if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
        info "✅ 前端运行正常 (端口 8080)"
    else
        warn "⚠️ 前端尚未就绪，请稍等后重试"
    fi

    # 检查 Redis
    echo ""
    info "检查 Redis..."
    if docker exec elevator-redis redis-cli ping 2>/dev/null | grep -q PONG; then
        info "✅ Redis 运行正常"
    else
        warn "⚠️ Redis 未响应"
    fi

    # 检查 MySQL
    echo ""
    info "检查 MySQL..."
    if docker exec elevator-mysql mysqladmin ping -h localhost -u root -p"${MYSQL_ROOT_PASSWORD:-SZTUbdi@1005}" 2>/dev/null | grep -q "alive"; then
        info "✅ MySQL 运行正常"
    else
        warn "⚠️ MySQL 未响应"
    fi
}

# ---- 完成提示 ----
print_summary() {
    echo ""
    echo -e "${GREEN}============================================================${NC}"
    echo -e "${GREEN}  🎉 部署完成！${NC}"
    echo -e "${GREEN}============================================================${NC}"
    echo ""
    echo "  访问地址:"
    echo "    📊 监控大屏:  http://localhost:8080/monitor"
    echo "    📋 设备详情:  http://localhost:8080/show?id=00000200"
    echo "    🔍 设备搜索:  http://localhost:8080/"
    echo "    🏥 健康检查:  http://localhost:8080/health"
    echo "    📡 后端 API:  http://localhost:10008/api/v2/status/{deviceId}"
    echo ""
    echo "  常用命令:"
    echo "    查看日志:     docker compose logs -f"
    echo "    查看后端日志: docker compose logs -f backend"
    echo "    查看前端日志: docker compose logs -f frontend"
    echo "    重启服务:     docker compose restart"
    echo "    停止服务:     docker compose down"
    echo "    停止+清理:   docker compose down -v"
    echo ""
}

# ---- 停止服务 ----
stop_services() {
    step "停止并清理所有服务"
    docker compose down
    info "✅ 所有服务已停止"

    read -p "是否同时删除数据卷？(y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker compose down -v
        info "✅ 数据卷已清理"
    fi
}

# ============================================================
# 主入口
# ============================================================
FORCE_BUILD="false"

case "${1:-}" in
    --build|-b)
        FORCE_BUILD="true"
        check_deps
        build_backend
        build_images
        init_database
        start_services
        verify_deployment
        print_summary
        ;;
    --down|-d)
        stop_services
        ;;
    --restart|-r)
        info "重启所有服务..."
        docker compose restart
        info "✅ 服务已重启"
        ;;
    --logs|-l)
        docker compose logs -f "${2:-}"
        ;;
    --help|-h)
        echo "用法: ./deploy.sh [选项]"
        echo ""
        echo "选项:"
        echo "  (无参数)      部署全部服务"
        echo "  --build, -b   强制重新构建镜像"
        echo "  --down, -d    停止并清理所有服务"
        echo "  --restart, -r 重启所有服务"
        echo "  --logs, -l    查看日志 (可指定服务名)"
        echo "  --help, -h    显示此帮助"
        ;;
    *)
        check_deps
        build_backend
        build_images
        init_database
        start_services
        verify_deployment
        print_summary
        ;;
esac
