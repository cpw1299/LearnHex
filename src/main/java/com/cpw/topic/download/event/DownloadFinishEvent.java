package com.cpw.topic.download.event;

import lombok.Getter;

/**
 * 下载完成事件
 */
@Getter
public class DownloadFinishEvent {

    private final Long taskId;

    private final Integer code;

    private final String message;

    public DownloadFinishEvent(Long taskId, Integer code, String message) {
        this.taskId = taskId;
        this.code = code;
        this.message = message;
    }
}
