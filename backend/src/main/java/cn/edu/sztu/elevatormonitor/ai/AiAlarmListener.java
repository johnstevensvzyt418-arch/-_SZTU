package cn.edu.sztu.elevatormonitor.ai;

import cn.edu.sztu.elevatormonitor.domain.event.ElevatorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * AI 异常检测监听器 — 监听 {@link ElevatorEvent}，攒批时序数据并调用 AI 推理。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>从 ElevatorEvent 提取 5 维特征向量</li>
 *   <li>追加到 {@link TimeSeriesBuffer}（Redis 滑动窗口）</li>
 *   <li>当窗口攒满后，调用 {@link AiPredictClient#predict}</li>
 *   <li>若 AI 判定异常，将告警写入 Redis，前端自动联动</li>
 * </ol>
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>AI 服务不可用时 → 静默跳过，不影响主流程</li>
 *   <li>Redis 不可用时 → 跳过攒批，记录 WARN 日志</li>
 *   <li>特征值解析异常 → 使用默认值 0</li>
 * </ul>
 *
 * <h3>与 AlarmHandler 的关系</h3>
 * <p>本 Listener 与 {@code AlarmHandler} 并行运行，互补而非替代：
 * AlarmHandler 负责规则引擎告警（平层超时等），
 * AiAlarmListener 负责 AI 模型告警（异常模式检测）。</p>
 *
 * @author ai-integration
 * @since 0.3.0
 */
@Component
public class AiAlarmListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiAlarmListener.class);

    /** Redis Hash — AI 告警存储 key */
    private static final String HASH_AI_ALARM = "elevator:ai_alarm";

    /** Redis Pub/Sub — AI 告警推送频道 */
    private static final String CHANNEL_AI_ALARM = "elevator:alarm";

    /** 推理触发的最小窗口大小 */
    private static final int WINDOW_MIN_SIZE = 10;

    private final TimeSeriesBuffer timeSeriesBuffer;
    private final AiPredictClient aiPredictClient;
    private final StringRedisTemplate redis;

    public AiAlarmListener(TimeSeriesBuffer timeSeriesBuffer,
                           AiPredictClient aiPredictClient,
                           StringRedisTemplate redis) {
        this.timeSeriesBuffer = timeSeriesBuffer;
        this.aiPredictClient = aiPredictClient;
        this.redis = redis;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("[AI-Listener] Bean 初始化完成, TimeSeriesBuffer={}, AiPredictClient={}, Redis={}",
                timeSeriesBuffer != null ? "已注入" : "NULL",
                aiPredictClient != null ? "已注入" : "NULL",
                redis != null ? "已注入" : "NULL");
    }

    /**
     * 监听电梯事件，异步执行 AI 异常检测。
     *
     * <p>使用 {@code @Async("alarmExecutor")} 与告警评估共享线程池，
     * 确保 AI 推理不阻塞事件主流程。</p>
     */
    @EventListener
    @Async("alarmExecutor")
    public void onElevatorEvent(ElevatorEvent event) {
        LOGGER.debug("[AI-Listener] 收到事件: eventId={}, deviceId={}",
                event.getEventId(), event.getDeviceId());

        try {
            // ---- 1. 提取 5 维特征 ----
            double[] features = extractFeatures(event);

            // ---- 2. 追加到时序缓冲区 ----
            timeSeriesBuffer.append(event.getDeviceId(), features, WINDOW_MIN_SIZE * 2);

            long currentSize = timeSeriesBuffer.size(event.getDeviceId());
            LOGGER.debug("[AI-Listener] 缓冲区 deviceId={} size={}/{}",
                    event.getDeviceId(), currentSize, WINDOW_MIN_SIZE);

            // ---- 3. 窗口攒满后触发推理 ----
            if (currentSize >= WINDOW_MIN_SIZE) {
                performInference(event.getDeviceId());
            }

        } catch (Exception e) {
            LOGGER.error("[AI-Listener] 处理异常: eventId={}, deviceId={}",
                    event.getEventId(), event.getDeviceId(), e);
        }
    }

    // ============================================================
    // 特征提取
    // ============================================================

    /**
     * 从 ElevatorEvent 提取 5 维特征向量。
     *
     * <p>当前特征映射（与模型训练数据的 xinshida 5 列对应）：</p>
     * <ol>
     *   <li>feature1: 门状态 → 0=关门到位, 1=开门到位, 2=关门中, 3=开门中</li>
     *   <li>feature2: 当前楼层 → 整数</li>
     *   <li>feature3: 目标楼层 → 整数</li>
     *   <li>feature4: 运行方向 → 0=平层, 1=上行, 2=下行</li>
     *   <li>feature5: 速度 (m/s)</li>
     * </ol>
     */
    private double[] extractFeatures(ElevatorEvent event) {
        double[] f = new double[5];
        f[0] = TimeSeriesBuffer.parseDoorStatus(event.getDoorStatus());
        f[1] = TimeSeriesBuffer.parseFloor(event.getCurrentFloor());
        f[2] = TimeSeriesBuffer.parseFloor(event.getTargetFloor());
        f[3] = TimeSeriesBuffer.parseDirection(event.getDirection());
        f[4] = event.getSpeed();  // speed 已是 double
        return f;
    }

    // ============================================================
    // 推理与告警
    // ============================================================

    /**
     * 执行 AI 推理，若检测异常则写告警。
     */
    private void performInference(String deviceId) {
        // 读取完整窗口序列
        List<List<Double>> sequence = timeSeriesBuffer.readWindow(deviceId, WINDOW_MIN_SIZE);
        if (sequence.isEmpty()) {
            LOGGER.debug("[AI-Listener] 窗口数据不足, 跳过推理 deviceId={}", deviceId);
            return;
        }

        // 调用 AI 服务
        AiPredictClient.PredictResult result = aiPredictClient.predict(deviceId, sequence);
        if (result == null) {
            LOGGER.warn("[AI-Listener] AI 推理返回 null, deviceId={} (服务可能不可用)", deviceId);
            return;
        }

        // 若异常，写入告警
        if (result.isAbnormal()) {
            writeAiAlarm(deviceId, result);
        } else {
            // 正常时清除之前的 AI 告警
            clearAiAlarm(deviceId);
        }
    }

    /**
     * 将 AI 告警写入 Redis，前端自动展示。
     */
    private void writeAiAlarm(String deviceId, AiPredictClient.PredictResult result) {
        try {
            String alarmJson = String.format(
                    "{\"type\":\"AI\",\"deviceId\":\"%s\",\"score\":%.2f,\"threshold\":%.1f,\"label\":\"%s\"}",
                    deviceId, result.getScore(), result.getThreshold(), result.getLabel());

            // HSET: 持久化 AI 告警状态
            redis.opsForHash().put(HASH_AI_ALARM, deviceId, alarmJson);

            // PUBLISH: 实时推送前端
            redis.convertAndSend(CHANNEL_AI_ALARM, alarmJson);

            LOGGER.info("[AI-Listener] ⚠️ AI 异常告警! deviceId={} score={:.2f} threshold={:.1f}",
                    deviceId, result.getScore(), result.getThreshold());
        } catch (Exception e) {
            LOGGER.error("[AI-Listener] 告警写入失败 deviceId={}: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 清除设备的 AI 告警（恢复正常）。
     */
    private void clearAiAlarm(String deviceId) {
        try {
            String existing = (String) redis.opsForHash().get(HASH_AI_ALARM, deviceId);
            if (existing != null) {
                redis.opsForHash().delete(HASH_AI_ALARM, deviceId);
                LOGGER.info("[AI-Listener] ✅ AI 告警已清除 deviceId={}", deviceId);
            }
        } catch (Exception e) {
            LOGGER.error("[AI-Listener] 清除告警失败 deviceId={}: {}", deviceId, e.getMessage());
        }
    }
}
