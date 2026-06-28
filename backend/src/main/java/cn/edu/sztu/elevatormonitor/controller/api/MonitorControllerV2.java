package cn.edu.sztu.elevatormonitor.controller.api;

import cn.edu.sztu.elevatormonitor.application.MNKApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电梯数据上报入口 — V2 重构版本。
 *
 * <h3>架构变更（对比 V1）</h3>
 * <table>
 *   <tr><th>V1 (旧)</th><th>V2 (新)</th></tr>
 *   <tr><td>Controller 直接调用 XXXService</td><td>Controller → ApplicationService</td></tr>
 *   <tr><td>Service 内解析 data 字符串</td><td>Parser 独立解析</td></tr>
 *   <tr><td>Service 直接调用 Alarm/Redis/MySQL</td><td>事件驱动，Listener 独立处理</td></tr>
 *   <tr><td>返回 int 错误码</td><td>返回统一响应（可扩展为 ApiResponse）</td></tr>
 * </table>
 *
 * <h3>Controller 职责边界</h3>
 * <ul>
 *   <li>✅ 参数接收与校验</li>
 *   <li>✅ 路由到正确的 ApplicationService</li>
 *   <li>✅ 统一异常处理</li>
 *   <li>❌ 不允许解析 data 字符串</li>
 *   <li>❌ 不允许包含业务逻辑</li>
 *   <li>❌ 不允许直接调用 Service/Repository</li>
 * </ul>
 *
 * <h3>扩展方式</h3>
 * <p>新增设备协议只需：</p>
 * <ol>
 *   <li>实现 {@code ProtocolParser} 接口（如 XinShiDaParser）</li>
 *   <li>创建对应的 ApplicationService（如 XinShiDaApplicationService）</li>
 *   <li>在此 Controller 新增一个 endpoint</li>
 * </ol>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
@RestController
@RequestMapping("api/v2")
public class MonitorControllerV2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(MonitorControllerV2.class);

    // ==================== Application Services（每个品牌一个入口） ====================

    private final MNKApplicationService mnkApplicationService;

    /** Redis — 仅用于只读查询最新状态（不破坏事件驱动架构） */
    private final StringRedisTemplate stringRedisTemplate;

    // 未来扩展:
    // private final XinShiDaApplicationService xinShiDaApplicationService;
    // private final MoNaKeApplicationService moNaKeApplicationService;

    public MonitorControllerV2(MNKApplicationService mnkApplicationService,
                               StringRedisTemplate stringRedisTemplate) {
        this.mnkApplicationService = mnkApplicationService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 状态查询端点 ====================

    /**
     * 查询指定设备的最新状态（从 Redis Hash 读取）。
     *
     * <p>Redis Key 结构: HSET elevator:status {deviceId} → JSON</p>
     *
     * @param deviceId 设备编号（如 00000004）
     * @return JSON 格式的电梯状态，不存在则返回 {@code {"error":"NOT_FOUND"}}
     */
    @GetMapping(value = "/status/{deviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getStatus(@PathVariable String deviceId) {
        Object raw = stringRedisTemplate.opsForHash().get("elevator:status", deviceId);
        if (raw == null) {
            LOGGER.debug("[V2-Status] 设备 {} 无缓存数据", deviceId);
            return "{\"error\":\"NOT_FOUND\",\"deviceId\":\"" + deviceId + "\"}";
        }
        LOGGER.debug("[V2-Status] 返回设备 {} 状态", deviceId);
        return raw.toString();
    }

    // ==================== 协议上报端点 ====================

    /**
     * MNK 电梯数据上报（V2 事件驱动架构）。
     *
     * <pre>
     * 处理链路:
     *   Controller → MNKApplicationService.handleDataReport()
     *     → MNKParser.parse() → MNKFrame
     *     → ElevatorEvent.from(frame)
     *     → EventPublisher.publish(event)
     *       ├── AlarmHandler.onElevatorEvent()   [@Async]
     *       ├── HistoryHandler.onElevatorEvent() [@Async]
     *       └── RedisHandler.onElevatorEvent()   [@Async]
     * </pre>
     *
     * @param data       MNK 协议 HEX 字符串
     * @param time       设备上报时间 (HH:mm:ss)
     * @param elevatorID 设备编号
     * @return 0=成功, -1=报文为空, -2=协议解析异常
     */
    @PostMapping("mnk")
    public int uploadMnk(@RequestParam String data,
                         @RequestParam String time,
                         @RequestParam String elevatorID) {
        LOGGER.debug("[V2-MNK] 收到数据, elevatorID={}", elevatorID);
        return mnkApplicationService.handleDataReport(data, time, elevatorID);
    }

    // ==================== 未来扩展示例 ====================

    // /**
    //  * 新时达电梯数据上报。
    //  */
    // @PostMapping("xinshida")
    // public int uploadXinshida(@RequestParam String data,
    //                           @RequestParam String time,
    //                           @RequestParam String elevatorID) {
    //     LOGGER.debug("[V2-XinShiDa] 收到数据, elevatorID={}", elevatorID);
    //     return xinShiDaApplicationService.handleDataReport(data, time, elevatorID);
    // }
}
