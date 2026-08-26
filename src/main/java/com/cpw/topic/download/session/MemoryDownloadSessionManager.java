package com.cpw.topic.download.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存（ConcurrentHashMap）的下载会话管理器
 */
@Slf4j
@Component
public class MemoryDownloadSessionManager
        implements DownloadSessionManager {

    private final Map<Long, DownloadSession> map
            = new ConcurrentHashMap<>();

    @Override
    public DownloadSession create(Long taskId) {
        DownloadSession session = new DownloadSession();
        session.setTaskId(taskId);
        session.setStatus(DownloadStatus.INIT);
        session.setStartTime(System.currentTimeMillis());
        session.setLastUpdateTime(System.currentTimeMillis());
        map.put(taskId, session);
        log.debug("[MemoryDownloadSessionManager] 创建会话, taskId={}", taskId);
        return session;
    }

    @Override
    public DownloadSession get(Long taskId) {
        if (map.isEmpty()) {
            return null;
        }
        DownloadSession session = map.get(taskId);
        if (session != null) {
            session.setLastUpdateTime(System.currentTimeMillis());
        }
        return session;
    }

    @Override
    public boolean checkSession(Long taskId) {
        return map.containsKey(taskId);
    }

    @Override
    public void remove(Long taskId) {
        map.remove(taskId);
        log.debug("[MemoryDownloadSessionManager] 移除会话, taskId={}", taskId);
    }

    @Override
    public boolean exists(Long taskId) {
        return map.containsKey(taskId);
    }

    /**
     * 定时清理超时会话（每 30 秒执行一次）
     * 超时阈值：30 分钟（1_800_000 ms）
     */
    @Scheduled(fixedRate = 30_000)
    public void scheduledCleanup() {
        cleanExpired(1_800_000);
    }

    @Override
    public int cleanExpired(long maxIdleMs) {
        long now = System.currentTimeMillis();
        int count = 0;
        Iterator<Map.Entry<Long, DownloadSession>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, DownloadSession> entry = it.next();
            DownloadSession session = entry.getValue();
            if (now - session.getLastUpdateTime() > maxIdleMs) {
                session.setStatus(DownloadStatus.FAILED);
                it.remove();
                count++;
                log.warn("[MemoryDownloadSessionManager] 会话超时清理, taskId={}, idleMs={}",
                        entry.getKey(), now - session.getLastUpdateTime());
            }
        }
        if (count > 0) {
            log.info("[MemoryDownloadSessionManager] 清理超时会话, count={}", count);
        }
        return count;
    }

}