package cn.edu.sztu.elevatormonitor.services.storage;

import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;

/**
 * 历史数据存储抽象接口 — 支持运行时切换 MySQL / InfluxDB / ClickHouse 等后端。
 *
 * 使用方式:
 *   1. 默认启用 MysqlHistoryStorage（JPA 实现）
 *   2. 未来只需新建 InfluxDBHistoryStorage 实现本接口，修改 Bean 注入即可
 */
public interface IHistoryStorage {

    /**
     * 保存单条电梯历史记录。
     * @param entity 历史记录快照
     * @return true 成功，false 失败
     */
    boolean save(ElevatorHistoryEntity entity);

    /**
     * 存储后端类型标识。
     */
    String storageType();
}
