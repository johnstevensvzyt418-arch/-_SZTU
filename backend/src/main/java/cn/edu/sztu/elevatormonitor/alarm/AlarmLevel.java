package cn.edu.sztu.elevatormonitor.alarm;

/**
 * 告警严重级别。
 */
public enum AlarmLevel {
    /** 提示 — 仅记录，不需要人工介入 */
    INFO,
    /** 警告 — 需关注，可能升级 */
    WARN,
    /** 严重 — 需立即处理 */
    CRITICAL
}
