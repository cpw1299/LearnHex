package com.cpw.topic.download.service;

import com.cpw.topic.download.model.DownloadDataPacket;
import com.cpw.topic.download.model.DownloadInfo;
import com.cpw.topic.download.session.DownloadSession;

/**
 * 下载业务服务接口
 */
public interface DownloadService {

    /**
     * 检查会话
     */
    boolean checkSession(Long taskId);

    /**
     * 启动下载任务
     */
    DownloadSession start(Long taskId, Long requestId);

    /**
     * 接收文件元信息
     */
    void receiveInfo(DownloadInfo info);

    /**
     * 接收数据包
     */
    void receiveData(DownloadDataPacket packet);

    /**
     * 下载完成
     */
    void finish(Long taskId, Integer code, String message);

    /**
     * 下载失败
     */
    void fail(Long taskId, String reason);
}