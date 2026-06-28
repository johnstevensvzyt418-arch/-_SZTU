package cn.edu.sztu.elevatormonitor.protocol;

/**
 * 协议解析器接口 — 策略模式，支持多种电梯协议扩展。
 *
 * <p>每种电梯品牌实现各自的 Parser，新增品牌无需修改现有代码（开闭原则）。</p>
 *
 * @param <T> 协议帧类型（如 MNKFrame）
 * @author architecture-v2
 * @since 0.2.0
 */
public interface ProtocolParser<T> {

    /**
     * 解析原始 data 字符串为协议帧。
     *
     * @param rawData    原始协议数据字符串
     * @param reportTime 设备上报时间字符串
     * @return 解析后的协议帧
     * @throws ProtocolParseException 解析失败时抛出
     */
    T parse(String rawData, String reportTime) throws ProtocolParseException;

    /**
     * 返回此解析器支持的协议版本。
     */
    String getProtocolVersion();
}
