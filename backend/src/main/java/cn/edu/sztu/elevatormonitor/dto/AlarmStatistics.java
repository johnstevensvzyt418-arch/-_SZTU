package cn.edu.sztu.elevatormonitor.dto;

import java.util.List;

/**
 * 告警统计聚合响应 — 面向前端仪表盘/图表展示。
 *
 * <p>一次请求返回全部统计维度，减少网络往返。</p>
 */
public class AlarmStatistics {

    /** 今日告警总数 */
    private long todayTotal;

    /** 按告警级别统计 (饼图) */
    private List<LevelCount> byLevel;

    /** 按规则 Top N (柱状图) */
    private List<RuleCount> topRules;

    /** 按设备 Top N (柱状图) */
    private List<DeviceCount> topDevices;

    /** 最近24小时逐小时趋势 (折线图) */
    private List<TrendPoint> trend;

    // ==================== 构造 & Getter/Setter ====================

    public AlarmStatistics() {}

    public long getTodayTotal() { return todayTotal; }
    public void setTodayTotal(long todayTotal) { this.todayTotal = todayTotal; }
    public List<LevelCount> getByLevel() { return byLevel; }
    public void setByLevel(List<LevelCount> byLevel) { this.byLevel = byLevel; }
    public List<RuleCount> getTopRules() { return topRules; }
    public void setTopRules(List<RuleCount> topRules) { this.topRules = topRules; }
    public List<DeviceCount> getTopDevices() { return topDevices; }
    public void setTopDevices(List<DeviceCount> topDevices) { this.topDevices = topDevices; }
    public List<TrendPoint> getTrend() { return trend; }
    public void setTrend(List<TrendPoint> trend) { this.trend = trend; }

    // ==================== 内嵌统计单元 ====================

    /** 按级别统计 */
    public static class LevelCount {
        private String level;
        private long count;

        public LevelCount() {}
        public LevelCount(String level, long count) { this.level = level; this.count = count; }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    /** 按规则统计 */
    public static class RuleCount {
        private String ruleName;
        private long count;

        public RuleCount() {}
        public RuleCount(String ruleName, long count) { this.ruleName = ruleName; this.count = count; }

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    /** 按设备统计 */
    public static class DeviceCount {
        private String deviceId;
        private long count;

        public DeviceCount() {}
        public DeviceCount(String deviceId, long count) { this.deviceId = deviceId; this.count = count; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    /** 趋势数据点 (24小时逐小时) */
    public static class TrendPoint {
        private String time;
        private long count;

        public TrendPoint() {}
        public TrendPoint(String time, long count) { this.time = time; this.count = count; }

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
