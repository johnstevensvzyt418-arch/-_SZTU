package cn.edu.sztu.elevatormonitor.alarm;

import cn.edu.sztu.elevatormonitor.entity.AlarmEvent;
import cn.edu.sztu.elevatormonitor.entity.ElevatorMessage;

/**
 * 告警规则 — 策略接口。
 * 每条规则实现 evaluate()，返回 null 表示无告警，否则返回 AlarmEvent。
 *
 * 规则分为两类:
 *   - 无状态规则: 仅依赖当前 ElevatorMessage 即可判断 (如速度阈值)
 *   - 有状态规则: 需要 DeviceState 追踪历史 (如长时间未移动)
 */
public interface AlarmRule {

    /** 规则唯一标识，如 "LONG_IDLE" */
    String ruleName();

    /** 告警级别 */
    AlarmLevel level();

    /** 规则描述 */
    String description();

    /**
     * 评估当前消息是否触发告警。
     * @param msg   当前电梯消息
     * @param state 该设备的历史状态（有状态规则使用；无状态规则可忽略）
     * @return AlarmEvent 若触发告警；null 若正常
     */
    AlarmEvent evaluate(ElevatorMessage msg, DeviceState state);
}
