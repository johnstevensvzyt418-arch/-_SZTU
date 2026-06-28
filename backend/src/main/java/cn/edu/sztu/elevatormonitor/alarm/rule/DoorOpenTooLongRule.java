package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 规则2: 门长时间未关闭。
 * 若门未处于"关门到位"(00)状态超过阈值秒数，触发告警。
 */
@Component
public class DoorOpenTooLongRule implements AlarmRule {

    @Value("${alarm.door-open.threshold-seconds:20}")
    private int thresholdSeconds;

    @Override
    public String ruleName() { return "DOOR_OPEN_TOO_LONG"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.WARN; }

    @Override
    public String description() { return "电梯门超过" + thresholdSeconds + "秒未关闭"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        String door = msg.getDoorStatus();
        if ("00".equals(door)) {
            // 关门到位 → 更新时间戳
            state.markDoorClosed();
            return null;
        }

        Instant lastClosed = state.getLastDoorClosedTime();
        if (lastClosed == null) {
            state.markDoorClosed();
            return null;
        }

        long openSec = Duration.between(lastClosed, Instant.now()).getSeconds();
        if (openSec >= thresholdSeconds) {
            String doorDesc;
            switch (door == null ? "" : door) {
                case "01": doorDesc = "开门到位"; break;
                case "02": doorDesc = "关门中"; break;
                case "03": doorDesc = "开门中"; break;
                default:   doorDesc = "未知(" + door + ")"; break;
            }
            return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                    "门状态=" + doorDesc + ", 持续" + openSec + "秒, 位于" + msg.getCurrentFloor() + "楼",
                    msg.getCurrentFloor(), msg.getSpeed());
        }
        return null;
    }
}
