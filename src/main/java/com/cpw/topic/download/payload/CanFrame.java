package com.cpw.topic.download.payload;

import java.util.Arrays;

public final class CanFrame {
    private final String header;
    private final long canId;
    private final int type;
    private final int length;
    private final byte[] data;
    private final Integer offsetBytes;

    public CanFrame(String header, long canId, int type, int length,
                    byte[] data, Integer offsetBytes) {
        this.header = header;
        this.canId = canId;
        this.type = type;
        this.length = length;
        this.data = data == null ? new byte[0] : data.clone();
        this.offsetBytes = offsetBytes;
    }

    public String getHeader() { return header; }
    public long getCanId() { return canId; }
    public int getType() { return type; }
    public int getLength() { return length; }
    public byte[] getData() { return data.clone(); }
    public Integer getOffsetBytes() { return offsetBytes; }

    public String getDataHex() {
        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte value : data) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return "CanFrame{" +
                "header='" + header + '\'' +
                ", canId=0x" + Long.toHexString(canId) +
                ", type=" + type +
                ", length=" + length +
                ", data=" + getDataHex() +
                ", offsetBytes=" + offsetBytes +
                '}';
    }
}
