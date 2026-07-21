package cn.edu.sztu.elevatormonitor.event;

import cn.edu.sztu.elevatormonitor.alarm.AlarmRuleEngine;
import cn.edu.sztu.elevatormonitor.domain.event.ElevatorEvent;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.entity.repository.AlarmEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告警事件处理器 — 监听 {@link ElevatorEvent}，异步执行告警规则评估与推送。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>将 ElevatorEvent 适配为 ElevatorMessage (兼容现有 AlarmRuleEngine)</li>
 *   <li>调用 AlarmRuleEngine.evaluate() 评估告警规则</li>
 *   <li>告警事件双写: JPA → MySQL + Redis Pub → 前端</li>
 * </ol>
 *
 * <h3>隔离性保证</h3>
 * <ul>
 *   <li>使用独立线程池 "alarmExecutor"，不阻塞事件发布线程</li>
 *   <li>告警处理失败不影响 History / Redis 处理器</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
@Component
public class AlarmHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmHandler.class);

    /** Redis 告警频道 */
    private static final String CHANNEL_ELEVATOR_ALARM = "elevator:alarm";

    private final AlarmRuleEngine alarmRuleEngine;
    private final AlarmEventJpaRepository alarmRepo;
    private final StringRedisTemplate stringRedisTemplate;

    public AlarmHandler(AlarmRuleEngine alarmRuleEngine,
                        AlarmEventJpaRepository alarmRepo,
                        StringRedisTemplate stringRedisTemplate) {
        this.alarmRuleEngine = alarmRuleEngine;
        this.alarmRepo = alarmRepo;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 监听电梯事件，异步执行告警评估。
     *
     * <p>使用 {@code @Async("alarmExecutor")} 确保告警处理不阻塞主流程。</p>
     */
    @EventListener
    @Async("alarmExecutor")
    public void onElevatorEvent(ElevatorEvent event) {
        LOGGER.info("[AlarmHandler] 收到事件: eventId={}, deviceId={}", event.getEventId(), event.getDeviceId());
        try {
            // 1. 适配为 ElevatorMessage（兼容现有引擎）
            ElevatorMessage msg = adaptToElevatorMessage(event);

            // 2. 规则评估
            List<AlarmEvent> alarmEvents = alarmRuleEngine.evaluate(msg);
            LOGGER.info("[AlarmHandler] 规则评估完成, deviceId={}, 触发数={}",
                    event.getDeviceId(), alarmEvents.size());

            // 3. 持久化 + 推送 + 回写 status HSET
            for (AlarmEvent alarm : alarmEvents) {
                persistAlarm(alarm);
                pushAlarm(alarm);
            }
            // 将触发的告警回写到 elevator:status HSET，让前端告警灯实时联动
            if (!alarmEvents.isEmpty()) {
                updateStatusAlarm(event.getDeviceId(), alarmEvents);
            }
        } catch (Exception e) {
            LOGGER.error("[AlarmHandler] 处理异常: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId(), e);
        }
    }

    /**
     * 将领域事件适配为 ElevatorMessage（过渡期兼容方案）。
     * 未来可直接改造 AlarmRuleEngine 接受 ElevatorEvent。
     */
    private ElevatorMessage adaptToElevatorMessage(ElevatorEvent event) {
        ElevatorMessage msg = new ElevatorMessage();
        msg.setDeviceId(event.getDeviceId());
        msg.setElevatorStatus(event.getElevatorStatus());
        msg.setDoorStatus(event.getDoorStatus());
        msg.setCurrentFloor(event.getCurrentFloor());
        msg.setTargetFloor(event.getTargetFloor());
        msg.setDirection(event.getDirection());
        msg.setSpeed(event.getSpeed());
        msg.setAlarm(event.getAlarm());
        msg.setPassenger(event.getPassenger());
        // 使用 ElevatorEvent 的设备时间格式化 runtime，而非当前系统时间
        if (event.getDeviceTime() != null) {
            msg.setRuntime(event.getDeviceTime().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        }
        msg.setDistance(event.getDistance());
        msg.setTimes(event.getTimes());
        return msg;
    }

    private void persistAlarm(AlarmEvent alarm) {
        try {
            alarmRepo.save(alarm);
        } catch (Exception e) {
            LOGGER.error("[AlarmHandler] 告警入库失败: deviceId={}, rule={}",
                    alarm.getDeviceId(), alarm.getRuleName(), e);
        }
    }

    private void pushAlarm(AlarmEvent alarm) {
        try {
            String json = alarm.toJson();
            stringRedisTemplate.convertAndSend(CHANNEL_ELEVATOR_ALARM, json);
            LOGGER.debug("[AlarmHandler] 告警已推送: {}", json);
        } catch (Exception e) {
            LOGGER.error("[AlarmHandler] 告警推送失败: deviceId={}, rule={}",
                    alarm.getDeviceId(), alarm.getRuleName(), e);
        }
    }

    /**
     * 将告警信息回写到 elevator:status HSET 的 Alarm 字段，
     * 使前端通过正常的 status WebSocket 消息即可看到告警灯变化。
     */
    private void updateStatusAlarm(String deviceId, List<AlarmEvent> alarmEvents) {
        try {
            // 只统计 FIRED 事件，CLEARED 事件的 ruleName 不应写入 Alarm 字段
            StringBuilder sb = new StringBuilder();
            for (AlarmEvent ae : alarmEvents) {
                if (!AlarmEvent.TYPE_FIRED.equals(ae.getEventType())) {
                    continue;
                }
                if (sb.length() > 0) sb.append(",");
                sb.append(ae.getRuleName());
            }
            String alarmValue = sb.toString();
            stringRedisTemplate.opsForHash().put("elevator:status", deviceId + ":alarm", alarmValue);
            // 同时更新主状态中的 Alarm 字段
            Object raw = stringRedisTemplate.opsForHash().get("elevator:status", deviceId);
            if (raw != null) {
                String json = raw.toString();
                // 简单替换 Alarm 字段值
                String updated = json.replaceFirst("\"Alarm\":\"[^\"]*\"", "\"Alarm\":\"" + alarmValue + "\"");
                stringRedisTemplate.opsForHash().put("elevator:status", deviceId, updated);
                // 重新 PUBLISH 更新后的状态，让前端告警灯实时变化
                stringRedisTemplate.convertAndSend("elevator:status", updated);
            }
            LOGGER.info("[AlarmHandler] status Alarm 已更新: deviceId={}, alarm={}", deviceId, alarmValue);
        } catch (Exception e) {
            LOGGER.error("[AlarmHandler] 更新 status Alarm 失败: deviceId={}", deviceId, e);
        }
    }
}
