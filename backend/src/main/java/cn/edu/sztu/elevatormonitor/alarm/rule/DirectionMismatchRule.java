package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 规则6: 运行方向与楼层变化不一致。
 * 上行(01)时楼层应变大，下行(02)时楼层应变小；若相反则告警。
 */
@Component
@Order(6)
public class DirectionMismatchRule implements AlarmRule {

    @Override
    public String ruleName() { return "DIRECTION_MISMATCH"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.WARN; }

    @Override
    public String description() { return "运行方向与楼层变化不一致"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        String dir = msg.getDirection();
        String curFloor = msg.getCurrentFloor();
        String prevFloor = state.getPreviousFloor();
        String prevDir = state.getPreviousDirection();

        if (dir == null || curFloor == null || prevFloor == null) {
            state.updateFloorAndDirection(curFloor, dir);
            return null;
        }

        // 方向或楼层未变化 → 不判断
        if (curFloor.equals(prevFloor)) return null;

        try {
            int cur = Integer.parseInt(curFloor);
            int prev = Integer.parseInt(prevFloor);

            state.setPreviousFloor(curFloor);
            state.setPreviousDirection(dir);

            if ("01".equals(dir) && cur < prev) {
                return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                        "方向=上行(01) 但楼层从" + prev + "降到" + cur + ", 上次方向=" + prevDir,
                        curFloor, msg.getSpeed());
            }
            if ("02".equals(dir) && cur > prev) {
                return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                        "方向=下行(02) 但楼层从" + prev + "升到" + cur + ", 上次方向=" + prevDir,
                        curFloor, msg.getSpeed());
            }
        } catch (NumberFormatException e) {
            // 非数字楼层跳过
            state.setPreviousFloor(curFloor);
            state.setPreviousDirection(dir);
        }
        return null;
    }
}
