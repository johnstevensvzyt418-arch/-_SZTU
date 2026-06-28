package cn.edu.sztu.elevatormonitor.services;

import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.repository.AlarmEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警历史查询服务 — 基于 JPA Specification 的动态组合查询。
 *
 * <h3>支持的筛选维度</h3>
 * <ul>
 *   <li>deviceId   — 设备ID，精确匹配</li>
 *   <li>alarmLevel — 告警级别 (INFO/WARN/CRITICAL)，精确匹配</li>
 *   <li>startTime  — 时间范围起始 (含)，格式 {@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>endTime    — 时间范围结束 (含)，格式 {@code yyyy-MM-dd HH:mm:ss}</li>
 * </ul>
 *
 * <p>所有筛选条件均为可选，未传即不做过滤。结果按创建时间倒序 (最新优先)。</p>
 */
@Service
public class AlarmQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmQueryService.class);

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 单页最大条数，防止深度分页拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    private final AlarmEventJpaRepository alarmRepo;

    public AlarmQueryService(AlarmEventJpaRepository alarmRepo) {
        this.alarmRepo = alarmRepo;
    }

    /**
     * 组合查询告警历史。
     *
     * @param deviceId   设备ID (可选)
     * @param alarmLevel 告警级别 (可选)
     * @param startTime  起始时间字符串 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param endTime    结束时间字符串 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param page       页码 (0-based)
     * @param size       每页条数 (1~100)
     * @return 分页告警记录
     */
    public Page<AlarmEvent> queryHistory(String deviceId,
                                         String alarmLevel,
                                         String startTime,
                                         String endTime,
                                         int page,
                                         int size) {
        // 安全上下界
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        Specification<AlarmEvent> spec = buildSpec(deviceId, alarmLevel, start, end);

        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        LOGGER.debug("[AlarmQuery] 查询告警历史: deviceId={}, level={}, start={}, end={}, page={}, size={}",
                deviceId, alarmLevel, start, end, safePage, safeSize);

        return alarmRepo.findAll(spec, pageable);
    }

    /**
     * 动态构建 JPA Specification。
     * 所有条件用 AND 组合，未传条件则忽略。
     */
    private Specification<AlarmEvent> buildSpec(String deviceId,
                                                 String alarmLevel,
                                                 LocalDateTime start,
                                                 LocalDateTime end) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (deviceId != null && !deviceId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId.trim()));
            }
            if (alarmLevel != null && !alarmLevel.trim().isEmpty()) {
                String level = alarmLevel.trim().toUpperCase();
                predicates.add(cb.equal(root.get("alarmLevel"), level));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 解析时间字符串，支持两种格式。
     * 解析失败返回 null，由 Specification 忽略该条件。
     */
    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        String trimmed = timeStr.trim();
        try {
            return LocalDateTime.parse(trimmed, TIME_FMT);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException e2) {
                LOGGER.warn("[AlarmQuery] 时间格式解析失败: '{}'", trimmed);
                return null;
            }
        }
    }
}
