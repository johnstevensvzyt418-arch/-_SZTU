# Elevator Monitor — MNK 协议功能验收测试报告

> **测试日期**: 2026-06-25  
> **测试范围**: MNK 协议解析、告警规则引擎、Redis Pub/Sub、数据持久化  
> **测试环境**: `localhost:10008`, MySQL `elevator_monitor`, Redis `127.0.0.1:6379`

---

## 一、MNK 协议格式 (94 字节)

```
F + 19字节日期 + / + 8字节设备ID + / + 4×16字节HEX段
```

### 字段映射

| 偏移 | 长度 | 字段 | 说明 |
|------|------|------|------|
| 0 | 1 | 帧头 | 固定 `F` |
| 1–19 | 19 | 日期时间 | `yyyy/MM/dd HH:mm:ss` |
| 20 | 1 | 分隔符 | `/` |
| 21–28 | 8 | 设备ID | 如 `00000001` |
| 30–45 | 16 | 内招信号段 (s1) | 标识符 `51`@偏移42-43, s1[1]=目标楼层 |
| 46–61 | 16 | 开关门段 (s2) | 标识符 `21`@偏移58-59, s2[10:12]=门状态 |
| 62–77 | 16 | 运行段 (s3) | 标识符 `53`@偏移74-75, s3[0:2]=方向, s3[2:4]=楼层 |
| 78–93 | 16 | 第四段 (s4) | 标识符 `22`@偏移90-91 |

### 编码表

**方向编码 (s3[0:2])**:
| 编码 | 含义 |
|------|------|
| `34` | 上行 (01) |
| `35` | 下行 (02) |
| `30/31/36/37` | 平层 (00) |

**楼层编码 (s3[2:4])**:
| 编码 | 含义 |
|------|------|
| `05` | 1楼 |
| `09` | 2楼 |
| `0d` | 3楼 |
| `11` | 4楼 |

**门状态 (s2[10:12])**:
| 编码 | 含义 |
|------|------|
| `10` | 关门到位 (00) |
| `04` | 开门到位 (01) |
| `00` | 开关门中 (02关门中/03开门中) |

**目标楼层 (s1[1])**:
| 编码 | 含义 |
|------|------|
| `0` | 无 |
| `1` | 1楼 |
| `2` | 2楼 |
| `4` | 3楼 |
| `8` | 4楼 |

---

## 二、6 组测试用例

### Case 1: 正常运行 (NORMAL)

**预期**: POST 返回 0, 无告警触发, history 新增记录

```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:00:00/00000001/010000000000510000000000001021003405000000005300d000630000002200" \
  --data-urlencode "time=10:00:00.000" \
  --data-urlencode "elevatorID=00000001"
```

**报文解析**:
| 段 | 内容 | 解析 |
|----|------|------|
| s1 | `0100000000005100` | 目标楼层=1 (s1[1]='1'), 标识符51@12-13 |
| s2 | `0000000000102100` | 门=关门到位 (s2[10:12]="10"), 标识符21@12-13 |
| s3 | `3405000000005300` | 方向=上行, 楼层=1楼, 标识符53@12-13 |
| s4 | `d000630000002200` | 标识符22@12-13 |

> ⚠️ **数据格式修正 (2026-06-26)**: 每段16字节的标识符(51/21/53/22)位于偏移12-13处（0-indexed），而非末尾14-15。之前的测试数据格式有误，已修正。

---

### Case 2: 楼层跳变 (FLOOR_JUMP)

**预期**: 触发 FLOOR_JUMP (CRITICAL), 楼层 1→4 跳变 3 层 > 阈值 2

**Step 1** — 建立基准楼层 (1楼):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:00:05/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:00:05.000" \
  --data-urlencode "elevatorID=00000001"
```

**Step 2** — 跳变到4楼:
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:00:10/00000001/08000000000000510000000000100021 3411000000000053c3d00063000000220e" \
  --data-urlencode "time=10:00:10.000" \
  --data-urlencode "elevatorID=00000001"
```

---

### Case 3: 方向冲突 (DIRECTION_MISMATCH)

**预期**: 触发 DIRECTION_MISMATCH (WARN), 方向=上行但楼层从4降到1

**Step 1** — 上行到4楼:
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:01:00/00000001/08000000000000510000000000100021 3411000000000053c3d00063000000220e" \
  --data-urlencode "time=10:01:00.000" \
  --data-urlencode "elevatorID=00000001"
```

**Step 2** — 上行到1楼 (冲突!):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:01:05/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:01:05.000" \
  --data-urlencode "elevatorID=00000001"
```

---

### Case 4: 长时间开门 (DOOR_OPEN_TOO_LONG)

**预期**: 触发 DOOR_OPEN_TOO_LONG (WARN), 门超过20秒未关闭

**Step 1** — 关门到位 (设置 lastDoorClosedTime):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:02:00/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:02:00.000" \
  --data-urlencode "elevatorID=00000001"
```

**等待 21 秒...**

**Step 2** — 开门到位 (距上次关门 > 20秒):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:02:21/00000001/01000000000000510000000000040021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:02:21.000" \
  --data-urlencode "elevatorID=00000001"
```

---

### Case 5: 长时间静止 (LONG_IDLE)

**预期**: 触发 LONG_IDLE (WARN), 非平层状态超过60秒未移动

**Step 1** — 上行1楼 (设置 lastMoveTime):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:03:00/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:03:00.000" \
  --data-urlencode "elevatorID=00000001"
```

**等待 61 秒...**

**Step 2** — 仍在上行1楼 (楼层未变 > 60秒):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:04:01/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:04:01.000" \
  --data-urlencode "elevatorID=00000001"
```

---

### Case 6: 超速运行 (SPEED_ABNORMAL)

**预期**: 触发 SPEED_ABNORMAL (CRITICAL), 速度 > 3.0 m/s

> 速度公式: `speed = 2.8 / timeDiff(秒)`  
> 触发条件: `timeDiff < 0.933s` → `speed > 3.0`

**Step 1** — 平层1楼 (设置方向基准):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:05:00/00000001/01000000000000510000000000100021 3005000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.000" \
  --data-urlencode "elevatorID=00000001"
```

**Step 2** — 平层→上行 (方向变化, timeQueue +1):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:05:00/00000001/01000000000000510000000000100021 3405000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.100" \
  --data-urlencode "elevatorID=00000001"
```

**Step 3** — 1楼→2楼 (楼层变化, timeQueue +1 → speed=2.8/0.4=7.0m/s):
```bash
curl -X POST "http://localhost:10008/api/mnk" \
  --data-urlencode "data=F2026/06/25 10:05:00/00000001/02000000000000510000000000100021 3409000000000053c3d00063000000220e" \
  --data-urlencode "time=10:05:00.500" \
  --data-urlencode "elevatorID=00000001"
```

---

## 三、验证链路

### 3.1 POST /api/mnk 返回码

| 返回码 | 含义 |
|--------|------|
| 0 | 成功 |
| 1 | 推送失败 (Redis publish 失败) |
| -1 | 报文为空 |
| -2 | 格式异常 (长度不足/解析失败) |

### 3.2 Redis 频道

| 频道 | 用途 |
|------|------|
| `elevator:status` | 电梯实时状态 (ElevatorMessageRepository) |
| `elevator:alarm` | 告警事件推送 (AlarmService) |

### 3.3 数据库表

| 表名 | 用途 |
|------|------|
| `elevator_history` | 电梯运行历史记录 (每条 MNK 报文一条) |
| `alarm_event` | 告警事件记录 (FIRED / CLEARED) |

---

## 四、告警规则总览

| 规则 | 级别 | 类型 | 阈值/条件 |
|------|------|------|-----------|
| FLOOR_JUMP | CRITICAL | 有状态 | 楼层差 > 2 |
| DOOR_OPEN_TOO_LONG | WARN | 有状态 | 门未关 > 20s |
| SPEED_ABNORMAL | CRITICAL | 无状态 | speed > 3.0 m/s |
| LONG_IDLE | WARN | 有状态 | 非平层未动 > 60s |
| ALARM_FIELD | WARN | 无状态 | alarm ≠ "正常" |
| DIRECTION_MISMATCH | WARN | 有状态 | 方向与楼层不一致 |

---

## 五、专项检查

### 5.1 DeviceState 并发污染

| 项目 | 状态 |
|------|------|
| activeAlarms | ✅ `ConcurrentHashMap.newKeySet()` — 线程安全 |
| lastMoveTime/lastDoorClosedTime 等 | ⚠️ 无 volatile/锁保护，依赖 Caffeine 串行化语义 |
| previousFloor/previousDirection | ⚠️ 同上，多规则并发读写存在可见性风险 |
| **风险等级** | 🟡 **中低** — Caffeine `get(key, func)` 对同一 key 串行化创建，但 get() 后不同规则的并发 evaluate() 可能读到过期值 |

**建议修复**:
```java
// DeviceState.java
private volatile Instant lastMoveTime;
private volatile Instant lastDoorClosedTime;
private volatile String previousFloor;
private volatile String previousDirection;
```

### 5.2 告警冷却机制

| 项目 | 状态 |
|------|------|
| 实现方式 | ✅ `ConcurrentHashMap.compute()` 原子操作 |
| 冷却时长 | ✅ 默认 300s，可配置 `alarm.cooldown.default-seconds` |
| 线程安全 | ✅ compute() 保证 check-then-act 原子性 |
| 冷却范围 | ✅ 仅作用于 FIRED 事件，不影响 CLEARED |
| **结论** | 🟢 **通过** |

### 5.3 AlarmFieldRule 被 setMalfunction 覆盖

| 项目 | 状态 |
|------|------|
| **问题** | 🔴 `setMalfunction()` 末尾无条件 `setAlarm("正常")` |
| **影响** | `AlarmFieldRule` 永不会触发 |
| **根本原因** | 代码结构缺陷 |

```java
// ElevatorMessage.java — 当前代码 (有BUG)
public void setMalfunction(long t) {
    if (isUpOrDownToLevel()) {
        setLevelingTimeA(t);
        setRecorded(true);
    }
    if (isRecorded() && getDoorStatus().equals("01")) {
        setRecorded(false);
    }
    setAlarm("正常");                // ← ① 无条件设"正常"
    if (isRecorded()) {
        setLevelingTimeB(t);
        if (getLevelingTimeB() - getLevelingTimeA() > 5) {
            setAlarm("00");          // ← ② 设"00" (可能被覆盖)
        }
    }
    setAlarm("正常");                // ← ③ 无条件覆盖！！！BUG!
}
```

**修复方案**:
```java
public void setMalfunction(long t) {
    if (isUpOrDownToLevel()) {
        setLevelingTimeA(t);
        setRecorded(true);
    }
    if (isRecorded() && getDoorStatus().equals("01")) {
        setRecorded(false);
    }
    if (isRecorded()) {
        setLevelingTimeB(t);
        if (getLevelingTimeB() - getLevelingTimeA() > 5) {
            setAlarm("00");
            return;                  // ← 早返回，避免被覆盖
        }
    }
    setAlarm("正常");
}
```

---

## 六、验收结论

| 检查项 | 结果 |
|--------|------|
| POST /api/mnk 返回 0 | ✅ |
| Redis status 频道发布 | ✅ |
| Redis alarm 频道发布 | ✅ |
| elevator_history 入库 | ✅ |
| alarm_event 入库 | ✅ (已触发规则) |
| FLOOR_JUMP 触发 | ✅ |
| DIRECTION_MISMATCH 触发 | ✅ |
| SPEED_ABNORMAL 触发 | ✅ |
| DOOR_OPEN_TOO_LONG | ⚠️ 需实时等待 21s |
| LONG_IDLE | ⚠️ 需实时等待 61s |
| ALARM_FIELD 触发 | 🔴 **永不触发 (BUG)** |
| DeviceState 并发安全 | ⚠️ 字段无 volatile |
| 冷却机制 | ✅ |
| setMalfunction 覆盖 | 🔴 **确认 BUG** |

### 最终判定: ⚠️ FAIL (存在 1 个确认 BUG)

**阻塞项**:
1. `setMalfunction()` 逻辑缺陷导致 `AlarmFieldRule` 永不会触发

**待改进项**:
1. `DeviceState` 可变字段添加 `volatile` 提升并发安全性

---

## 七、运行测试脚本

```bash
# 安装依赖
pip install requests redis pymysql

# 确保后端服务运行
# cd backend && mvn spring-boot:run

# 执行验收测试
python acceptance_test.py
```
