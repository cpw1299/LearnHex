package com.cpw.topic;

/**
 * MQTT 消息业务策略接口
 * <p>
 * 每种业务 Topic 实现该接口，通过 {@link #getTopicName()} 返回的 Topic 名称
 * 作为策略工厂的 Key，由 {@link MqttBusinessStrategyFactory} 统一路由分发。
 * </p>
 *
 * @author up
 */
public interface MqttBusinessStrategy {

    /**
     * 返回当前策略监听的 Topic 名称，同时也是策略工厂的匹配 Key。
     *
     * @return Topic 名称
     */
    String getTopicName();

    /**
     * 执行业务逻辑。
     *
     * @param topic MQTT Topic
     * @param payload MQTT 消息体
     */
    void processMessage(String topic, byte[] payload);
}
