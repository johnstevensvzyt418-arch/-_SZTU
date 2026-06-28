package cn.edu.sztu.elevatormonitor.event;

import cn.edu.sztu.elevatormonitor.domain.event.ElevatorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 事件发布器 — 封装 Spring {@link ApplicationEventPublisher}，统一事件发布入口。
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li>隔离 Spring 基础设施依赖，未来可无缝切换为 Kafka/RabbitMQ</li>
 *   <li>统一日志追踪（eventId → 全链路可观测）</li>
 *   <li>发布失败不抛异常，避免影响主流程</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
@Component
public class EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisher.class);

    private final ApplicationEventPublisher springPublisher;

    public EventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    /**
     * 发布电梯领域事件。
     * 所有 {@code @EventListener} 方法将异步/同步接收此事件。
     *
     * @param event 电梯领域事件
     */
    public void publish(ElevatorEvent event) {
        try {
            LOGGER.debug("[EventBus] 发布事件: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId());
            springPublisher.publishEvent(event);
        } catch (Exception e) {
            LOGGER.error("[EventBus] 事件发布异常: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId(), e);
        }
    }
}
