package cn.edu.sztu.elevatormonitor.services;

import cn.edu.sztu.elevatormonitor.alarm.AlarmRuleEngine;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.entity.repository.AlarmEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警服务 — 异步评估 + 持久化 + 实时推送。
 *
 * 设计原则:
 *   1. @Async 确保告警评估不阻塞实时数据链路
 *   2. 告警事件双写: JPA → MySQL + Redis Pub → 前端
 *   3. 告警频道: elevator:alarm
 */
@Service
public class AlarmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmService.class);

    /** Redis 告警频道 */
    public static final String CHANNEL_ELEVATOR_ALARM = "elevator:alarm";

    private final AlarmRuleEngine engine;
    private final AlarmEventJpaRepository alarmRepo;
    private final StringRedisTemplate stringRedisTemplate;

    public AlarmService(AlarmRuleEngine engine,
                        AlarmEventJpaRepository alarmRepo,
                        StringRedisTemplate stringRedisTemplate) {
        this.engine = engine;
        this.alarmRepo = alarmRepo;
        this.stringRedisTemplate = stringRedisTemplate;
        LOGGER.info("[Alarm] 告警服务初始化完成, Redis频道={}", CHANNEL_ELEVATOR_ALARM);
    }

    /**
     * 异步评估告警、持久化、推送前端。
     * 调用方无需等待返回。
     *
     * @param msg 已解析的 ElevatorMessage
     */
    @Async("alarmExecutor")
    public void evaluateAsync(ElevatorMessage msg) {
        LOGGER.info("[Alarm] evaluateAsync 被调用, deviceId={}, floor={}, dir={}",
                msg.getDeviceId(), msg.getCurrentFloor(), msg.getDirection());
        try {
            List<AlarmEvent> events = engine.evaluate(msg);
            LOGGER.info("[Alarm] 规则评估完成, deviceId={}, 触发事件数={}",
                    msg.getDeviceId(), events.size());
            for (AlarmEvent event : events) {
                // 1. 持久化到 MySQL
                try {
                    alarmRepo.save(event);
                } catch (Exception e) {
                    LOGGER.error("[Alarm] 告警入库失败, deviceId={}, rule={}",
                            event.getDeviceId(), event.getRuleName(), e);
                }

                // 2. 实时推送到 Redis Pub/Sub → Go → WebSocket 前端
                try {
                    String json = event.toJson();
                    stringRedisTemplate.convertAndSend(CHANNEL_ELEVATOR_ALARM, json);
                    LOGGER.debug("[Alarm] 告警已推送: {}", json);
                } catch (Exception e) {
                    LOGGER.error("[Alarm] 告警推送失败, deviceId={}, rule={}",
                            event.getDeviceId(), event.getRuleName(), e);
                }
            }

            // 3. 将触发的告警回写到 status HSET，让前端告警灯实时联动
            if (!events.isEmpty()) {
                updateStatusAlarm(msg.getDeviceId(), events);
            }
        } catch (Exception e) {
            LOGGER.error("[Alarm] 告警评估异常, deviceId={}", msg.getDeviceId(), e);
        }
    }

    /**
     * 回写 elevator:status HSET 的 Alarm 字段并重新 PUBLISH，
     * 使前端通过正常的 status 消息即可看到告警灯变化。
     */
    private void updateStatusAlarm(String deviceId, List<AlarmEvent> events) {
        try {
            StringBuilder sb = new StringBuilder();
            for (AlarmEvent ae : events) {
                if (sb.length() > 0) sb.append(",");
                sb.append(ae.getRuleName());
            }
            String alarmValue = sb.toString();
            Object raw = stringRedisTemplate.opsForHash().get("elevator:status", deviceId);
            if (raw != null) {
                String json = raw.toString();
                String updated = json.replaceFirst("\"Alarm\":\"[^\"]*\"", "\"Alarm\":\"" + alarmValue + "\"");
                stringRedisTemplate.opsForHash().put("elevator:status", deviceId, updated);
                stringRedisTemplate.convertAndSend("elevator:status", updated);
            }
            LOGGER.info("[Alarm] status Alarm 已更新: deviceId={}, alarm={}", deviceId, alarmValue);
        } catch (Exception e) {
            LOGGER.error("[Alarm] 更新 status Alarm 失败: deviceId={}", deviceId, e);
        }
    }
}
