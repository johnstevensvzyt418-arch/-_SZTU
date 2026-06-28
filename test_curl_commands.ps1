# ============================================================================
# Elevator Monitor — MNK 验收测试 curl 命令集 (PowerShell)
# 逐条复制到 PowerShell 终端执行
# 服务地址: http://localhost:10008
# ============================================================================

$BASE = "http://localhost:10008/api/mnk"
$DEV = "00000001"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 1: 正常运行 (NORMAL)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
$body1 = @{
    data = "F2026/06/25 10:00:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:00:00.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 2: 楼层跳变 (FLOOR_JUMP)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "--- Step 1: 1楼基准 ---" -ForegroundColor Yellow
$body2a = @{
    data = "F2026/06/25 10:00:05/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:00:05.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body2a

Start-Sleep -Seconds 1

Write-Host "--- Step 2: 跳变到4楼 (1->4, 差3层 > 阈值2) ---" -ForegroundColor Yellow
$body2b = @{
    data = "F2026/06/25 10:00:10/$DEV/08000000000000510000000000100021 3411000000000053c3d00063000000220e"
    time = "10:00:10.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body2b

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 3: 方向冲突 (DIRECTION_MISMATCH)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "--- Step 1: 上行到4楼 ---" -ForegroundColor Yellow
$body3a = @{
    data = "F2026/06/25 10:01:00/$DEV/08000000000000510000000000100021 3411000000000053c3d00063000000220e"
    time = "10:01:00.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body3a

Start-Sleep -Seconds 1

Write-Host "--- Step 2: 上行到1楼 (冲突! 上行但楼层下降) ---" -ForegroundColor Yellow
$body3b = @{
    data = "F2026/06/25 10:01:05/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:01:05.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body3b

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 4: 长时间开门 (DOOR_OPEN_TOO_LONG)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "--- Step 1: 关门到位 ---" -ForegroundColor Yellow
$body4a = @{
    data = "F2026/06/25 10:02:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:02:00.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body4a

Write-Host ">>> 等待 21 秒... <<<" -ForegroundColor Red
Start-Sleep -Seconds 21

Write-Host "--- Step 2: 开门到位 (距上次关门 > 20秒) ---" -ForegroundColor Yellow
$body4b = @{
    data = "F2026/06/25 10:02:21/$DEV/01000000000000510000000000040021 3405000000000053c3d00063000000220e"
    time = "10:02:21.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body4b

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 5: 长时间静止 (LONG_IDLE)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "--- Step 1: 上行1楼 ---" -ForegroundColor Yellow
$body5a = @{
    data = "F2026/06/25 10:03:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:03:00.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body5a

Write-Host ">>> 等待 61 秒... <<<" -ForegroundColor Red
Start-Sleep -Seconds 61

Write-Host "--- Step 2: 仍在上行1楼 (超过60秒未移动) ---" -ForegroundColor Yellow
$body5b = @{
    data = "F2026/06/25 10:04:01/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:04:01.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body5b

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Case 6: 超速运行 (SPEED_ABNORMAL)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "--- Step 1: 平层1楼 (基准方向) ---" -ForegroundColor Yellow
$body6a = @{
    data = "F2026/06/25 10:05:00/$DEV/01000000000000510000000000100021 3005000000000053c3d00063000000220e"
    time = "10:05:00.000"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body6a

Start-Sleep -Milliseconds 500

Write-Host "--- Step 2: 平层->上行 (方向变化, speed采样点1) ---" -ForegroundColor Yellow
$body6b = @{
    data = "F2026/06/25 10:05:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e"
    time = "10:05:00.100"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body6b

Start-Sleep -Milliseconds 500

Write-Host "--- Step 3: 1楼->2楼 (楼层变化, speed=2.8/0.4=7.0m/s > 3.0) ---" -ForegroundColor Yellow
$body6c = @{
    data = "F2026/06/25 10:05:00/$DEV/02000000000000510000000000100021 3409000000000053c3d00063000000220e"
    time = "10:05:00.500"
    elevatorID = $DEV
}
Invoke-RestMethod -Uri $BASE -Method Post -Body $body6c

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " 验证 SQL 查询" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "-- elevator_history 最近记录 --"
Write-Host "SELECT device_id, created_at, current_floor, direction, speed, door_status, alarm"
Write-Host "FROM elevator_history WHERE device_id='$DEV' ORDER BY created_at DESC LIMIT 10;"
Write-Host ""
Write-Host "-- alarm_event 最近记录 --"
Write-Host "SELECT device_id, created_at, rule_name, event_type, alarm_level, active, title"
Write-Host "FROM alarm_event WHERE device_id='$DEV' ORDER BY created_at DESC LIMIT 10;"
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " 验收完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
