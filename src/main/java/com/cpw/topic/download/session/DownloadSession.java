package com.cpw.topic.download.session;

import lombok.Data;

/**
 * 下载会话 —— 跟踪单个下载任务的生命周期状态
 */
@Data
public class DownloadSession {

    /**
     * download_task_id
     */
    private Long taskId;

    /**
     * request_msg_id
     */
    private Long requestId;

    /**
     * 状态
     */
    private DownloadStatus status;

    /**
     * 总文件数
     */
    private Integer fileSum;

    /**
     * 已接收
     */
    private Integer receiveCount;

    /**
     * 当前文件
     */
    private String currentFilename;

    /** 会话创建时间戳 */
    private long startTime;

    /** 最后更新时间戳 */
    private long lastUpdateTime;

}