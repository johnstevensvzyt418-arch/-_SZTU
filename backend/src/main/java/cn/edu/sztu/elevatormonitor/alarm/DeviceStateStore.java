package cn.edu.sztu.elevatormonitor.alarm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 设备运行时状态存储 — 基于 Caffeine 的线程安全缓存，支持自动过期回收。
 *
 * <h3>内存回收策略</h3>
 * <ul>
 *   <li><b>容量上限:</b> 最大 10,000 条，超出后 W-TinyLFU 自动淘汰低频条目</li>
 *   <li><b>访问过期:</b> 24 小时未被访问 (get/getOrCreate) 的条目自动删除</li>
 *   <li><b>主动清理:</b> 设备离线时可调用 {@link #remove} 立即回收</li>
 * </ul>
 *
 * <p>每条电梯消息到达时，本 Store 提供该设备的 DeviceState。
 * 若设备首次出现，自动创建新状态对象。
 */
@Component
public class DeviceStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceStateStore.class);

    /** 缓存最大容量 */
    private static final long MAX_SIZE = 10_000L;

    /** 访问过期时间 (小时) */
    private static final int EXPIRE_HOURS = 24;

    private final Cache<String, DeviceState> cache;

    public DeviceStateStore() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterAccess(EXPIRE_HOURS, TimeUnit.HOURS)
                .recordStats()
                .removalListener((deviceId, state, cause) ->
                        LOGGER.info("[StateStore] 设备状态已回收: deviceId={}, cause={}",
                                deviceId, cause))
                .build();
        LOGGER.info("[StateStore] Caffeine 缓存初始化完成: maxSize={}, expireAfterAccess={}h",
                MAX_SIZE, EXPIRE_HOURS);
    }

    /**
     * 获取或创建设备状态 (原子操作)。
     */
    public DeviceState getOrCreate(String deviceId) {
        return cache.get(deviceId, DeviceState::new);
    }

    /**
     * 获取设备状态（可能为 null，不触发自动创建）。
     */
    public DeviceState get(String deviceId) {
        return cache.getIfPresent(deviceId);
    }

    /**
     * 移除设备状态（设备离线时主动清理）。
     */
    public void remove(String deviceId) {
        cache.invalidate(deviceId);
    }

    /**
     * 当前缓存条目估算数量。
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息 (命中率、淘汰数等)，用于运维监控。
     */
    public CacheStats stats() {
        return cache.stats();
    }
}
