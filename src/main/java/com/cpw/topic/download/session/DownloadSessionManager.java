package com.cpw.topic.download.session;

/**
 * 下载会话管理器接口
 */
public interface DownloadSessionManager {

    DownloadSession create(Long taskId);

    DownloadSession get(Long taskId);

    /**
     * 检查会话
     */
    boolean checkSession(Long taskId);

    void remove(Long taskId);

    /**
     * 检查会话是否存在
     */
    boolean exists(Long taskId);

    /**
     * 清理超过指定毫秒数未更新的会话
     * @param maxIdleMs 最大空闲时间（毫秒）
     * @return 清理的会话数
     */
    int cleanExpired(long maxIdleMs);

}