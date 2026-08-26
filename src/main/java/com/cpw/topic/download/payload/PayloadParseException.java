package com.cpw.topic.download.payload;

/**
 * 单个 MQTT Payload 解析失败时抛出的异常。
 *
 * <p>它和 MQTT、Session、状态机无关，只描述“这段 Hex 不符合我们当前理解的协议”。</p>
 */
public class PayloadParseException extends RuntimeException {

    private final int offset;

    public PayloadParseException(String message, int offset) {
        super(message + " (offset=" + offset + ")");
        this.offset = offset;
    }

    public PayloadParseException(String message, int offset, Throwable cause) {
        super(message + " (offset=" + offset + ")", cause);
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }
}
