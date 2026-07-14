# ============================================================
# Elevator Monitor — Go 前端 Docker 镜像
# 多阶段构建：编译 → 精简运行镜像
# ============================================================

# ---- 阶段1：编译 ----
FROM golang:1.19-alpine AS builder

# 设置 Go 代理（国内加速）
ENV GOPROXY=https://goproxy.cn,direct
ENV CGO_ENABLED=0
ENV GOOS=linux
ENV GOARCH=amd64

WORKDIR /build

# 先复制依赖文件，利用 Docker 缓存层
COPY go.mod go.sum ./
RUN go mod download

# 复制源码并编译
COPY . .
RUN go build -ldflags="-s -w" -o elevator-frontend main.go

# ---- 阶段2：运行 ----
FROM alpine:3.18

# 安装 wget（健康检查用）和 ca-certificates
RUN apk add --no-cache wget ca-certificates tzdata
ENV TZ=Asia/Shanghai

WORKDIR /app

# 从编译阶段复制可执行文件
COPY --from=builder /build/elevator-frontend .

# 复制静态资源和模板
COPY html/ ./html/
COPY static/ ./static/

# 暴露端口
EXPOSE 8080

# 启动
ENTRYPOINT ["./elevator-frontend"]
CMD ["8080"]
