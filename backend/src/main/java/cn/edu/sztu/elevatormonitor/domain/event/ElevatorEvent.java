package cn.edu.sztu.elevatormonitor.domain.event;

import cn.edu.sztu.elevatormonitor.protocol.MNKFrame;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 电梯领域事件 — 在事件总线上传递的标准载荷。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>纯数据结构，不允许包含任何解析/业务逻辑</li>
 *   <li>携带 MNKFrame 的完整数据副本，Listener 无需回查源数据</li>
 *   <li>eventId 用于日志追踪和幂等去重</li>
 *   <li>occurredAt 记录事件发生时刻，独立于设备上报时间</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
public class ElevatorEvent {

    /** 事件唯一标识 */
    private final String eventId;

    /** 事件发生时刻（系统时间） */
    private final LocalDateTime occurredAt;

    /** 协议版本 */
    private final String protocolVersion;

    /** 设备上报时间 */
    private final LocalDateTime deviceTime;

    /** 设备ID */
    private final String deviceId;

    /** 电梯状态 */
    private final String elevatorStatus;

    /** 门状态 */
    private final String doorStatus;

    /** 当前楼层 */
    private final String currentFloor;

    /** 目标楼层 */
    private final String targetFloor;

    /** 运行方向 */
    private final String direction;

    /** 报警状态 */
    private final String alarm;

    /** 乘客状态 */
    private final String passenger;

    /** 运行速度 */
    private final double speed;

    /** 运行时间 */
    private final String runtime;

    /** 累计里程 */
    private final int distance;

    /** 累计次数 */
    private final int times;

    /** 原始报文（审计用） */
    private final String rawData;

    // ==================== 工厂方法：从 MNKFrame 创建 ====================

    /**
     * 从协议帧创建领域事件。
     * 这是唯一推荐的事件构造方式。
     */
    public static ElevatorEvent from(MNKFrame frame) {
        return from(frame, Clock.systemDefaultZone());
    }

    /**
     * 从协议帧创建领域事件（可注入 Clock 用于测试）。
     */
    static ElevatorEvent from(MNKFrame frame, Clock clock) {
        return new ElevatorEvent(
                UUID.randomUUID().toString(),
                LocalDateTime.now(clock),
                frame.getProtocolVersion(),
                frame.getTimestamp(),
                frame.getDeviceId(),
                frame.getElevatorStatus(),
                frame.getDoorStatus(),
                frame.getCurrentFloor(),
                frame.getTargetFloor(),
                frame.getDirection(),
                frame.getAlarm(),
                frame.getPassenger(),
                frame.getSpeed(),
                frame.getRuntime(),
                frame.getDistance(),
                frame.getTimes(),
                frame.getRawData()
        );
    }

    // ==================== 私有构造 ====================

    private ElevatorEvent(String eventId, LocalDateTime occurredAt,
                          String protocolVersion, LocalDateTime deviceTime,
                          String deviceId, String elevatorStatus, String doorStatus,
                          String currentFloor, String targetFloor, String direction,
                          String alarm, String passenger, double speed, String runtime,
                          int distance, int times, String rawData) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.protocolVersion = protocolVersion;
        this.deviceTime = deviceTime;
        this.deviceId = deviceId;
        this.elevatorStatus = elevatorStatus;
        this.doorStatus = doorStatus;
        this.currentFloor = currentFloor;
        this.targetFloor = targetFloor;
        this.direction = direction;
        this.alarm = alarm;
        this.passenger = passenger;
        this.speed = speed;
        this.runtime = runtime;
        this.distance = distance;
        this.times = times;
        this.rawData = rawData;
    }

    // ==================== Getters ====================

    public String getEventId() { return eventId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getProtocolVersion() { return protocolVersion; }
    public LocalDateTime getDeviceTime() { return deviceTime; }
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
        return "ElevatorEvent{id=" + eventId + ", device=" + deviceId
                + ", floor=" + currentFloor + ", dir=" + direction + "}";
    }
}
