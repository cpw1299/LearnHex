package com.cpw.topic.download.event;

import lombok.Getter;

/**
 * 下载失败事件
 */
@Getter
public class DownloadFailEvent {

    private final Long taskId;

    private final String reason;

    private final byte[] payload;

    public DownloadFailEvent(Long taskId, String reason) {
        this.taskId = taskId;
        this.reason = reason;
        this.payload = null;
    }

    public DownloadFailEvent(Long taskId, String reason, byte[] payload) {
        this.taskId = taskId;
        this.reason = reason;
        this.payload = payload;
    }
}