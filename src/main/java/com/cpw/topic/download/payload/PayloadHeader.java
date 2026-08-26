package com.cpw.topic.download.payload;

public final class PayloadHeader {
    private final String header;
    private final long startTimeSec;
    private final int startTimeMsec;
    private final long packetLength;
    private final int compressFlag;

    public PayloadHeader(String header, long startTimeSec, int startTimeMsec,
                         long packetLength, int compressFlag) {
        this.header = header;
        this.startTimeSec = startTimeSec;
        this.startTimeMsec = startTimeMsec;
        this.packetLength = packetLength;
        this.compressFlag = compressFlag;
    }

    public String getHeader() { return header; }
    public long getStartTimeSec() { return startTimeSec; }
    public int getStartTimeMsec() { return startTimeMsec; }
    public long getPacketLength() { return packetLength; }
    public int getCompressFlag() { return compressFlag; }

    public long getTimestampMillis() {
        return startTimeSec * 1000L + startTimeMsec;
    }
}
