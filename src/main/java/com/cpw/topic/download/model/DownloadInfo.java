package com.cpw.topic.download.model;

import lombok.Data;

/**
 * download/info 消息体 —— 文件元信息
 */
@Data
public class DownloadInfo {

    /** 下载任务 ID */
    private Long downloadTaskId;

    /** 当前文件序号（从 1 开始） */
    private Integer fileNum;

    /** 总文件数 */
    private Integer fileSum;

    /** 当前文件名 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;
}
