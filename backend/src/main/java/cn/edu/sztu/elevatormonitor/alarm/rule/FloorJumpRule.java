package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 规则4: 楼层跳变异常。
 * 若当前楼层与上一楼层差值超过阈值，判定为跳变（传感器故障或滑梯）。
 */
@Component
@Order(1)
public class FloorJumpRule implements AlarmRule {

    @Value("${alarm.floor-jump.max-diff:2}")
    private int maxFloorDiff;

    @Override
    public String ruleName() { return "FLOOR_JUMP"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.CRITICAL; }

    @Override
    public String description() { return "楼层跳变超过" + maxFloorDiff + "层"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        String curFloor = msg.getCurrentFloor();
        String prevFloor = state.getPreviousFloorForJump();

        if (curFloor == null || prevFloor == null || curFloor.equals(prevFloor)) {
            state.setPreviousFloorForJump(curFloor);
            return null;
        }

        try {
            int cur = Integer.parseInt(curFloor);
            int prev = Integer.parseInt(prevFloor);
            int diff = Math.abs(cur - prev);

            state.setPreviousFloorForJump(curFloor);

            if (diff > maxFloorDiff) {
                return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                        "楼层从" + prev + "跳变到" + cur + "(差值=" + diff + "层), 方向=" + msg.getDirection(),
                        curFloor, msg.getSpeed());
            }
        } catch (NumberFormatException e) {
            // 楼层为非数字（如 B1, B2），跳过数值比较
            state.setPreviousFloorForJump(curFloor);
        }
        return null;
    }
}
