package cn.edu.sztu.elevatormonitor.entity.repository;

import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警事件 Repository — 基础查询 + JPA Specification + 聚合统计。
 */
@Repository
public interface AlarmEventJpaRepository extends JpaRepository<AlarmEvent, Long>,
                                                  JpaSpecificationExecutor<AlarmEvent> {

    /** 查询某设备当前激活的告警 */
    List<AlarmEvent> findByDeviceIdAndActiveTrue(String deviceId);

    /** 按时间范围查询所有告警 */
    List<AlarmEvent> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /** 查询最近的告警（用于仪表盘） */
    List<AlarmEvent> findTop50ByOrderByCreatedAtDesc();

    // ==================== 统计聚合查询 ====================

    /**
     * 统计今日告警总数。
     * 只统计 FIRED 事件，CLEARED 不计入。
     */
    @Query("SELECT COUNT(e) FROM AlarmEvent e "
            + "WHERE e.createdAt >= :todayStart AND e.eventType = 'FIRED'")
    long countTodayFired(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 按告警级别统计今日 FIRED 数量 (饼图数据)。
     * 返回 [level, count] 原始数组，由 Service 映射为 LevelCount。
     */
    @Query(value = "SELECT e.alarm_level, COUNT(*) AS cnt "
            + "FROM alarm_event e "
            + "WHERE e.created_at >= :todayStart AND e.event_type = 'FIRED' "
            + "GROUP BY e.alarm_level "
            + "ORDER BY cnt DESC",
            nativeQuery = true)
    List<Object[]> countByLevelToday(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 按规则统计今日 FIRED Top N (柱状图数据)。
     * 返回 [ruleName, count] 原始数组，由 Service 截断 Top N。
     */
    @Query(value = "SELECT e.rule_name, COUNT(*) AS cnt "
            + "FROM alarm_event e "
            + "WHERE e.created_at >= :todayStart AND e.event_type = 'FIRED' "
            + "GROUP BY e.rule_name "
            + "ORDER BY cnt DESC",
            nativeQuery = true)
    List<Object[]> topRulesToday(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 按设备统计今日 FIRED Top N (柱状图数据)。
     * 返回 [deviceId, count] 原始数组，由 Service 截断 Top N。
     */
    @Query(value = "SELECT e.device_id, COUNT(*) AS cnt "
            + "FROM alarm_event e "
            + "WHERE e.created_at >= :todayStart AND e.event_type = 'FIRED' "
            + "GROUP BY e.device_id "
            + "ORDER BY cnt DESC",
            nativeQuery = true)
    List<Object[]> topDevicesToday(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 最近24小时逐小时趋势 (折线图数据)。
     * 使用 MySQL DATE_FORMAT 按小时分组；缺失时段由 Service 补零。
     * 返回 [hourStr, count] 原始数组。
     */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00') AS hr, "
            + "COUNT(*) AS cnt "
            + "FROM alarm_event "
            + "WHERE created_at >= :since AND event_type = 'FIRED' "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:00') "
            + "ORDER BY hr ASC",
            nativeQuery = true)
    List<Object[]> hourlyTrendRaw(@Param("since") LocalDateTime since);
}
