package com.cpw.framework.mqtt.config;

import com.cpw.framework.mqtt.consumer.MqttConsumer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MQTT 配置类
 *
 * @author up
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mqtt")
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttConfig {

    /**
     * Broker 地址
     */
    private String brokerUrl = "tcp://localhost:1883";

    /**
     * 客户端 ID
     */
    private String clientId = "phm-vehicle-assist-client";

    /**
     * 用户名（可选）
     */
    private String username;

    /**
     * 密码（可选）
     */
    private String password;

    /**
     * 连接超时时间（秒）
     */
    private int connectionTimeout = 30;

    /**
     * 心跳间隔（秒）
     */
    private int keepAliveInterval = 60;

    /**
     * 订阅主题列表
     */
    private List<TopicItem> topics;

    /**
     * MQTT 客户端实例（创建后缓存，供消费者重连时使用）
     */
    private MqttClient mqttClient;

    /**
     * 主题配置项
     */
    @Getter
    @Setter
    public static class TopicItem {
        private String name;
        private int qos = 1;
    }

    /**
     * 获取主题名称数组
     */
    public String[] getTopicNames() {
        if (topics == null || topics.isEmpty()) {
            return new String[0];
        }
        return topics.stream().map(TopicItem::getName).toArray(String[]::new);
    }

    /**
     * 获取 QoS 数组
     */
    public int[] getTopicQos() {
        if (topics == null || topics.isEmpty()) {
            return new int[0];
        }
        return topics.stream().mapToInt(TopicItem::getQos).toArray();
    }

    /**
     * MQTT 连接选项
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }

        String topicList = topics != null
                ? topics.stream().map(t -> t.getName() + "(" + t.getQos() + ")").collect(Collectors.joining(", "))
                : "none";
        log.info("[MQTT配置] 初始化连接选项: brokerUrl={}, clientId={}, topics=[{}]",
                brokerUrl, clientId, topicList);
        return options;
    }

    /**
     * MQTT 客户端
     * <p>
     * 直接创建并返回客户端实例，连接操作在后台异步执行，
     * 无论连接成功与否都不影响主程序启动。
     * </p>
     */
    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient(MqttConnectOptions mqttConnectOptions, MqttConsumer mqttConsumer) {
        String finalClientId = clientId + "_" + getLocalRealIp();
        try {
            MqttClient client = new MqttClient(brokerUrl, finalClientId, new MemoryPersistence());
            client.setCallback(mqttConsumer);

            // 缓存客户端实例，供消费者重连时使用（即使未连接也缓存）
            this.mqttClient = client;

            // 后台异步连接，不影响主程序启动
            asyncConnect(client, mqttConnectOptions);

            return client;
        } catch (MqttException e) {
            log.error("[MQTT客户端] 创建客户端失败: brokerUrl={}, clientId={}", brokerUrl, finalClientId, e);
            return null;
        }
    }

    /**
     * 后台异步连接 MQTT Broker，连接成功后自动订阅主题
     */
    private void asyncConnect(MqttClient client, MqttConnectOptions options) {
        new Thread(() -> {
            try {
                log.info("[MQTT客户端] 开始异步连接 Broker: {}", brokerUrl);
                client.connect(options);
                log.info("[MQTT客户端] 连接到 Broker 成功: {}", brokerUrl);
                subscribeTopics(client);
            } catch (MqttException e) {
                log.error("[MQTT客户端] 异步连接/订阅失败: brokerUrl={}", brokerUrl, e);
            }
        }, "mqtt-async-connector").start();
    }

    /**
     * 订阅所有配置的主题
     */
    public void subscribeTopics(MqttClient client) throws MqttException {
        String[] topicNames = getTopicNames();
        int[] topicQos = getTopicQos();
        if (topicNames.length == 0) {
            log.warn("[MQTT客户端] 未配置订阅主题");
            return;
        }
        client.subscribe(topicNames, topicQos);
        log.info("[MQTT客户端] 订阅主题成功: count={}", topicNames.length);
        for (int i = 0; i < topicNames.length; i++) {
            log.info("  -> topic={}, qos={}", topicNames[i], topicQos[i]);
        }
    }

    private String getLocalRealIp() {
        try {
            // 遍历所有网络接口
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                System.out.println(netInterface.getDisplayName() + "\t\t\t"  +  netInterface.isUp());
                // 过滤掉回环接口、未启用接口、虚拟接口
                if (netInterface.isLoopback() || netInterface.isVirtual() || !netInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                if (netInterface.getDisplayName().contains("VirtualBox")) {
                    continue;
                }
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    // 只要 IPv4 地址，且不是回环地址
                    if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress()) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.error("获取网络接口信息失败", e);
        }
        try {
            // 兜底方案
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
