package com.cpw.topic.download.service;

import com.cpw.topic.download.model.DownloadDataPacket;
import com.cpw.topic.download.model.DownloadInfo;
import com.cpw.topic.download.session.DownloadSession;
import com.cpw.topic.download.session.DownloadSessionManager;
import com.cpw.topic.download.session.DownloadStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 默认下载业务实现
 */
@Slf4j
@Service
public class DefaultDownloadService implements DownloadService {

    @Resource
    private DownloadSessionManager sessionManager;

    @Override
    public boolean checkSession(Long taskId) {
        return sessionManager.checkSession(taskId);
    }

    @Override
    public DownloadSession start(Long taskId, Long requestId) {
        DownloadSession session = sessionManager.create(taskId);
        session.setRequestId(requestId);
        session.setStatus(DownloadStatus.RUNNING);
        log.info("[DefaultDownloadService] 下载任务启动, taskId={}, requestId={}", taskId, requestId);
        return session;
    }

    @Override
    public void receiveInfo(DownloadInfo info) {
        DownloadSession session = sessionManager.get(info.getDownloadTaskId());
        if (session == null) {
            log.warn("[DefaultDownloadService] 收到信息但会话不存在, taskId={}", info.getDownloadTaskId());
            return;
        }
        session.setFileSum(info.getFileSum());
        session.setReceiveCount(info.getFileNum());
        session.setCurrentFilename(info.getFilename());
        session.setStatus(DownloadStatus.RUNNING);
        log.debug("[DefaultDownloadService] 更新文件信息, taskId={}, file={}({}/{})",
                info.getDownloadTaskId(), info.getFilename(), info.getFileNum(), info.getFileSum());
    }

    @Override
    public void receiveData(DownloadDataPacket packet) {
        DownloadSession session = sessionManager.get(packet.getDownloadTaskId());
        if (session == null) {
            log.warn("[DefaultDownloadService] 收到数据但会话不存在, taskId={}", packet.getDownloadTaskId());
            return;
        }
        // TODO 持久化数据块到文件或数据库
        log.debug("[DefaultDownloadService] 收到数据包, taskId={}, packetNum={}, size={}",
                packet.getDownloadTaskId(), packet.getPacketNum(),
                packet.getData() != null ? packet.getData().length : 0);
    }

    @Override
    public void finish(Long taskId, Integer code, String message) {
        DownloadSession session = sessionManager.get(taskId);
        if (session == null) {
            log.warn("[DefaultDownloadService] 收到完成但会话不存在, taskId={}", taskId);
            return;
        }
        session.setStatus(DownloadStatus.FINISHED);
        log.info("[DefaultDownloadService] 下载任务完成, taskId={}, code={}, msg={}", taskId, code, message);
    }

    @Override
    public void fail(Long taskId, String reason) {
        DownloadSession session = sessionManager.get(taskId);
        if (session == null) {
            log.warn("[DefaultDownloadService] 收到失败, 会话不存在, taskId={}", taskId);
            return;
        }
        session.setStatus(DownloadStatus.FAILED);
        log.warn("[DefaultDownloadService] 下载任务失败, taskId={}, reason={}", taskId, reason);
        sessionManager.remove(taskId);
    }
}