package cn.edu.sztu.elevatormonitor.event;

import cn.edu.sztu.elevatormonitor.domain.event.ElevatorEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;
import cn.edu.sztu.elevatormonitor.services.storage.IHistoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 历史数据持久化处理器 — 监听 {@link ElevatorEvent}，异步写入 MySQL。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>@Async 确保写入不阻塞事件总线</li>
 *   <li>写入失败仅记日志，不影响其他 Listener</li>
 *   <li>通过 IHistoryStorage 接口解耦具体存储后端 (MySQL/InfluxDB 可切换)</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
@Component
public class HistoryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryHandler.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final IHistoryStorage storage;

    public HistoryHandler(IHistoryStorage storage) {
        this.storage = storage;
        LOGGER.info("[HistoryHandler] 初始化完成, 存储后端={}", storage.storageType());
    }

    /**
     * 监听电梯事件，异步持久化历史记录。
     */
    @EventListener
    @Async
    public void onElevatorEvent(ElevatorEvent event) {
        try {
            ElevatorHistoryEntity entity = new ElevatorHistoryEntity();
            entity.setDeviceId(event.getDeviceId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setReportTime(event.getDeviceTime().format(DT_FMT));
            entity.setCurrentFloor(event.getCurrentFloor());
            entity.setTargetFloor(event.getTargetFloor());
            entity.setDirection(event.getDirection());
            entity.setSpeed(event.getSpeed());
            entity.setDoorStatus(event.getDoorStatus());
            entity.setElevatorStatus(event.getElevatorStatus());
            entity.setAlarm(event.getAlarm());
            entity.setRunTimes(event.getTimes());
            entity.setDistance(event.getDistance());
            entity.setRuntime(event.getRuntime());
            entity.setPassenger(event.getPassenger());

            storage.save(entity);
            LOGGER.debug("[HistoryHandler] 历史数据已保存: deviceId={}", event.getDeviceId());
        } catch (Exception e) {
            LOGGER.error("[HistoryHandler] 保存异常: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId(), e);
        }
    }
}
