package com.cpw.topic.download.payload;

public final class DeviceInfo {
    private final int deviceType;
    private final int deviceId;
    private final int deviceArea;
    private final int reserved1;
    private final int reserved2;
    private final int sampleDuration;
    private final int counter;

    public DeviceInfo(int deviceType, int deviceId, int deviceArea, int reserved1,
                      int reserved2, int sampleDuration, int counter) {
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        this.deviceArea = deviceArea;
        this.reserved1 = reserved1;
        this.reserved2 = reserved2;
        this.sampleDuration = sampleDuration;
        this.counter = counter;
    }

    public int getDeviceType() { return deviceType; }
    public int getDeviceId() { return deviceId; }
    public int getDeviceArea() { return deviceArea; }
    public int getReserved1() { return reserved1; }
    public int getReserved2() { return reserved2; }
    public int getSampleDuration() { return sampleDuration; }
    public int getCounter() { return counter; }
}
