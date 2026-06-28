package cn.edu.sztu.elevatormonitor.services;

import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import cn.edu.sztu.elevatormonitor.services.storage.IHistoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 历史数据异步持久化服务。
 *
 * 设计原则:
 *   1. @Async 确保写入不阻塞实时链路
 *   2. 存储失败仅记日志，不影响主流程
 *   3. 通过 IHistoryStorage 接口解耦具体存储后端
 */
@Service
public class HistoryPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryPersistenceService.class);

    private final IHistoryStorage storage;

    public HistoryPersistenceService(IHistoryStorage storage) {
        this.storage = storage;
        LOGGER.info("[History] 历史存储后端: {}", storage.storageType());
    }

    /**
     * 异步保存电梯历史记录。
     * 调用方无需等待返回，fire-and-forget。
     *
     * @param msg        已解析的 ElevatorMessage
     * @param reportTime 设备上报时间字符串
     */
    @Async
    public void saveAsync(ElevatorMessage msg, String reportTime) {
        try {
            ElevatorHistoryEntity entity = ElevatorHistoryEntity.from(msg, reportTime);
            storage.save(entity);
        } catch (Exception e) {
            LOGGER.error("[History] 异步保存异常, deviceId={}", msg.getDeviceId(), e);
        }
    }
}
