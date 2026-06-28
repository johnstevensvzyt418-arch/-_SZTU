package cn.edu.sztu.elevatormonitor.entity.repository;

import cn.edu.sztu.elevatormonitor.entity.ElevatorHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 电梯历史数据 JPA Repository — 提供常用统计分析查询。
 */
@Repository
public interface ElevatorHistoryJpaRepository extends JpaRepository<ElevatorHistoryEntity, Long> {

    /** 按设备ID + 时间范围查询历史轨迹 */
    List<ElevatorHistoryEntity> findByDeviceIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            String deviceId, LocalDateTime start, LocalDateTime end);

    /** 按设备ID查询最近 N 条记录 */
    List<ElevatorHistoryEntity> findTop100ByDeviceIdOrderByCreatedAtDesc(String deviceId);

    /** 统计某设备在某时间段内的运行次数增量 */
    @Query("SELECT MAX(e.runTimes) - MIN(e.runTimes) FROM ElevatorHistoryEntity e " +
           "WHERE e.deviceId = :deviceId AND e.createdAt BETWEEN :start AND :end")
    Long countRunTimesByDevice(@Param("deviceId") String deviceId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    /** 统计所有设备报警记录 */
    @Query("SELECT e FROM ElevatorHistoryEntity e WHERE e.alarm <> '正常' " +
           "AND e.createdAt BETWEEN :start AND :end ORDER BY e.createdAt DESC")
    List<ElevatorHistoryEntity> findAlarms(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);
}
