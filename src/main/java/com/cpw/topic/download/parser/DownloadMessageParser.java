package com.cpw.topic.download.parser;

import com.alibaba.fastjson.JSONObject;
import com.cpw.topic.download.model.DownloadDataPacket;
import com.cpw.topic.download.model.DownloadInfo;
import com.cpw.topic.download.MqttResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 下载消息解析器 —— 将 MQTT byte[] payload 解析为对应的模型对象
 */
@Slf4j
@Component
public class DownloadMessageParser {

    private final ObjectMapper objectMapper;

    public DownloadMessageParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 解析 download/info 消息
     */
    public DownloadInfo parseInfo(byte[] payload) {
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, DownloadInfo.class);
        } catch (Exception e) {
            log.error("[DownloadParser] 解析 download/info 失败: {}", new String(payload, StandardCharsets.UTF_8), e);
            throw new IllegalArgumentException("解析 download/info 失败", e);
        }
    }

    /**
     * 解析 download/data 消息
     */
    public DownloadDataPacket parseDataPacket(byte[] payload) {
        // data 可能为 JSON 也可能为纯二进制，尝试 JSON 解析
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            if (json.trim().startsWith("{")) {
                return objectMapper.readValue(json, DownloadDataPacket.class);
            }
        } catch (Exception ignored) {
            // 非 JSON 格式，走二进制解析
        }

        // 二进制格式：前 8 字节为 downloadTaskId，剩余为数据块
        DownloadDataPacket packet = new DownloadDataPacket();
        if (payload.length >= 8) {
            long taskId = 0;
            for (int i = 0; i < 8; i++) {
                taskId = (taskId << 8) | (payload[i] & 0xFF);
            }
            packet.setDownloadTaskId(taskId);
            byte[] data = new byte[payload.length - 8];
            System.arraycopy(payload, 8, data, 0, data.length);
            packet.setData(data);
        } else {
            packet.setData(payload);
        }
        return packet;
    }

    /**
     * 解析 interact/edge2cloud 响应消息（MQTT_RESPONSE）
     */
    public MqttResponse parseResponse(byte[] payload) {
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            System.out.printf("响应消息（MQTT_RESPONSE）: %s\n", json);
            return JSONObject.parseObject(json, MqttResponse.class);
        } catch (Exception e) {
            log.error("[DownloadParser] 解析 MQTT_RESPONSE 失败: {}", new String(payload, StandardCharsets.UTF_8), e);
            throw new IllegalArgumentException("解析 MQTT_RESPONSE 失败", e);
        }
    }
}