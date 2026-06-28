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
 * 规则1: 电梯长时间未移动。
 * 若电梯处于非平层状态 且 超过阈值秒数未发生楼层变化，触发告警。
 */
@Component
public class LongIdleRule implements AlarmRule {

    @Value("${alarm.long-idle.threshold-seconds:60}")
    private int thresholdSeconds;

    @Override
    public String ruleName() { return "LONG_IDLE"; }

    @Override
    public AlarmLevel level() { return AlarmLevel.WARN; }

    @Override
    public String description() { return "电梯超过" + thresholdSeconds + "秒未移动"; }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        String dir = msg.getDirection();
        if (dir == null || "00".equals(dir)) return null; // 平层状态不告警

        String curFloor = msg.getCurrentFloor();
        String lastFloor = state.getLastFloorForIdleCheck();

        // 楼层变化 → 电梯确实在移动 → 更新基准时间
        if (curFloor != null && !curFloor.equals(lastFloor)) {
            state.markMoved();
            state.setLastFloorForIdleCheck(curFloor);
            return null;
        }
        // 首次检测 → 初始化基准
        if (lastFloor == null) {
            state.markMoved();
            state.setLastFloorForIdleCheck(curFloor);
            return null;
        }

        // 楼层未变 → 检查停滞时长
        Instant lastMove = state.getLastMoveTime();
        if (lastMove == null) {
            state.markMoved();
            return null;
        }

        long idleSec = Duration.between(lastMove, Instant.now()).getSeconds();
        if (idleSec >= thresholdSeconds) {
            return AlarmEvent.fire(msg.getDeviceId(), ruleName(), level(), description(),
                    "电梯在" + msg.getCurrentFloor() + "楼停滞" + idleSec + "秒, 方向=" + dir,
                    msg.getCurrentFloor(), msg.getSpeed());
        }
        return null;
    }
}
