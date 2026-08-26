package com.cpw.topic.download.listener;

import com.cpw.topic.download.event.DownloadFailEvent;
import com.cpw.topic.download.event.DownloadFinishEvent;
import com.cpw.topic.download.event.DownloadInfoEvent;
import com.cpw.topic.download.event.DownloadStartEvent;
import com.cpw.topic.download.model.DownloadInfo;
import com.cpw.topic.download.service.DownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 会话状态监听器 —— 根据下载事件更新会话状态
 */
@Slf4j
@Component
public class SessionEventListener {

    @Resource
    private DownloadService downloadService;

    @EventListener
    public void onStart(DownloadStartEvent event) {
        downloadService.start(event.getTaskId(), event.getRequestId());
    }

    @EventListener
    public void onInfo(DownloadInfoEvent event) {
        DownloadInfo info = event.getInfo();
        if (info == null) {
            log.warn("[SessionEventListener] 收到空的 DownloadInfoEvent");
            return;
        }
        boolean checkSession = downloadService.checkSession(info.getDownloadTaskId());
        if (!checkSession) {
            downloadService.start(info.getDownloadTaskId(), null);
        }
        downloadService.receiveInfo(info);
    }

    @EventListener
    public void onFinish(DownloadFinishEvent event) {
        downloadService.finish(event.getTaskId(), event.getCode(), event.getMessage());
    }

    @EventListener
    public void onFail(DownloadFailEvent event) {
        boolean checkSession = downloadService.checkSession(event.getTaskId());
        if (checkSession) {
            downloadService.fail(event.getTaskId(), event.getReason());
        } else {
            log.error("SessionEventListener#onFail 触发失败时，会话不存在！");
        }
    }
}