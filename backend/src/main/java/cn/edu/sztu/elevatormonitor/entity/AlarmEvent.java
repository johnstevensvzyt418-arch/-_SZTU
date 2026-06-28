package cn.edu.sztu.elevatormonitor.entity;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 告警事件 — JPA 实体，映射 alarm_event 表。
 *
 * 告警生命周期:
 *   eventType = FIRED   → 告警触发
 *   eventType = CLEARED → 告警恢复
 */
@Entity
@Table(name = "alarm_event", indexes = {
        @Index(name = "idx_alarm_device_time", columnList = "device_id, created_at"),
        @Index(name = "idx_alarm_rule",         columnList = "rule_name"),
        @Index(name = "idx_alarm_active",       columnList = "active")
})
public class AlarmEvent {

    /** 告警触发 */
    public static final String TYPE_FIRED = "FIRED";
    /** 告警恢复 */
    public static final String TYPE_CLEARED = "CLEARED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备ID */
    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    /** 记录时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 事件类型: FIRED / CLEARED */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    /** 规则名称，如 LONG_IDLE */
    @Column(name = "rule_name", nullable = false, length = 64)
    private String ruleName;

    /** 告警级别: INFO / WARN / CRITICAL */
    @Column(name = "alarm_level", nullable = false, length = 16)
    private String alarmLevel;

    /** 告警标题 */
    @Column(name = "title", length = 128)
    private String title;

    /** 告警详情 */
    @Column(name = "detail", length = 512)
    private String detail;

    /** 触发时的当前楼层 */
    @Column(name = "current_floor", length = 8)
    private String currentFloor;

    /** 触发时的速度 */
    @Column(name = "speed")
    private Double speed;

    /** 是否仍处于激活状态 (FIRED=true, CLEARED=false) */
    @Column(name = "active", nullable = false)
    private Boolean active;

    // ==================== 工厂方法 ====================

    public static AlarmEvent fire(String deviceId, String ruleName, AlarmLevel level,
                                  String title, String detail,
                                  String currentFloor, Double speed) {
        AlarmEvent e = new AlarmEvent();
        e.deviceId = deviceId;
        e.createdAt = LocalDateTime.now();
        e.eventType = TYPE_FIRED;
        e.ruleName = ruleName;
        e.alarmLevel = level.name();
        e.title = title;
        e.detail = detail;
        e.currentFloor = currentFloor;
        e.speed = speed;
        e.active = true;
        return e;
    }

    public static AlarmEvent clear(String deviceId, String ruleName, String title) {
        AlarmEvent e = new AlarmEvent();
        e.deviceId = deviceId;
        e.createdAt = LocalDateTime.now();
        e.eventType = TYPE_CLEARED;
        e.ruleName = ruleName;
        e.alarmLevel = AlarmLevel.INFO.name();
        e.title = title + " - 已恢复";
        e.detail = "告警条件消失，自动恢复";
        e.active = false;
        return e;
    }

    // ==================== JSON 序列化（推送前端用） ====================

    public String toJson() {
        return "{\"Device\":\"" + nvl(deviceId) + "\"" +
                ",\"EventType\":\"" + nvl(eventType) + "\"" +
                ",\"RuleName\":\"" + nvl(ruleName) + "\"" +
                ",\"Level\":\"" + nvl(alarmLevel) + "\"" +
                ",\"Title\":\"" + escape(nvl(title)) + "\"" +
                ",\"Detail\":\"" + escape(nvl(detail)) + "\"" +
                ",\"Floor\":\"" + nvl(currentFloor) + "\"" +
                ",\"Speed\":\"" + (speed == null ? "" : speed.toString()) + "\"" +
                ",\"Time\":\"" + (createdAt == null ? "" : createdAt.toString()) + "\"}";
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(String alarmLevel) { this.alarmLevel = alarmLevel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(String currentFloor) { this.currentFloor = currentFloor; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
