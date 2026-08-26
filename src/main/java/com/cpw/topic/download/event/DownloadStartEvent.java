package com.cpw.topic.download.event;

import lombok.Getter;

/**
 * 下载启动事件
 */
@Getter
public class DownloadStartEvent {

    private final Long taskId;

    private final Long requestId;

    public DownloadStartEvent(Long taskId, Long requestId) {
        this.taskId = taskId;
        this.requestId = requestId;
    }
}
