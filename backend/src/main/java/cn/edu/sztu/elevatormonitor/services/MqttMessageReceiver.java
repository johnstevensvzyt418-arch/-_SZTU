package cn.edu.sztu.elevatormonitor.services;

import cn.edu.sztu.elevatormonitor.application.MNKApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT 消息接收服务 — 将嵌入式设备通过 MQTT 上报的 MNK 协议数据
 * 路由到 {@link MNKApplicationService} 统一处理。
 *
 * <h3>MQTT Topic 约定</h3>
 * <pre>
 *   elevator/mnk/data              → payload = MNK 94字节HEX字符串, deviceId 从报文中提取
 *   elevator/{deviceId}/data       → payload 同上, deviceId 从 topic 提取
 * </pre>
 *
 * <h3>处理链路</h3>
 * <pre>
 *   MQTT Message → 提取 payload + deviceId + time
 *                → MNKApplicationService.handleDataReport()
 *                → (与 HTTP POST 完全相同的事件驱动管道)
 * </pre>
 *
 * @author mqtt-integration
 * @since 0.3.0
 */
@Service
public class MqttMessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttMessageReceiver.class);

    /** 从 topic 中提取 deviceId 的正则: elevator/{deviceId}/data */
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("elevator/(\\w+)/data");

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MNKApplicationService mnkApplicationService;

    public MqttMessageReceiver(MNKApplicationService mnkApplicationService) {
        this.mnkApplicationService = mnkApplicationService;
        LOGGER.info("[MQTT-Receiver] 初始化完成, 已注入 MNKApplicationService");
    }

    /**
     * 接收 MQTT 消息，解析后路由到 MNK 处理管道。
     *
     * <p>@ServiceActivator 绑定到 mqttInputChannel，由 Spring Integration 自动调用。</p>
     *
     * @param message MQTT 消息 (payload = MNK HEX 字符串)
     */
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMqttMessage(Message<?> message) {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            byte[] payloadBytes = (byte[]) message.getPayload();
            if (payloadBytes == null || payloadBytes.length == 0) {
                LOGGER.warn("[MQTT-Receiver] 收到空消息, topic={}", topic);
                return;
            }

            String payload = new String(payloadBytes, StandardCharsets.UTF_8).trim();
            LOGGER.info("[MQTT-Receiver] 收到MQTT消息: topic={}, len={}", topic, payload.length());

            // ---- 1. 提取 deviceId ----
            String deviceId = extractDeviceId(topic, payload);
            if (deviceId == null || deviceId.isEmpty()) {
                LOGGER.warn("[MQTT-Receiver] 无法提取deviceId, topic={}", topic);
                return;
            }

            // ---- 2. 优先从 payload 提取设备时间 (MNK协议 data[1..20] = yyyy/MM/dd HH:mm:ss) ----
            // 使用设备真实采集时间而非服务器时间，保证速度计算等时间敏感逻辑的准确性
            String reportTime = extractDeviceTime(payload);

            // ---- 3. 路由到统一处理管道 ----
            int result = mnkApplicationService.handleDataReport(payload, reportTime, deviceId);

            LOGGER.info("[MQTT-Receiver] 处理完成: topic={}, deviceId={}, result={}", topic, deviceId, result);

        } catch (Exception e) {
            LOGGER.error("[MQTT-Receiver] 消息处理异常", e);
        }
    }

    /**
     * 从 MNK 协议 payload 中提取设备采集时间。
     * MNK 协议格式: Fyyyy/MM/dd HH:mm:ss/...  时间位于 data[1..20]
     * 若提取失败，退化为服务器当前时间。
     */
    private String extractDeviceTime(String payload) {
        try {
            if (payload != null && payload.length() >= 20 && payload.charAt(0) == 'F') {
                // 提取 yyyy/MM/dd HH:mm:ss → 转为 HH:mm:ss
                String dateTime = payload.substring(1, 20); // "2020/11/10 11:24:46"
                if (dateTime.length() >= 17) {
                    return dateTime.substring(11, 19); // "11:24:46"
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[MQTT-Receiver] 设备时间提取失败，使用服务器时间", e);
        }
        return LocalDateTime.now().format(DT_FMT);
    }

    /**
     * 从 MQTT Topic 或报文中提取设备ID。
     */
    private String extractDeviceId(String topic, String payload) {
        // 方式A: 从 topic 中提取 elevator/{deviceId}/data
        if (topic != null) {
            Matcher m = DEVICE_ID_PATTERN.matcher(topic);
            if (m.find()) {
                return m.group(1);
            }
        }

        // 方式B: 从 MNK 协议报文中提取 (偏移21-28为设备ID)
        if (payload != null && payload.length() >= 29) {
            try {
                return payload.substring(21, 29).trim();
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
