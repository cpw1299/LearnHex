package com.cpw.topic.download.payload;

import java.util.Locale;

/**
 * Hex 字节读取器。
 *
 * <p>核心思想：协议解析代码只表达“读取几个 byte”，不要到处手写
 * substring(pos, pos + n)。offset 统一以 byte 为单位。</p>
 */
public final class HexReader {

    private final byte[] data;
    private int offset;

    public HexReader(String hex) {
        if (hex == null) {
            throw new PayloadParseException("payload 不能为 null", 0);
        }

        String normalized = hex.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if ((normalized.length() & 1) != 0) {
            throw new PayloadParseException("Hex 长度必须是偶数", normalized.length() / 2);
        }

        this.data = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new PayloadParseException("发现非法 Hex 字符", i / 2);
            }
            data[i / 2] = (byte) ((high << 4) | low);
        }
    }

    public int position() {
        return offset;
    }

    public int remaining() {
        return data.length - offset;
    }

    public boolean hasRemaining() {
        return remaining() > 0;
    }

    public int readUInt8() {
        require(1);
        return data[offset++] & 0xFF;
    }

    public int readUInt16() {
        require(2);
        int value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;
        return value;
    }

    public long readUInt32() {
        require(4);
        long value = ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
        offset += 4;
        return value;
    }

    public byte[] readBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length 不能小于 0");
        }
        require(length);
        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        offset += length;
        return result;
    }

    public String readHex(int length) {
        byte[] bytes = readBytes(length);
        StringBuilder result = new StringBuilder(length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }

    public void require(int length) {
        if (remaining() < length) {
            throw new PayloadParseException(
                    "数据不足，需要 " + length + " byte，但只剩 " + remaining() + " byte",
                    offset);
        }
    }
}
