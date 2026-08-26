package com.cpw.topic.download.model;

import lombok.Data;

/**
 * download/data 消息体 —— 二进制数据分包
 */
@Data
public class DownloadDataPacket {

    /** 下载任务 ID */
    private Long downloadTaskId;

    /** 分包序号 */
    private Integer packetNum;

    /** 二进制数据块 */
    private byte[] data;

    /** 校验值（可选） */
    private String checksum;
}
