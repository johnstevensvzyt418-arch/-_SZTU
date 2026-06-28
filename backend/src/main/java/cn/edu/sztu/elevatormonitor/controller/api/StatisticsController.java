package cn.edu.sztu.elevatormonitor.controller.api;

import cn.edu.sztu.elevatormonitor.dto.AlarmStatistics;
import cn.edu.sztu.elevatormonitor.services.AlarmStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警统计 API — 面向前端仪表盘/图表。
 *
 * <pre>
 * GET /api/alarm/statistics
 * </pre>
 *
 * <p>一次请求返回全部统计维度：今日总数、按级别、按规则 Top10、
 * 按设备 Top10、最近24小时逐小时趋势。</p>
 */
@RestController
@RequestMapping("api/alarm")
public class StatisticsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsController.class);

    private final AlarmStatisticsService statisticsService;

    public StatisticsController(AlarmStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 获取告警统计面板全部数据。
     *
     * @return 聚合统计，结构见 {@link AlarmStatistics}
     */
    @GetMapping("/statistics")
    public ResponseEntity<AlarmStatistics> getStatistics() {
        LOGGER.info("[API] 告警统计查询");
        AlarmStatistics stats = statisticsService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
