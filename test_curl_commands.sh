#!/bin/bash
# ============================================================================
# Elevator Monitor — MNK 验收测试 curl 命令集
# 可直接复制到终端逐条执行
# 服务地址: http://localhost:10008
# ============================================================================

BASE="http://localhost:10008/api/mnk"
DEV="00000001"

echo "========================================"
echo " Case 1: 正常运行 (NORMAL)"
echo "========================================"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:00:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:00:00.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " Case 2: 楼层跳变 (FLOOR_JUMP)"
echo "========================================"
echo "--- Step 1: 1楼基准 ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:00:05/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:00:05.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

sleep 1

echo "--- Step 2: 跳变到4楼 (1→4, 差3层 > 阈值2) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:00:10/$DEV/08000000000000510000000000100021 3411000000000053c3d00063000000220e" \
  --data-urlencode "time=10:00:10.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " Case 3: 方向冲突 (DIRECTION_MISMATCH)"
echo "========================================"
echo "--- Step 1: 上行到4楼 ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:01:00/$DEV/08000000000000510000000000100021 3411000000000053c3d00063000000220e" \
  --data-urlencode "time=10:01:00.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

sleep 1

echo "--- Step 2: 上行到1楼 (冲突! 上行但楼层下降) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:01:05/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:01:05.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " Case 4: 长时间开门 (DOOR_OPEN_TOO_LONG)"
echo "========================================"
echo "--- Step 1: 关门到位 ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:02:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:02:00.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo ">>> 等待 21 秒... <<<"
sleep 21

echo "--- Step 2: 开门到位 (距上次关门 > 20秒) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:02:21/$DEV/01000000000000510000000000040021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:02:21.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " Case 5: 长时间静止 (LONG_IDLE)"
echo "========================================"
echo "--- Step 1: 上行1楼 ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:03:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:03:00.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo ">>> 等待 61 秒... <<<"
sleep 61

echo "--- Step 2: 仍在上行1楼 (超过60秒未移动) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:04:01/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:04:01.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " Case 6: 超速运行 (SPEED_ABNORMAL)"
echo "========================================"
echo "--- Step 1: 平层1楼 (基准方向) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:05:00/$DEV/01000000000000510000000000100021 3005000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.000" \
  --data-urlencode "elevatorID=$DEV"
echo ""

sleep 0.5

echo "--- Step 2: 平层→上行 (方向变化, 10:05:00.100) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:05:00/$DEV/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.100" \
  --data-urlencode "elevatorID=$DEV"
echo ""

sleep 0.5

echo "--- Step 3: 1楼→2楼 (楼层变化, speed=2.8/0.4=7.0m/s > 3.0) ---"
curl -s -X POST "$BASE" \
  --data-urlencode "data=F2026/06/25 10:05:00/$DEV/02000000000000510000000000100021 3409000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.500" \
  --data-urlencode "elevatorID=$DEV"
echo ""

echo "========================================"
echo " 验证数据库结果:"
echo "========================================"
echo ""
echo "--- elevator_history 最近记录 ---"
echo "SELECT device_id, created_at, current_floor, direction, speed, door_status, alarm"
echo "FROM elevator_history WHERE device_id='$DEV' ORDER BY created_at DESC LIMIT 10;"
echo ""
echo "--- alarm_event 最近记录 ---"
echo "SELECT device_id, created_at, rule_name, event_type, alarm_level, active, title"
echo "FROM alarm_event WHERE device_id='$DEV' ORDER BY created_at DESC LIMIT 10;"
echo ""
echo "========================================"
echo " 验收完成!"
echo "========================================"
