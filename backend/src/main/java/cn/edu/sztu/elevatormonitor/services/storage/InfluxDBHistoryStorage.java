package cn.edu.sztu.elevatormonitor.services.storage;

import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 历史数据存储 — 预留实现。
 *
 * 激活方式: elevator.storage.type=influxdb
 *
 * 未来接入步骤:
 *   1. 引入 influxdb-client-java 依赖
 *   2. 配置 spring.influx.url / token / bucket / org
 *   3. 将 ElevatorHistoryEntity 转换为 Point 写入
 *
 * InfluxDB Point 映射建议:
 *   - measurement: elevator_history
 *   - tags:        deviceId, direction, doorStatus
 *   - fields:      currentFloor, targetFloor, speed, alarm, runTimes, distance
 *   - timestamp:   createdAt (LocalDateTime → Instant)
 */
@Component
@ConditionalOnProperty(name = "elevator.storage.type", havingValue = "influxdb")
public class InfluxDBHistoryStorage implements IHistoryStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBHistoryStorage.class);

    public InfluxDBHistoryStorage() {
        LOGGER.warn("[InfluxDB] 存储后端已激活，但为预留桩实现，数据暂不写入。"
                + "请完成 influxdb-client-java 集成后替换本实现。");
    }

    @Override
    public boolean save(ElevatorHistoryEntity entity) {
        // TODO: 实现 InfluxDB Point 写入
        // WriteApi writeApi = influxDBClient.makeWriteApi();
        // Point point = Point.measurement("elevator_history")
        //         .addTag("deviceId", entity.getDeviceId())
        //         .addField("currentFloor", entity.getCurrentFloor())
        //         .addField("speed", entity.getSpeed())
        //         .time(entity.getCreatedAt().toInstant(ZoneOffset.UTC), WritePrecision.MS);
        // writeApi.writePoint(point);
        LOGGER.debug("[InfluxDB] (桩) 跳过写入, deviceId={}", entity.getDeviceId());
        return true;
    }

    @Override
    public String storageType() {
        return "influxdb";
    }
}
