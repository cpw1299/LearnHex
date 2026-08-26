package com.cpw.framework.mqtt.producer;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * MQTT 消息生产者 — 向指定主题发送字符串消息
 *
 * @author up
 */
@Slf4j
@Component
public class MqttProducer {

    @Resource
    private MqttClient mqttClient;

    /**
     * 发送字符串消息到指定主题
     *
     * @param topic   目标主题
     * @param message 消息内容
     */
    public void send(String topic, String message) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.error("[MQTT生产者] MQTT 客户端未就绪（未初始化或未连接），无法发送消息");
            return;
        }
        if (message == null || message.isEmpty()) {
            log.warn("[MQTT生产者] 消息内容为空，跳过发送: topic={}", topic);
            return;
        }
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttClient.publish(topic, mqttMessage);
            log.info("[MQTT生产者] 消息发送成功: topic={}, payload={}", topic, message);
        } catch (MqttException e) {
            log.error("[MQTT生产者] 消息发送失败: topic={}, payload={}", topic, message, e);
        }
    }
}
