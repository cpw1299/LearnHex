package com.cpw.framework.mqtt.consumer;

import com.cpw.framework.mqtt.config.MqttConfig;
import com.cpw.topic.MqttBusinessStrategy;
import com.cpw.topic.MqttBusinessStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * MQTT 消息消费者 — 接收并处理订阅主题的消息
 * <p>
 * 实现 {@link MqttCallbackExtended} 接口，在连接完成（含自动重连）后自动恢复订阅。
 *
 * @author up
 */
@Slf4j
@Component
public class MqttConsumer implements MqttCallbackExtended {

    @Resource
    private MqttConfig mqttConfig;

    @Resource
    private MqttBusinessStrategyFactory strategyFactory;

    /**
     * 连接完成回调（首次连接成功 + 自动重连成功均会触发）
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[MQTT消费者] 连接完成: reconnect={}, serverURI={}", reconnect, serverURI);
        MqttClient client = mqttConfig.getMqttClient();
        if (client != null) {
            try {
                mqttConfig.subscribeTopics(client);
                log.info("[MQTT消费者] {} 后订阅主题成功", reconnect ? "自动重连" : "首次连接");
            } catch (MqttException e) {
                log.error("[MQTT消费者] 订阅主题失败", e);
            }
        }
    }

    /**
     * 连接断开回调
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.error("[MQTT消费者] 连接已断开", cause);
    }

    /**
     * 消息到达回调
     *
     * @param topic   主题
     * @param message MQTT 消息
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if ("trdpdata".equals(topic)){
            return;
        }
            MqttClient mqttClient = mqttConfig.getMqttClient();
        if (message.getPayload().length == 0) {
            log.warn("[MQTT消费者] 收到空消息，跳过处理: {} topic={}", mqttClient.getCurrentServerURI(), topic);
            return;
        }
//        log.info("[MQTT消费者] 收到消息: {} topic={}, qos={}, payload={}", mqttClient.getCurrentServerURI(),
//                topic, message.getQos(), new String(message.getPayload()));

        MqttBusinessStrategy strategy = strategyFactory.getStrategy(topic);
        if (strategy != null) {
            strategy.processMessage(topic, message.getPayload());
        } else {
            log.warn("[MQTT消费者] Topic={} 无对应的业务策略，消息将被忽略！{}", topic, message.getPayload());
        }
    }

    /**
     * 消息投递完成回调
     *
     * @param token 投递令牌
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("[MQTT消费者] 消息投递完成: messageId={}", token.getMessageId());
    }
}
