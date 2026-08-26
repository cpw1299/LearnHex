package com.cpw.topic.download;

import lombok.Data;

/**
 * MQTT 通用响应体 —— 对应 interact/edge2cloud 消息
 */
@Data
public class MqttResponse {

    /** 任务 ID */
    private Long taskId;

    /** 响应码（0=成功，非0=失败） */
    private Integer code;

    /** 响应消息 */
    private String message;
}
