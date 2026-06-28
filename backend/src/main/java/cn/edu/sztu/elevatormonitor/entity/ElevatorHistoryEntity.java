package cn.edu.sztu.elevatormonitor.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 电梯历史运行数据 — JPA 实体，映射 elevator_history 表。
 *
 * 表设计要点:
 * - device_id + created_at 联合索引，支撑按设备+时间范围查询
 * - 所有业务字段冗余存储，避免回表
 */
@Entity
@Table(name = "elevator_history", indexes = {
        @Index(name = "idx_device_time", columnList = "device_id, created_at"),
        @Index(name = "idx_created_at",  columnList = "created_at")
})
public class ElevatorHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 电梯设备ID，如 00000001 */
    @Column(name = "device_id", nullable = false, length = 32)
    private String deviceId;

    /** 记录写入时间（非设备上报时间，保证时序不乱） */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 设备上报时间字符串 */
    @Column(name = "report_time", length = 32)
    private String reportTime;

    /** 当前楼层 */
    @Column(name = "current_floor", length = 8)
    private String currentFloor;

    /** 目标楼层 */
    @Column(name = "target_floor", length = 8)
    private String targetFloor;

    /** 运行方向: 00=平层 01=上行 02=下行 */
    @Column(name = "direction", length = 4)
    private String direction;

    /** 当前速度 (m/s) */
    @Column(name = "speed")
    private Double speed;

    /** 门状态: 00=关门到位 01=开门到位 02=关门中 03=开门中 */
    @Column(name = "door_status", length = 4)
    private String doorStatus;

    /** 电梯状态: 00=正常 */
    @Column(name = "elevator_status", length = 4)
    private String elevatorStatus;

    /** 报警状态 */
    @Column(name = "alarm", length = 32)
    private String alarm;

    /** 累计运行次数 */
    @Column(name = "run_times")
    private Integer runTimes;

    /** 累计运行里程 (层站数) */
    @Column(name = "distance")
    private Integer distance;

    /** 运行时间 (格式化字符串) */
    @Column(name = "runtime", length = 32)
    private String runtime;

    /** 乘客状态: 00=无人 01=有人 */
    @Column(name = "passenger", length = 4)
    private String passenger;

    // ==================== 构造方法 ====================

    public ElevatorHistoryEntity() {
    }

    /**
     * 从 ElevatorMessage 快照构造历史记录。
     * 注意: ElevatorMessage 是可变对象，此处深拷贝关键字段。
     */
    public static ElevatorHistoryEntity from(ElevatorMessage msg, String reportTime) {
        ElevatorHistoryEntity e = new ElevatorHistoryEntity();
        e.deviceId = msg.getDeviceId();
        e.createdAt = LocalDateTime.now();
        e.reportTime = reportTime;
        e.currentFloor = msg.getCurrentFloor();
        e.targetFloor = msg.getTargetFloor();
        e.direction = msg.getDirection();
        e.speed = msg.getSpeed();
        e.doorStatus = msg.getDoorStatus();
        e.elevatorStatus = msg.getElevatorStatus();
        e.alarm = msg.getAlarm();
        e.runTimes = msg.getTimes();
        e.distance = msg.getDistance();
        e.runtime = msg.getRuntime();
        e.passenger = msg.getPassenger();
        return e;
    }

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }

    public String getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(String currentFloor) { this.currentFloor = currentFloor; }

    public String getTargetFloor() { return targetFloor; }
    public void setTargetFloor(String targetFloor) { this.targetFloor = targetFloor; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public String getDoorStatus() { return doorStatus; }
    public void setDoorStatus(String doorStatus) { this.doorStatus = doorStatus; }

    public String getElevatorStatus() { return elevatorStatus; }
    public void setElevatorStatus(String elevatorStatus) { this.elevatorStatus = elevatorStatus; }

    public String getAlarm() { return alarm; }
    public void setAlarm(String alarm) { this.alarm = alarm; }

    public Integer getRunTimes() { return runTimes; }
    public void setRunTimes(Integer runTimes) { this.runTimes = runTimes; }

    public Integer getDistance() { return distance; }
    public void setDistance(Integer distance) { this.distance = distance; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public String getPassenger() { return passenger; }
    public void setPassenger(String passenger) { this.passenger = passenger; }

    @Override
    public String toString() {
        return "ElevatorHistory{" +
                "deviceId='" + deviceId + '\'' +
                ", floor=" + currentFloor +
                ", direction=" + direction +
                ", speed=" + speed +
                ", door=" + doorStatus +
                ", alarm=" + alarm +
                ", times=" + runTimes +
                ", dist=" + distance +
                '}';
    }
}
