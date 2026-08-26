package com.cpw.topic;

import com.cpw.framework.mqtt.consumer.MqttConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 业务策略工厂
 * <p>
 * 收集所有 {@link MqttBusinessStrategy} 实现，按 {@code topicName} 建立映射。
 * {@link MqttConsumer} 收到消息后，
 * 通过本工厂获取对应的策略并执行业务逻辑。
 * </p>
 * <p>
 * 添加新业务 Topic 时只需：<br>
 * 1. 实现 {@link MqttBusinessStrategy} 接口<br>
 * 2. 将该实现类注册为 Spring Bean（{@code @Component}）<br>
 * 3. 工厂会自动将其纳入路由
 * </p>
 *
 * @author up
 */
@Slf4j
@Component
public class MqttBusinessStrategyFactory {

    @Resource
    private List<MqttBusinessStrategy> strategyList;

    public MqttBusinessStrategyFactory(List<MqttBusinessStrategy> strategyList) {
        this.strategyList = strategyList;
    }

    /**
     * topicName -> strategy 映射
     */
    private final Map<String, MqttBusinessStrategy> strategyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (strategyList == null || strategyList.isEmpty()) {
            log.warn("[MQTT策略工厂] 未发现任何 MqttBusinessStrategy 实现");
            return;
        }
        for (MqttBusinessStrategy strategy : strategyList) {
            String topicName = strategy.getTopicName();
            if (topicName == null || topicName.isEmpty()) {
                log.warn("[MQTT策略工厂] 策略 {} 的 topicName 为空，跳过注册", strategy.getClass().getSimpleName());
                continue;
            }
            if (strategyMap.containsKey(topicName)) {
                log.warn("[MQTT策略工厂] topicName={} 已被 {} 占用，策略 {} 将被忽略",
                        topicName, strategyMap.get(topicName).getClass().getSimpleName(),
                        strategy.getClass().getSimpleName());
                continue;
            }
            strategyMap.put(topicName, strategy);
            log.info("[MQTT策略工厂] 注册策略: topicName={}, impl={}", topicName, strategy.getClass().getSimpleName());
        }
    }

    /**
     * 根据 Topic 名称获取对应的业务策略。
     * 支持精确匹配和通配符匹配（+ 单级通配符，# 多级通配符）
     *
     * @param topicName Topic 名称
     * @return 匹配的策略，无匹配时返回 {@code null}
     */
    public MqttBusinessStrategy getStrategy(String topicName) {
        // 先尝试精确匹配
        MqttBusinessStrategy strategy = strategyMap.get(topicName);
        if (strategy != null) {
            return strategy;
        }

        // download/ interact/edge 开头的
        if (topicName.startsWith("download/") || topicName.startsWith("interact/edge")) {
            return strategyMap.get("download");
        }

        // 尝试通配符匹配
        for (Map.Entry<String, MqttBusinessStrategy> entry : strategyMap.entrySet()) {
            String pattern = entry.getKey();
            if (matchTopic(pattern, topicName)) {
//                log.debug("[MQTT策略工厂] 通配符匹配成功: pattern={}, topic={}", pattern, topicName);
                return entry.getValue();
            }
        }

        log.warn("[MQTT策略工厂] 未找到对应 topicName={} 的策略", topicName);
        return null;
    }

    /**
     * MQTT topic 通配符匹配
     *
     * @param pattern 订阅模式（可包含 + 和 #）
     * @param topic   实际 topic
     * @return 是否匹配
     */
    private boolean matchTopic(String pattern, String topic) {
        if (pattern == null || topic == null) {
            return false;
        }

        String[] patternParts = pattern.split("/");
        String[] topicParts = topic.split("/");

        int patternLen = patternParts.length;
        int topicLen = topicParts.length;

        for (int i = 0; i < patternLen; i++) {
            String patternPart = patternParts[i];

            // # 匹配剩余所有级别
            if ("#".equals(patternPart)) {
                return true;
            }

            // + 匹配单个级别
            if ("+".equals(patternPart)) {
                if (i >= topicLen) {
                    return false;
                }
                continue;
            }

            // 精确匹配
            if (i >= topicLen || !patternPart.equals(topicParts[i])) {
                return false;
            }
        }

        // pattern 和 topic 必须长度相同（除非 pattern 以 # 结尾）
        return patternLen == topicLen;
    }
}
