package com.cpw.topic.download.listener;

import com.cpw.topic.download.event.DownloadDataEvent;
import com.cpw.topic.download.service.DownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 数据存储监听器 —— 处理下载数据的持久化
 */
@Slf4j
@Component
public class StorageEventListener {

    @Resource
    private DownloadService downloadService;

    @EventListener
    public void onData(DownloadDataEvent event) {
        byte[] payload = event.getPayload();
        if (payload == null || payload.length == 0) {
            log.warn("[StorageEventListener] 收到空数据包");
            return;
        }
        System.out.println(Arrays.toString(payload));

        // TODO 第1步：解析 data 包（JSON / 二进制）
        // DownloadDataPacket packet = parser.parseDataPacket(payload);

        // TODO 第2步：写入文件系统
        // FileUtil.writeBytes(packet.getData(), downloadDir + "/" + packet.getDownloadTaskId());

        // TODO 第3步：写入数据库
        // downloadCommandService.updateDataStatus(packet.getDownloadTaskId(), packet.getPacketNum());

        // TODO 第4步：更新会话接收计数
        // downloadService.receiveData(packet);

        log.debug("[StorageEventListener] 收到数据, size={} bytes (暂未实现持久化)", payload.length);
    }
}