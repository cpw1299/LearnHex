package com.cpw.topic.download.payload;

import java.util.Collections;
import java.util.List;

public final class PayloadParseResult {
    private final PayloadHeader header;
    private final DeviceInfo deviceInfo;
    private final List<CanFrame> canFrames;
    private final String crcHex;

    public PayloadParseResult(PayloadHeader header, DeviceInfo deviceInfo,
                              List<CanFrame> canFrames, String crcHex) {
        this.header = header;
        this.deviceInfo = deviceInfo;
        this.canFrames = Collections.unmodifiableList(canFrames);
        this.crcHex = crcHex;
    }

    public PayloadHeader getHeader() { return header; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }
    public List<CanFrame> getCanFrames() { return canFrames; }
    public String getCrcHex() { return crcHex; }
}
