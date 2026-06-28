package cn.edu.sztu.elevatormonitor.protocol;

import java.time.LocalDateTime;

/**
 * MNK 协议帧 — 协议层统一数据模型。
 *
 * <p>职责：承载 MNK 协议解析后的结构化数据，是协议解析层与应用层的唯一契约。
 * 本对象是纯数据结构（DTO），不包含任何业务逻辑。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>timestamp 统一为 {@link LocalDateTime}，消除 SimpleDateFormat/ParseException</li>
 *   <li>所有字段不可变（immutable），由 Builder 构造</li>
 *   <li>保留 rawData 用于调试/审计追踪</li>
 *   <li>protocolVersion 支持未来协议升级</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
public class MNKFrame {

    /** 协议版本，可用于未来协议升级兼容 */
    private final String protocolVersion;

    /** 设备上报时间（已解析为标准类型） */
    private final LocalDateTime timestamp;

    /** 设备唯一标识 */
    private final String deviceId;

    /** 电梯状态: 00=正常 */
    private final String elevatorStatus;

    /** 门状态: 00=关门到位 01=开门到位 02=关门中 03=开门中 */
    private final String doorStatus;

    /** 当前楼层 */
    private final String currentFloor;

    /** 目标楼层 */
    private final String targetFloor;

    /** 运行方向: 00=平层 01=上行 02=下行 */
    private final String direction;

    /** 报警状态 */
    private final String alarm;

    /** 乘客状态: 00=无人 01=有人 */
    private final String passenger;

    /** 运行速度 (m/s) */
    private final double speed;

    /** 运行时间（格式化字符串） */
    private final String runtime;

    /** 累计运行里程 */
    private final int distance;

    /** 累计运行次数 */
    private final int times;

    /** 原始数据（用于审计/调试，不参与业务逻辑） */
    private final String rawData;

    // ==================== 私有构造（强制使用 Builder） ====================

    private MNKFrame(Builder builder) {
        this.protocolVersion = builder.protocolVersion;
        this.timestamp = builder.timestamp;
        this.deviceId = builder.deviceId;
        this.elevatorStatus = builder.elevatorStatus;
        this.doorStatus = builder.doorStatus;
        this.currentFloor = builder.currentFloor;
        this.targetFloor = builder.targetFloor;
        this.direction = builder.direction;
        this.alarm = builder.alarm;
        this.passenger = builder.passenger;
        this.speed = builder.speed;
        this.runtime = builder.runtime;
        this.distance = builder.distance;
        this.times = builder.times;
        this.rawData = builder.rawData;
    }

    // ==================== Getters ====================

    public String getProtocolVersion() { return protocolVersion; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getDeviceId() { return deviceId; }
    public String getElevatorStatus() { return elevatorStatus; }
    public String getDoorStatus() { return doorStatus; }
    public String getCurrentFloor() { return currentFloor; }
    public String getTargetFloor() { return targetFloor; }
    public String getDirection() { return direction; }
    public String getAlarm() { return alarm; }
    public String getPassenger() { return passenger; }
    public double getSpeed() { return speed; }
    public String getRuntime() { return runtime; }
    public int getDistance() { return distance; }
    public int getTimes() { return times; }
    public String getRawData() { return rawData; }

    @Override
    public String toString() {
        return "MNKFrame{deviceId='" + deviceId + "', floor=" + currentFloor
                + ", direction=" + direction + ", door=" + doorStatus + '}';
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String protocolVersion = "1.0";
        private LocalDateTime timestamp;
        private String deviceId;
        private String elevatorStatus = "00";
        private String doorStatus;
        private String currentFloor;
        private String targetFloor;
        private String direction;
        private String alarm = "";
        private String passenger = "00";
        private double speed = -1.0;
        private String runtime = "";
        private int distance = -1;
        private int times = -1;
        private String rawData;

        public Builder protocolVersion(String v) { this.protocolVersion = v; return this; }
        public Builder timestamp(LocalDateTime v) { this.timestamp = v; return this; }
        public Builder deviceId(String v) { this.deviceId = v; return this; }
        public Builder elevatorStatus(String v) { this.elevatorStatus = v; return this; }
        public Builder doorStatus(String v) { this.doorStatus = v; return this; }
        public Builder currentFloor(String v) { this.currentFloor = v; return this; }
        public Builder targetFloor(String v) { this.targetFloor = v; return this; }
        public Builder direction(String v) { this.direction = v; return this; }
        public Builder alarm(String v) { this.alarm = v; return this; }
        public Builder passenger(String v) { this.passenger = v; return this; }
        public Builder speed(double v) { this.speed = v; return this; }
        public Builder runtime(String v) { this.runtime = v; return this; }
        public Builder distance(int v) { this.distance = v; return this; }
        public Builder times(int v) { this.times = v; return this; }
        public Builder rawData(String v) { this.rawData = v; return this; }

        public MNKFrame build() {
            if (deviceId == null || deviceId.isEmpty()) {
                throw new IllegalArgumentException("deviceId is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp is required");
            }
            return new MNKFrame(this);
        }
    }
}
