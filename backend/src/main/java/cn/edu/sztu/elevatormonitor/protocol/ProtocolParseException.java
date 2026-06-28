package cn.edu.sztu.elevatormonitor.protocol;

/**
 * 协议解析异常 — 统一包装所有解析错误，取代原有的 ParseException / NumberFormatException 等。
 *
 * @author architecture-v2
 * @since 0.2.0
 */
public class ProtocolParseException extends RuntimeException {

    private final String errorCode;

    public ProtocolParseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ProtocolParseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
