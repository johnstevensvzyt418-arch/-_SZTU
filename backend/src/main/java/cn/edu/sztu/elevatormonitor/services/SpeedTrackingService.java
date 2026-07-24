package cn.edu.sztu.elevatormonitor.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 基于 Redis 的有状态速度追踪服务。
 *
 * <h3>问题背景</h3>
 * <p>原有速度计算依赖 ElevatorMessage 内部的 timeQueue/floorQueue，这些队列在每次
 * HTTP 请求中重新创建，无法跨请求累积状态，导致 SPEED_ABNORMAL 告警永远无法触发。</p>
 *
 * <h3>解决方案</h3>
 * <p>将每个设备的上次楼层和上报时间存储到 Redis Hash 中，跨请求计算瞬时速度：</p>
 * <pre>
 *   speed (m/s) = |currentFloor - lastFloor| × 2.8 / |currentTime - lastTime|
 * </pre>
 * <p>其中 2.8m 为单层楼高（与现有 distance 计算一致）。</p>
 *
 * <h3>Redis 数据结构</h3>
 * <pre>
 *   HSET elevator:speedtrack:{deviceId}
 *     lastFloor      → "01"
 *     lastTimeEpoch  → "1719312000"
 *     lastSpeed      → "0.05"       (缓存上次有效速度，避免被同秒/同楼层的后续报文覆盖为0)
 * </pre>
 *
 * @author bugfix
 * @since 0.1.5
 */
@Service
public class SpeedTrackingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpeedTrackingService.class);

    /** 单层楼高（米），与 ElevatorMessageRepository 中 distance 计算一致 */
    private static final double FLOOR_HEIGHT_M = 2.8;

    /** Redis Hash 键名前缀 */
    private static final String HASH_PREFIX = "elevator:speedtrack:";

    private final StringRedisTemplate stringRedisTemplate;

    public SpeedTrackingService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 计算并更新设备的速度。
     *
     * @param deviceId     设备ID
     * @param currentFloor 当前楼层（如 "01", "02"）
     * @param reportTime   上报时间字符串（HH:mm:ss 格式）
     * @param direction    运行方向（"00"=平层/停止, "01"=上行, "02"=下行）
     * @return 计算出的瞬时速度（m/s），平层时返回 0.0，首次上报返回 0.0
     */
    public double calculateAndUpdateSpeed(String deviceId, String currentFloor, String reportTime, String direction) {
        if (deviceId == null || currentFloor == null || reportTime == null) {
            return 0.0;
        }

        String hashKey = HASH_PREFIX + deviceId;
        long currentEpoch = parseTimeToEpochSeconds(reportTime);

        // 获取上次状态
        Map<Object, Object> lastState = stringRedisTemplate.opsForHash().entries(hashKey);
        String lastFloor = lastState != null ? (String) lastState.get("lastFloor") : null;
        String lastTimeStr = lastState != null ? (String) lastState.get("lastTimeEpoch") : null;
        String lastSpeedStr = lastState != null ? (String) lastState.get("lastSpeed") : null;

        // 首次上报，无历史数据
        if (lastFloor == null || lastTimeStr == null) {
            // 更新当前状态到 Redis
            stringRedisTemplate.opsForHash().put(hashKey, "lastFloor", currentFloor);
            stringRedisTemplate.opsForHash().put(hashKey, "lastTimeEpoch", String.valueOf(currentEpoch));
            stringRedisTemplate.opsForHash().put(hashKey, "lastSpeed", "0.0");
            LOGGER.debug("[SpeedTrack] 设备 {} 首次上报, floor={}, 速度=0.0", deviceId, currentFloor);
            return 0.0;
        }

        // 解析上次缓存的速度（用于楼层未变时保持显示）
        double cachedSpeed = 0.0;
        if (lastSpeedStr != null) {
            try {
                cachedSpeed = Double.parseDouble(lastSpeedStr);
            } catch (NumberFormatException e) {
                cachedSpeed = 0.0;
            }
        }

        // 计算楼层差和时间差
        long lastEpoch;
        int curFloor, prevFloor;
        try {
            lastEpoch = Long.parseLong(lastTimeStr);
            curFloor = Integer.parseInt(currentFloor);
            prevFloor = Integer.parseInt(lastFloor);
        } catch (NumberFormatException e) {
            LOGGER.warn("[SpeedTrack] 数值解析失败: deviceId={}, curFloor={}, lastFloor={}",
                    deviceId, currentFloor, lastFloor);
            return cachedSpeed;  // 解析失败时返回缓存速度，避免错误覆盖
        }

        int floorDiff = Math.abs(curFloor - prevFloor);
        long timeDiffSec = Math.abs(currentEpoch - lastEpoch);

        // 更新当前状态到 Redis（只在楼层变化时更新 lastFloor，始终更新时间戳）
        if (floorDiff > 0) {
            stringRedisTemplate.opsForHash().put(hashKey, "lastFloor", currentFloor);
        }
        stringRedisTemplate.opsForHash().put(hashKey, "lastTimeEpoch", String.valueOf(currentEpoch));

        // 平层（方向=00）→ 速度归零，重置缓存避免残留
        if ("00".equals(direction)) {
            stringRedisTemplate.opsForHash().put(hashKey, "lastSpeed", "0.0");
            LOGGER.debug("[SpeedTrack] 设备 {} 平层停止, 速度归零", deviceId);
            return 0.0;
        }

        // 同一秒内或楼层未变化 → 返回上次缓存的速度，保持前端速度显示不归零
        if (timeDiffSec == 0 || floorDiff == 0) {
            LOGGER.debug("[SpeedTrack] 设备 {} 时间差=0或楼层未变, 返回缓存速度={}m/s",
                    deviceId, String.format("%.2f", cachedSpeed));
            return cachedSpeed;
        }

        double distance = floorDiff * FLOOR_HEIGHT_M;
        double speed = distance / timeDiffSec;

        // 缓存本次计算的有效速度
        stringRedisTemplate.opsForHash().put(hashKey, "lastSpeed", String.valueOf(speed));

        LOGGER.info("[SpeedTrack] 设备 {}: {}F→{}F, 间隔{}s, 距离{}m, 速度={}m/s",
                deviceId, prevFloor, curFloor, timeDiffSec,
                String.format("%.1f", distance), String.format("%.2f", speed));

        return speed;
    }

    /**
     * 解析 HH:mm:ss 格式时间为当天 epoch 秒数。
     * 使用当天日期 + 上报时间组合，通过完整 epoch 秒数避免跨天问题。
     */
    private long parseTimeToEpochSeconds(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length < 3) return Instant.now().getEpochSecond();
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            // 使用当天日期 + 上报时间 → 完整 epoch 秒，解决跨午夜回绕问题
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalTime time = java.time.LocalTime.of(hours, minutes, seconds);
            return java.time.LocalDateTime.of(today, time)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toEpochSecond();
        } catch (Exception e) {
            LOGGER.warn("[SpeedTrack] 时间解析失败: {}", timeStr);
            return Instant.now().getEpochSecond();
        }
    }
}
