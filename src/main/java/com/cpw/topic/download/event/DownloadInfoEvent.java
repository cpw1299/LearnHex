package com.cpw.topic.download.event;

import com.cpw.topic.download.model.DownloadInfo;
import lombok.Getter;

@Getter
public class DownloadInfoEvent {

    private final DownloadInfo info;

    public DownloadInfoEvent(DownloadInfo info) {
        this.info = info;
    }

}
