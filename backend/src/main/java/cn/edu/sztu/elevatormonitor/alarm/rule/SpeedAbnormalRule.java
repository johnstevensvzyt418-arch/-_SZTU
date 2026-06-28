package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 规则3: 速度异常。
 * 电梯运行速度超过额定最大速度阈值时触发告警。
 */
@Component
public class SpeedAbnormalRule implements AlarmRule {

    @Value("${alarm.speed.max-mps:3.0}")
    private double maxSpeedMps;

    @Override
    public String ruleName() { return "SPEED_ABNORMAL"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.CRITICAL; }

    @Override
    public String description() { return "电梯运行速度超过" + maxSpeedMps + "m/s"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        double speed = msg.getSpeed();
        if (speed > maxSpeedMps) {
            return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                    "当前速度=" + speed + "m/s, 超过阈值" + maxSpeedMps + "m/s, 位于" + msg.getCurrentFloor() + "楼",
                    msg.getCurrentFloor(), speed);
        }
        return null;
    }
}
