package cn.edu.sztu.elevatormonitor.alarm.rule;

import cn.edu.sztu.elevatormonitor.alarm.AlarmLevel;
import cn.edu.sztu.elevatormonitor.alarm.AlarmRule;
import cn.edu.sztu.elevatormonitor.alarm.DeviceState;
import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;
import org.springframework.stereotype.Component;

/**
 * 硬件故障告警规则 — 电梯控制器上报 0x80 硬件故障。
 *
 * <h3>触发条件</h3>
 * <p>方向码为 {@code "03"}，对应 MNK 协议 s3[0:2]="38"（控制器 CAN 008 帧 data[3]==0x80）。</p>
 *
 * <h3>告警级别</h3>
 * <p>CRITICAL — 硬件故障属于最高级别告警。</p>
 *
 * <h3>前端显示</h3>
 * <p>告警灯 07（检修状态），通过 Alarm 字段中 {@code "HARDWARE_FAULT"} 匹配。</p>
 *
 * @author hardware-fault-integration
 * @since 0.4.0
 */
@Component
public class HardwareFaultRule implements AlarmRule {

    @Override
    public String ruleName() {
        return "HARDWARE_FAULT";
    }

    @Override
    public AlarmLevel level() {
        return AlarmLevel.CRITICAL;
    }

    @Override
    public String description() {
        return "电梯硬件故障(0x80)";
    }

    @Override
    public AlarmEvent evaluate(ElevatorMessage msg, DeviceState state) {
        if ("03".equals(msg.getDirection())) {
            return AlarmEvent.fire(
                    msg.getDeviceId(),
                    ruleName(),
                    level(),
                    description(),
                    "电梯控制器上报硬件故障，位于" + msg.getCurrentFloor() + "楼",
                    msg.getCurrentFloor(),
                    msg.getSpeed()
            );
        }
        return null;
    }
}
