package cn.edu.sztu.elevatormonitor.services;

import cn.edu.sztu.elevatormonitor.dto.AlarmStatistics;
import cn.edu.sztu.elevatormonitor.entity.repository.AlarmEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警统计服务 — 聚合查询 + 数据组装。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>全部聚合在数据库侧完成 (GROUP BY + COUNT)，不拉取明细到内存</li>
 *   <li>趋势数据自动补零，确保前端折线图连续</li>
 *   <li>Top N 在 Service 层截断，方便未来参数化</li>
 * </ul>
 */
@Service
public class AlarmStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmStatisticsService.class);

    private static final DateTimeFormatter HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    /** Top N 默认数量 */
    private static final int DEFAULT_TOP_N = 10;

    private final AlarmEventJpaRepository alarmRepo;

    public AlarmStatisticsService(AlarmEventJpaRepository alarmRepo) {
        this.alarmRepo = alarmRepo;
    }

    /**
     * 获取告警统计面板全部数据。
     *
     * @return 聚合统计结果
     */
    public AlarmStatistics getStatistics() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);

        AlarmStatistics stats = new AlarmStatistics();

        // 1. 今日总数
        stats.setTodayTotal(alarmRepo.countTodayFired(todayStart));

        // 2. 按级别统计
        stats.setByLevel(mapLevelCounts(alarmRepo.countByLevelToday(todayStart)));

        // 3. 按规则 Top N
        stats.setTopRules(limit(mapRuleCounts(alarmRepo.topRulesToday(todayStart)), DEFAULT_TOP_N));

        // 4. 按设备 Top N
        stats.setTopDevices(limit(mapDeviceCounts(alarmRepo.topDevicesToday(todayStart)), DEFAULT_TOP_N));

        // 5. 24小时趋势 (补零)
        stats.setTrend(buildTrend(since24h));

        LOGGER.debug("[Statistics] 统计完成: todayTotal={}, levels={}, rules={}, devices={}, trendPoints={}",
                stats.getTodayTotal(),
                stats.getByLevel().size(),
                stats.getTopRules().size(),
                stats.getTopDevices().size(),
                stats.getTrend().size());

        return stats;
    }

    // ==================== Object[] → DTO 映射 ====================

    private List<AlarmStatistics.LevelCount> mapLevelCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AlarmStatistics.LevelCount((String) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    private List<AlarmStatistics.RuleCount> mapRuleCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AlarmStatistics.RuleCount((String) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    private List<AlarmStatistics.DeviceCount> mapDeviceCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AlarmStatistics.DeviceCount((String) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    /**
     * 构建24小时逐小时趋势，缺失时段补零。
     */
    private List<AlarmStatistics.TrendPoint> buildTrend(LocalDateTime since) {
        // 数据库查询已有数据的小时
        List<Object[]> rawRows = alarmRepo.hourlyTrendRaw(since);

        // 转为 Map<小时字符串, count>
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (Object[] row : rawRows) {
            String hour = (String) row[0];
            long count = ((Number) row[1]).longValue();
            countMap.put(hour, count);
        }

        // 生成完整的24小时序列 (逐小时补零)
        List<AlarmStatistics.TrendPoint> trend = new ArrayList<>(24);
        LocalDateTime cursor = since.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        while (!cursor.isAfter(now)) {
            String hourKey = cursor.format(HOUR_FMT);
            long count = countMap.getOrDefault(hourKey, 0L);
            trend.add(new AlarmStatistics.TrendPoint(hourKey, count));
            cursor = cursor.plusHours(1);
        }

        return trend;
    }

    /** 截取前 N 条 */
    private static <T> List<T> limit(List<T> list, int n) {
        return list.stream().limit(n).collect(Collectors.toList());
    }
}
