package cn.edu.sztu.elevatormonitor.protocol;

import cn.edu.sztu.elevatormonitor.enums.DestFloorConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * MNK 协议解析器 — 负责将 MNK 原始 HEX 字符串解析为 {@link MNKFrame}。
 *
 * <h3>协议格式（MNK 34.007）</h3>
 * <pre>
 * F + 19字节日期时间 + /8字节ID + 4×16字节HEX段 = 最少94字符
 *
 * 示例:
 * F2020/11/10 11:24:46/00000004/000000000000517c 00000000000421b9 30050000000053c3 d00063000000220e
 *                                  内招/目标楼层   开关门状态        运行(方向+楼层)    保留
 * </pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>时间字段统一解析为 {@link LocalDateTime}，使用 {@link DateTimeFormatter} 消除 ParseException</li>
 *   <li>所有 HEX→业务映射逻辑集中于此，Controller/Service 不感知协议细节</li>
 *   <li>错误统一包装为 {@link ProtocolParseException}，调用方只处理一种异常</li>
 * </ul>
 *
 * @author architecture-v2
 * @since 0.2.0
 */
@Component
public class MNKParser implements ProtocolParser<MNKFrame> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MNKParser.class);

    // ==================== 协议常量 ====================

    /** 报文最小长度 */
    private static final int MNK_MIN_LEN = 94;

    /** 日期时间格式: yyyy/MM/dd HH:mm:ss */
    private static final DateTimeFormatter DT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** 仅时间格式: HH:mm:ss */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 当前协议版本 */
    private static final String PROTOCOL_VERSION = "MNK-34.007";

    // ==================== HEX 信号标识符 ====================

    /** 内招信号标识 */
    private static final String SIG_INNER_CALL = "51";
    /** 开关门信号标识 */
    private static final String SIG_DOOR = "21";
    /** 运行信号标识 */
    private static final String SIG_RUN = "53";
    /** 保留信号标识 */
    private static final String SIG_RESERVED = "22";

    // ==================== 核心解析方法 ====================

    /**
     * 解析 MNK 原始数据为统一协议帧。
     *
     * @param rawData    原始 HEX 字符串
     * @param reportTime 设备上报时间 (HH:mm:ss)
     * @return 解析后的 MNKFrame
     * @throws ProtocolParseException 解析失败
     */
    @Override
    public MNKFrame parse(String rawData, String reportTime) throws ProtocolParseException {
        // ---- 1. 空值/长度预校验 ----
        if (rawData == null || rawData.isEmpty()) {
            throw new ProtocolParseException("MNK-001", "报文为空");
        }
        if (rawData.length() < MNK_MIN_LEN) {
            throw new ProtocolParseException("MNK-002",
                    "报文长度不足(期望>=" + MNK_MIN_LEN + " 实际=" + rawData.length() + ")");
        }

        // ---- 2. 转换为小写统一处理 ----
        String data = rawData.toLowerCase();

        // ---- 3. 解析四个16字节HEX段 ----
        String seg1, seg2, seg3, seg4;
        try {
            seg1 = data.substring(30, 46);
            seg2 = data.substring(46, 62);
            seg3 = data.substring(62, 78);
            seg4 = data.substring(78, 94);

            // 按标识符重新定位各段（协议允许4个段乱序）
            for (int i = 30; i < 94; i += 16) {
                String marker = data.substring(i + 12, i + 14);
                switch (marker) {
                    case SIG_INNER_CALL: seg1 = data.substring(i, i + 16); break;
                    case SIG_DOOR:       seg2 = data.substring(i, i + 16); break;
                    case SIG_RUN:        seg3 = data.substring(i, i + 16); break;
                    case SIG_RESERVED:   seg4 = data.substring(i, i + 16); break;
                    default:
                        throw new ProtocolParseException("MNK-003",
                                "非法信号标识符: " + marker + " (offset=" + i + ")");
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ProtocolParseException("MNK-004", "报文索引越界", e);
        }

        // ---- 4. 解析时间戳 (统一为 LocalDateTime) ----
        LocalDateTime timestamp = parseTimestamp(data);

        // ---- 5. 解析设备ID ----
        String deviceId = data.substring(21, 29);

        // ---- 6. 解析门状态 ----
        String doorStatus = parseDoorStatus(seg2);

        // ---- 7. 解析目标楼层 ----
        String targetFloor = parseTargetFloor(seg1);

        // ---- 8. 解析运行方向 + 当前楼层 ----
        String direction = parseDirection(seg3);
        String currentFloor = parseCurrentFloor(seg3);

        // ---- 9. 解析报警状态 —— 协议不携带报警位，由上游 AlarmRuleEngine 判定 ----
        String alarm = "";

        // ---- 10. 解析乘客状态 ----
        String passenger = inferPassenger(targetFloor, doorStatus);

        // ---- 11. 速度/里程/次数 —— 有状态字段，由上游服务跨请求累积计算 ----
        // 协议单帧不包含这些累计值，设为 -1 表示"待上游填充"
        double speed = -1.0;
        int distance = -1;
        int times = -1;

        // ---- 12. 构建 MNKFrame ----
        MNKFrame frame = MNKFrame.builder()
                .protocolVersion(PROTOCOL_VERSION)
                .timestamp(timestamp)
                .deviceId(deviceId)
                .elevatorStatus("00")   // MNK 协议默认正常
                .doorStatus(doorStatus)
                .currentFloor(currentFloor)
                .targetFloor(targetFloor)
                .direction(direction)
                .alarm(alarm)
                .passenger(passenger)
                .speed(speed)
                .runtime("")            // 由上游填充
                .distance(distance)
                .times(times)
                .rawData(rawData)
                .build();

        LOGGER.debug("[MNKParser] 解析成功: {}", frame);
        return frame;
    }

    @Override
    public String getProtocolVersion() {
        return PROTOCOL_VERSION;
    }

    // ==================== 时间解析（唯一出口） ====================

    /**
     * 从 data[1..20] 解析日期时间，统一返回 LocalDateTime。
     * 整个系统中时间解析的唯一入口，消除多处 SimpleDateFormat 的 ParseException。
     *
     * @param data 完整报文（已 toLowerCase）
     * @return LocalDateTime
     * @throws ProtocolParseException 时间格式异常
     */
    LocalDateTime parseTimestamp(String data) throws ProtocolParseException {
        String dateStr = data.substring(1, 20); // "2020/11/10 11:24:46"
        try {
            return LocalDateTime.parse(dateStr, DT_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ProtocolParseException("MNK-010",
                    "时间格式错误: " + dateStr + " (期望: yyyy/MM/dd HH:mm:ss)", e);
        }
    }

    /**
     * 解析纯时间字符串 (HH:mm:ss) → 秒数。
     */
    long parseTimeToSeconds(String timeStr) throws ProtocolParseException {
        try {
            LocalTime lt = LocalTime.parse(timeStr, TIME_FORMATTER);
            return lt.toSecondOfDay();
        } catch (DateTimeParseException e) {
            throw new ProtocolParseException("MNK-011",
                    "时间格式错误: " + timeStr + " (期望: HH:mm:ss)", e);
        }
    }

    // ==================== 各字段解析（私有，单一职责） ====================

    /**
     * 解析门状态。
     * <pre>
     *   seg2[10..12]:
     *     "10" → 00 关门到位
     *     "00" → 02/03 开关门中(需结合历史状态)
     *     "04" → 01 开门到位
     * </pre>
     */
    private String parseDoorStatus(String seg2) throws ProtocolParseException {
        String subS2 = seg2.substring(10, 12);
        switch (subS2) {
            case "04": return "01"; // 开门到位
            case "10": return "00"; // 关门到位
            case "00": return "";  // 开关门中，由上游根据历史状态判定02/03
            default:
                throw new ProtocolParseException("MNK-020",
                        "非法开关门信号: " + subS2);
        }
    }

    /**
     * 解析目标楼层。
     * <pre>
     *   seg1[1] 按位掩码标识:
     *     '0'=无内召 '1'=1F '2'=2F '4'=3F '8'=4F
     *     组合值如 '3'=1F+2F
     * </pre>
     */
    private String parseTargetFloor(String seg1) throws ProtocolParseException {
        try {
            DestFloorConstant dfc = DestFloorConstant.getDestFloorConstant(seg1.charAt(1));
            return dfc.destFloorDesc;
        } catch (RuntimeException e) {
            throw new ProtocolParseException("MNK-021",
                    "非法目标楼层标识: " + seg1.charAt(1), e);
        }
    }

    /**
     * 解析运行方向。
     * <pre>
     *   seg3[0..2]:
     *     30/31/36/37 → 00 平层
     *     34          → 01 上行
     *     35          → 02 下行
     * </pre>
     */
    private String parseDirection(String seg3) throws ProtocolParseException {
        String dit = seg3.substring(0, 2);
        if (dit.matches("[3][0167]")) return "00"; // 平层
        if ("34".equals(dit)) return "01";           // 上行
        if ("35".equals(dit)) return "02";           // 下行
        throw new ProtocolParseException("MNK-022", "非法运行方向: " + dit);
    }

    /**
     * 解析当前楼层。
     * <pre>
     *   seg3[2..4]:
     *     05→1F  09→2F  0d→3F  11→4F
     * </pre>
     */
    private String parseCurrentFloor(String seg3) throws ProtocolParseException {
        String curFloor = seg3.substring(2, 4);
        switch (curFloor) {
            case "05": return "01";
            case "09": return "02";
            case "0d": return "03";
            case "11": return "04";
            default:
                throw new ProtocolParseException("MNK-023", "非法楼层信号: " + curFloor);
        }
    }

    /**
     * 推理乘客状态。
     */
    private String inferPassenger(String targetFloor, String doorStatus) {
        if (!"无".equals(targetFloor)) {
            return "01"; // 有内召 → 有乘客
        }
        if ("01".equals(doorStatus)) {
            return "00"; // 开门到位且无内召 → 乘客已离开
        }
        return "00";
    }

    /**
     * 解析速度（预留，后续根据需求实现）。
     */
    private double parseSpeed(String seg3) {
        return 0.0;
    }
}
