# 电梯监控系统 — 手动验证命令集
# 环境: Windows PowerShell + Docker
# 后端: http://localhost:10008
# 前端: http://localhost:8080
# Redis: docker exec elevator-redis redis-cli
# MySQL: docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB!

# ============================================================
# 预备：定义快捷 alias（可选）
# ============================================================
$redis = "docker exec elevator-redis redis-cli"
$mysql = "docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB!"

# ============================================================
# 1. 正常报文 → 全链路验证
# ============================================================

# 1.1 发送正常报文（设备00000050, 1F, 上行, 关门）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:00:00/00000050/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:00:00&elevatorID=00000050"

# 1.2 验证 Redis HSET
docker exec elevator-redis redis-cli HGET elevator:status 00000050

# 1.3 验证 V2 API 查询
curl.exe -s "http://localhost:10008/api/v2/status/00000050"

# 1.4 验证 MySQL 历史记录
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT id, device_id, current_floor, direction, door_status, speed, created_at FROM elevator_monitor.elevator_history WHERE device_id='00000050' ORDER BY id DESC LIMIT 3;"

# 1.5 验证 Redis PUBLISH（另开一个终端执行）
# docker exec elevator-redis redis-cli SUBSCRIBE elevator:status
# 然后在当前终端再发一条报文


# ============================================================
# 2. V2 API 端点验证
# ============================================================

# 2.1 V2 数据上报
curl.exe -s -X POST "http://localhost:10008/api/v2/mnk" -d "data=F2026/06/25 13:01:00/00000051/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:01:00&elevatorID=00000051"

# 2.2 V2 状态查询
curl.exe -s "http://localhost:10008/api/v2/status/00000051"

# 2.3 V2 查询不存在的设备（应返回 NOT_FOUND）
curl.exe -s "http://localhost:10008/api/v2/status/99999999"


# ============================================================
# 3. 告警规则逐一验证
# ============================================================

# --- 3.1 FLOOR_JUMP（楼层跳变）---
# 先建基线：1F
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:10:00/00000061/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:10:00&elevatorID=00000061"
# 跳变：1F→4F (diff=3 > threshold=2)
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:10:01/00000061/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=13:10:01&elevatorID=00000061"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail FROM elevator_monitor.alarm_event WHERE device_id='00000061' ORDER BY id DESC LIMIT 3;"


# --- 3.2 DIRECTION_MISMATCH（方向不一致）---
# 基线：上行4F
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:11:00/00000062/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=13:11:00&elevatorID=00000062"
# 矛盾：方向=上行(34)，但楼层从4降到1
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:11:01/00000062/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:11:01&elevatorID=00000062"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail FROM elevator_monitor.alarm_event WHERE device_id='00000062' ORDER BY id DESC LIMIT 3;"


# --- 3.3 SPEED_ABNORMAL（超速）---
# 第一包：1F
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:12:00/00000063/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:12:00&elevatorID=00000063"
# 第二包：4F, 2秒后 → speed = 3层×2.8m / 2s = 4.2m/s > 3.0
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:12:02/00000063/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=13:12:02&elevatorID=00000063"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail, speed FROM elevator_monitor.alarm_event WHERE device_id='00000063' ORDER BY id DESC LIMIT 3;"
# 验证速度已写入 Redis
docker exec elevator-redis redis-cli HGET elevator:status 00000063


# --- 3.4 DOOR_OPEN_TOO_LONG（开门超时, 阈值20s）---
# 第一包：开门（建立基准时间）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:13:00/00000064/000000000000517c00000000000421b934050000000053c3d00063000000220e&time=13:13:00&elevatorID=00000064"
# ⚠️ 等待至少 22 秒
Start-Sleep -Seconds 22
# 第二包：再次开门
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:13:22/00000064/000000000000517c00000000000421b934050000000053c3d00063000000220e&time=13:13:22&elevatorID=00000064"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail FROM elevator_monitor.alarm_event WHERE device_id='00000064' ORDER BY id DESC LIMIT 3;"


# --- 3.5 ALARM_FIELD（平层开门超时, dev阈值10s）---
# 第一包：开门+平层 (door=01开门, floor=01, target=1, direction=平层30)
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:14:00/00000065/010000000000517c00000000000421b930050000000053c3d00063000000220e&time=13:14:00&elevatorID=00000065"
# ⚠️ 等待至少 12 秒（dev阈值10s）
Start-Sleep -Seconds 12
# 第二包：再次开门平层
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:14:12/00000065/010000000000517c00000000000421b930050000000053c3d00063000000220e&time=13:14:12&elevatorID=00000065"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail FROM elevator_monitor.alarm_event WHERE device_id='00000065' ORDER BY id DESC LIMIT 3;"
# 验证 Redis 中 alarm 字段
docker exec elevator-redis redis-cli HGET elevator:status 00000065
# 验证平层追踪状态
docker exec elevator-redis redis-cli HGETALL elevator:leveling:00000065


# --- 3.6 LONG_IDLE（长时空闲, 阈值60s）---
# 第一包：上行1F（非平层方向）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:15:00/00000066/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:15:00&elevatorID=00000066"
# ⚠️ 等待至少 62 秒
Start-Sleep -Seconds 62
# 第二包：相同楼层、相同方向
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:16:02/00000066/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:16:02&elevatorID=00000066"
# 查告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, detail FROM elevator_monitor.alarm_event WHERE device_id='00000066' ORDER BY id DESC LIMIT 3;"


# ============================================================
# 4. 异常容错验证
# ============================================================

# 4.1 空 body（预期 -1）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=&time=13:20:00&elevatorID=00000099"

# 4.2 长度不足（预期 -2）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25&time=13:20:00&elevatorID=00000099"

# 4.3 乱码文本（预期 -2）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=this is garbage data!!!&time=13:20:00&elevatorID=00000099"

# 4.4 异常后系统仍正常（预期 0）
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 13:20:01/00000050/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=13:20:01&elevatorID=00000050"


# ============================================================
# 5. Redis 直接操作
# ============================================================

# 查看所有 elevator 相关 key
docker exec elevator-redis redis-cli KEYS "elevator:*"

# 查看某个设备的完整状态
docker exec elevator-redis redis-cli HGETALL elevator:status 00000050

# 查看 speedtrack 状态
docker exec elevator-redis redis-cli HGETALL elevator:speedtrack:00000063

# 查看 leveling 状态
docker exec elevator-redis redis-cli HGETALL elevator:leveling:00000065

# 查看 Hash 中的字段数量
docker exec elevator-redis redis-cli HLEN elevator:status

# 查看所有设备ID
docker exec elevator-redis redis-cli HKEYS elevator:status

# 实时监控所有 Redis 命令
docker exec elevator-redis redis-cli MONITOR

# 订阅电梯状态频道
docker exec elevator-redis redis-cli SUBSCRIBE elevator:status

# 订阅告警频道
docker exec elevator-redis redis-cli SUBSCRIBE elevator:alarm


# ============================================================
# 6. MySQL 查询
# ============================================================

# 查看最近10条历史记录
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT id, device_id, current_floor, direction, speed, door_status, alarm, created_at FROM elevator_monitor.elevator_history ORDER BY id DESC LIMIT 10;"

# 查看某设备的所有历史记录
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT * FROM elevator_monitor.elevator_history WHERE device_id='00000050' ORDER BY id DESC;"

# 查看最近告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT id, device_id, event_type, rule_name, alarm_level, detail, created_at FROM elevator_monitor.alarm_event ORDER BY id DESC LIMIT 10;"

# 按规则统计告警次数
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level, COUNT(*) as cnt FROM elevator_monitor.alarm_event WHERE event_type='FIRED' GROUP BY rule_name, alarm_level ORDER BY cnt DESC;"

# 查看当前活跃告警
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT * FROM elevator_monitor.alarm_event WHERE active=1 ORDER BY created_at DESC;"

# 清理某设备的所有数据（替换 DEVICE_ID）
# docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "DELETE FROM elevator_monitor.elevator_history WHERE device_id='DEVICE_ID';"
# docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "DELETE FROM elevator_monitor.alarm_event WHERE device_id='DEVICE_ID';"


# ============================================================
# 7. 前端验证
# ============================================================

# 打开前端页面
start http://localhost:8080

# 检查前端 HTML 是否正常
curl.exe -s http://localhost:8080/ | Select-Object -First 10


# ============================================================
# 8. 批量回归脚本（一键跑完所有告警规则）
# ============================================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  电梯监控系统 — 批量回归测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$devices = @{
    normal    = "00000070"
    floorJump = "00000071"
    dirMismatch = "00000072"
    speed     = "00000073"
    doorOpen  = "00000074"
    leveling  = "00000075"
}

# 正常报文
Write-Host "`n[1/6] 正常报文..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:00:00/$($devices.normal)/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=14:00:00&elevatorID=$($devices.normal)"
$r1 = docker exec elevator-redis redis-cli HGET elevator:status $devices.normal
Write-Host "  Redis: $r1"

# FLOOR_JUMP
Write-Host "`n[2/6] FLOOR_JUMP..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:01:00/$($devices.floorJump)/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=14:01:00&elevatorID=$($devices.floorJump)"
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:01:01/$($devices.floorJump)/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=14:01:01&elevatorID=$($devices.floorJump)"
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level FROM elevator_monitor.alarm_event WHERE device_id='$($devices.floorJump)' ORDER BY id DESC LIMIT 1;"

# DIRECTION_MISMATCH
Write-Host "`n[3/6] DIRECTION_MISMATCH..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:02:00/$($devices.dirMismatch)/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=14:02:00&elevatorID=$($devices.dirMismatch)"
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:02:01/$($devices.dirMismatch)/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=14:02:01&elevatorID=$($devices.dirMismatch)"
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level FROM elevator_monitor.alarm_event WHERE device_id='$($devices.dirMismatch)' ORDER BY id DESC LIMIT 1;"

# SPEED_ABNORMAL
Write-Host "`n[4/6] SPEED_ABNORMAL..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:03:00/$($devices.speed)/000000000000517c00000000001021b934050000000053c3d00063000000220e&time=14:03:00&elevatorID=$($devices.speed)"
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:03:02/$($devices.speed)/000000000000517c00000000001021b934110000000053c3d00063000000220e&time=14:03:02&elevatorID=$($devices.speed)"
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level FROM elevator_monitor.alarm_event WHERE device_id='$($devices.speed)' ORDER BY id DESC LIMIT 1;"

# DOOR_OPEN_TOO_LONG
Write-Host "`n[5/6] DOOR_OPEN_TOO_LONG (wait 22s)..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:04:00/$($devices.doorOpen)/000000000000517c00000000000421b934050000000053c3d00063000000220e&time=14:04:00&elevatorID=$($devices.doorOpen)"
Start-Sleep -Seconds 22
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:04:22/$($devices.doorOpen)/000000000000517c00000000000421b934050000000053c3d00063000000220e&time=14:04:22&elevatorID=$($devices.doorOpen)"
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level FROM elevator_monitor.alarm_event WHERE device_id='$($devices.doorOpen)' ORDER BY id DESC LIMIT 1;"

# ALARM_FIELD (leveling timeout)
Write-Host "`n[6/6] ALARM_FIELD (wait 12s)..." -ForegroundColor Yellow
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:05:00/$($devices.leveling)/010000000000517c00000000000421b930050000000053c3d00063000000220e&time=14:05:00&elevatorID=$($devices.leveling)"
Start-Sleep -Seconds 12
curl.exe -s -X POST "http://localhost:10008/api/mnk" -d "data=F2026/06/25 14:05:12/$($devices.leveling)/010000000000517c00000000000421b930050000000053c3d00063000000220e&time=14:05:12&elevatorID=$($devices.leveling)"
docker run --rm mysql:8.0 mysql -h host.docker.internal -u root -p892366520bB! -e "SELECT rule_name, alarm_level FROM elevator_monitor.alarm_event WHERE device_id='$($devices.leveling)' ORDER BY id DESC LIMIT 1;"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  回归测试完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan


# ============================================================
# 9. HEX 报文速查表
# ============================================================
# 
# 模板: F{日期19字符}/{8字节ID}/{段1 16字符}{段2 16字符}{段3 16字符}{段4 16字符}
#
# 方向 (段3[0-1]):
#   34 = 上行(01)   35 = 下行(02)   30/31/36/37 = 平层(00)
#
# 楼层 (段3[2-3]):
#   05 = 1F(01)     09 = 2F(02)     0d = 3F(03)     11 = 4F(04)
#
# 门状态 (段2[10-11]):
#   10 = 关门(00)   04 = 开门(01)   00 = 关门中(02)
#
# 目标楼层 (段1[1]):
#   0=无  1=1F  2=2F  4=3F  8=4F
#
# 段标识符:
#   段1末尾2字符=51 (内招)   段2末尾2字符=21 (门)
#   段3末尾2字符=53 (运行)   段4末尾2字符=22 (保留)
#
# 示例报文:
# F2026/06/25 13:00:00/00000050/000000000000517c00000000001021b934050000000053c3d00063000000220e
#                                  ^目标=无       ^门=关门    ^上行+1F         ^保留
