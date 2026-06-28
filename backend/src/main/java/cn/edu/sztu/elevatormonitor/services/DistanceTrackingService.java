package cn.edu.sztu.elevatormonitor.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 基于 Redis 的有状态运行里程/次数追踪服务。
 *
 * <h3>问题背景</h3>
 * <p>原有 ElevatorMessage 的 distance/times 字段每次请求都从硬编码初始值
 * (1000/100) 开始，只做了请求内 +1，从未跨请求累积，导致前端永远显示假数据。</p>
 *
 * <h3>解决方案</h3>
 * <p>将每个设备的累计里程和运行次数存储到 Redis Hash 中，跨请求追踪：</p>
 * <pre>
 *   distance += |currentFloor - lastFloor|    (楼层变化时)
 *   times    += 1                             (每次请求)
 * </pre>
 *
 * <h3>Redis 数据结构</h3>
 * <pre>
 *   HSET elevator:cumul:{deviceId}
 *     distance  → "150"  (累计楼层变化次数, 乘以2.8得米数)
 *     times     → "42"   (累计请求次数)
 *     lastFloor → "03"   (上次楼层, 用于计算距离增量)
 * </pre>
 *
 * @author bugfix
 * @since 0.2.1
 */
@Service
public class DistanceTrackingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistanceTrackingService.class);

    private static final String HASH_PREFIX = "elevator:cumul:";

    private final StringRedisTemplate stringRedisTemplate;

    public DistanceTrackingService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 更新并返回设备的累计里程(楼层变化次数)和运行次数。
     *
     * @param deviceId     设备ID
     * @param currentFloor 当前楼层（如 "01", "02"）
     * @return CumulativeResult 包含最新的 distance(楼层变化次数) 和 times(请求次数)
     */
    public CumulativeResult updateAndGet(String deviceId, String currentFloor) {
        String hashKey = HASH_PREFIX + deviceId;

        // 读取当前累积值
        Map<Object, Object> state = stringRedisTemplate.opsForHash().entries(hashKey);
        int distance = parseInt(state, "distance");
        int times = parseInt(state, "times");
        String lastFloor = state != null ? (String) state.get("lastFloor") : null;

        // 运行次数 +1
        times++;

        // 楼层变化时累加里程
        if (lastFloor != null && currentFloor != null && !lastFloor.equals(currentFloor)) {
            try {
                int prev = Integer.parseInt(lastFloor);
                int curr = Integer.parseInt(currentFloor);
                int diff = Math.abs(curr - prev);
                distance += diff;
                LOGGER.info("[Cumul] 设备 {} 里程更新: {}→{}F, +{}层, 累计={}层",
                        deviceId, lastFloor, currentFloor, diff, distance);
            } catch (NumberFormatException e) {
                LOGGER.warn("[Cumul] 楼层解析失败: lastFloor={}, curFloor={}", lastFloor, currentFloor);
            }
        }

        // 写回 Redis
        stringRedisTemplate.opsForHash().put(hashKey, "distance", String.valueOf(distance));
        stringRedisTemplate.opsForHash().put(hashKey, "times", String.valueOf(times));
        stringRedisTemplate.opsForHash().put(hashKey, "lastFloor", currentFloor != null ? currentFloor : "");

        LOGGER.debug("[Cumul] 设备 {}: distance={}层, times={}次", deviceId, distance, times);
        return new CumulativeResult(distance, times);
    }

    private int parseInt(Map<Object, Object> state, String key) {
        if (state == null) return 0;
        Object val = state.get(key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 累积结果 DTO。
     */
    public static class CumulativeResult {
        /** 累计楼层变化次数 (乘以2.8得米数) */
        public final int distance;
        /** 累计运行次数 */
        public final int times;

        public CumulativeResult(int distance, int times) {
            this.distance = distance;
            this.times = times;
        }
    }
}
