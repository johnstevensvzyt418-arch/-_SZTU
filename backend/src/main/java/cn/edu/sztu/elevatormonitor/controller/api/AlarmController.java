package cn.edu.sztu.elevatormonitor.controller.api;

import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.services.AlarmQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 告警历史查询 API。
 *
 * <pre>
 * GET /api/alarm/history
 *   ?deviceId=ELV-001
 *   &alarmLevel=WARN
 *   &startTime=2024-01-01 00:00:00
 *   &endTime=2024-01-31 23:59:59
 *   &page=0
 *   &size=20
 * </pre>
 *
 * 所有查询参数均为可选；未传的不参与过滤。
 */
@RestController
@RequestMapping("api/alarm")
@Validated
public class AlarmController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmController.class);

    private final AlarmQueryService alarmQueryService;

    public AlarmController(AlarmQueryService alarmQueryService) {
        this.alarmQueryService = alarmQueryService;
    }

    /**
     * 分页查询告警历史，支持多维度组合筛选，按时间倒序。
     *
     * @param deviceId   设备ID (可选)
     * @param alarmLevel 告警级别: INFO / WARN / CRITICAL (可选)
     * @param startTime  起始时间 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param endTime    结束时间 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param page       页码, 0-based (默认 0)
     * @param size       每页条数 (默认 20, 最大 100)
     * @return 分页告警记录
     */
    @GetMapping("/history")
    public ResponseEntity<Page<AlarmEvent>> queryHistory(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        LOGGER.info("[API] 告警历史查询: deviceId={}, level={}, start={}, end={}, page={}, size={}",
                deviceId, alarmLevel, startTime, endTime, page, size);

        Page<AlarmEvent> result = alarmQueryService.queryHistory(
                deviceId, alarmLevel, startTime, endTime, page, size);

        return ResponseEntity.ok(result);
    }
}
