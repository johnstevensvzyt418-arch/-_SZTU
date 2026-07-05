package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 规则: 冲顶。
 * 电梯上行超过最高楼层阈值时触发告警，表示可能发生冲顶事故。
 */
@Component
public class OverTopRule implements AlarmRule {

    @Value("${alarm.over-top.max-floor:30}")
    private int maxFloor;

    @Override
    public String ruleName() { return "OVER_TOP"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.CRITICAL; }

    @Override
    public String description() { return "电梯冲顶（超过" + maxFloor + "楼）"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        String curFloor = msg.getCurrentFloor();
        if (curFloor == null) {
            return null;
        }

        try {
            int floor = Integer.parseInt(curFloor);
            // 冲顶: 楼层超过最高阈值 且 方向为上行
            if (floor > maxFloor && "01".equals(msg.getDirection())) {
                return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                        "当前楼层=" + floor + "楼, 超过最高" + maxFloor + "楼阈值, 方向=上行, 速度=" + msg.getSpeed() + "m/s",
                        curFloor, msg.getSpeed());
            }
        } catch (NumberFormatException e) {
            // 非数字楼层忽略
        }
        return null;
    }
}
