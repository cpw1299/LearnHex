package com.cpw.topic.strategy;

import com.cpw.topic.MqttBusinessStrategy;
import com.cpw.topic.MqttBusinessStrategyFactory;
import com.cpw.topic.download.event.*;
import com.cpw.topic.download.model.DownloadInfo;
import com.cpw.topic.download.parser.DownloadMessageParser;
import com.cpw.topic.download.session.DownloadSessionManager;
import com.cpw.topic.download.MqttResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 演示用 MQTT 业务策略 — 监听 demo/topic 主题
 * <p>
 * 用于验证 {@link MqttBusinessStrategyFactory} 的策略路由功能。
 * 收到消息后仅打印日志，不做实际业务处理。
 * </p>
 *
 * @author up
 */
@Slf4j
@Component
public class BeidouDownloadStrategy implements MqttBusinessStrategy {

    @Resource
    private ApplicationEventPublisher publisher;

    @Resource
    private DownloadMessageParser parser;

    @Resource
    private DownloadSessionManager downloadSessionManager;

//    @Resource
//    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String getTopicName() {
        return "download";
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        log.info("[BeidouDownloadStrategy] 收到下载消息, topic={}, size={}", topic, payload.length);
//        downloadDispatcher.dispatch(topic, payload);
        this.dispatch(topic, payload);
    }

    public void dispatch(String topic, byte[] payload) {

        if (payload == null || payload.length == 0) {
            log.warn("[DownloadDispatcher] 收到空消息, topic={}", topic);
            return;
        }
//        if (topic.startsWith("download")) {
//            stringRedisTemplate.opsForZSet().add("mqtt", Arrays.toString(payload), 1);
//        }

        try {
            switch (topic) {

                case "download/start":
                    MqttResponse startResp = parser.parseResponse(payload);
                    publisher.publishEvent(new DownloadStartEvent(startResp.getTaskId(), (long) startResp.getCode()));
                    log.info("[DownloadDispatcher] 收到下载启动消息, taskId={}", startResp.getTaskId());
                    break;

                case "download/info":
                    DownloadInfo info = parser.parseInfo(payload);
                    publisher.publishEvent(new DownloadInfoEvent(info));
                    log.debug("[DownloadDispatcher] 收到文件元信息, taskId={}, file={}",
                            info.getDownloadTaskId(), info.getFilename());
                    break;

                case "download/data":
                    publisher.publishEvent(new DownloadDataEvent(payload));
                    log.debug("[DownloadDispatcher] 收到下载数据, size={} bytes", payload.length);
                    break;

                case "interact/edge2cloud":
                    // {"device_id":1,"device_type":901,"device_area":1,"response_id":0,"response_command":"","response_code":-1,"response_time":"2026-08-13 17:02:34 +0800 (CST)"}
                    // response_code 非0 都是失败
                    // 失败时，download/info、download/data 不发送消息
                    MqttResponse finishResp2 = parser.parseResponse(payload);
                    publisher.publishEvent(new DownloadFinishEvent(
                            finishResp2.getTaskId(),
                            finishResp2.getCode(),
                            finishResp2.getMessage()
                    ));
                    log.info("[DownloadDispatcher] 收到下载完成响应, taskId={}, code={}",
                            finishResp2.getTaskId(), finishResp2.getCode());
                    break;

                default:
                    log.warn("[DownloadDispatcher] 未知 topic: {}", topic);
            }
        } catch (Exception e) {
            log.error("[DownloadDispatcher] 处理消息失败, topic={}", topic, e);
            publisher.publishEvent(new DownloadFailEvent(null, e.getMessage(), payload));
        }
    }


}
