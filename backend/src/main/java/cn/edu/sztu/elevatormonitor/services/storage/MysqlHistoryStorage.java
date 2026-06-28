package cn.edu.sztu.elevatormonitor.services.storage;

import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;
import cn.edu.sztu.elevatormonitor.entity.repository.ElevatorHistoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MySQL 历史数据存储实现。
 *
 * 激活条件: elevator.storage.type=mysql (默认值)
 * 若配置 elevator.storage.type=influxdb，则本 Bean 不加载。
 */
@Component
@ConditionalOnProperty(name = "elevator.storage.type", havingValue = "mysql", matchIfMissing = true)
public class MysqlHistoryStorage implements IHistoryStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MysqlHistoryStorage.class);

    private final ElevatorHistoryJpaRepository jpaRepository;

    public MysqlHistoryStorage(ElevatorHistoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean save(ElevatorHistoryEntity entity) {
        try {
            jpaRepository.save(entity);
            LOGGER.debug("[MySQL] 历史记录已保存, deviceId={}, floor={}",
                    entity.getDeviceId(), entity.getCurrentFloor());
            return true;
        } catch (Exception e) {
            LOGGER.error("[MySQL] 历史记录保存失败, deviceId={}", entity.getDeviceId(), e);
            return false;
        }
    }

    @Override
    public String storageType() {
        return "mysql";
    }
}
