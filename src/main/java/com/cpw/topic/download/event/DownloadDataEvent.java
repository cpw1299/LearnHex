package com.cpw.topic.download.event;

import lombok.Getter;

@Getter
public class DownloadDataEvent {

    private final byte[] payload;

    public DownloadDataEvent(byte[] payload){

        this.payload=payload;

    }

}
