package com.cpw.topic.parser;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DynamicPacketParser {



    public static SignalConfig buildSignalConfig(String name, int byteOffset, int bitOffset, int lengthBits) {
        return new SignalConfig(name, byteOffset, bitOffset, lengthBits);
    }

    @Getter
    @Setter
    // 信号配置
    public static class SignalConfig {
        String name;
        int byteOffset;
        int bitOffset;
        int lengthBits;

        public SignalConfig(String name, int byteOffset, int bitOffset, int lengthBits) {
            this.name = name;
            this.byteOffset = byteOffset;
            this.bitOffset = bitOffset;
            this.lengthBits = lengthBits;
        }
    }

    @Getter
    @Setter
    // Comid 组配置
    public static class ComIdConfig {
        int comId;
        List<SignalConfig> signals;

        public ComIdConfig(int comId, List<SignalConfig> signals) {
            this.comId = comId;
            this.signals = signals;
        }
    }

    @Getter
    @Setter
    // 解析结果包装类
    public static class ParseResult {
        long utcTimestamp;
        int version;
        // 外层Key是comid，内层Key是signal名，Value是解析值
        Map<Integer, Map<String, String>> comIdData;

        public ParseResult() {
            this.comIdData = new LinkedHashMap<>();
        }
    }

    /**
     * 解析数据包并按 Comid 分组
     *
     * @param hexString  原始十六进制字符串
     * @param configList 动态配置列表
     * @return ParseResult 包含分层结果的对象
     */
    public static ParseResult parsePacketWithComId(String hexString, List<ComIdConfig> configList) {
        byte[] rawData = hexStringToByteArray(hexString);
        ParseResult result = new ParseResult();

        // 1. 提取头部：前4个字节 UTC 时间戳，第5个字节版本号
        result.utcTimestamp = ((long) (rawData[0] & 0xFF) << 24) |
                ((long) (rawData[1] & 0xFF) << 16) |
                ((long) (rawData[2] & 0xFF) << 8) |
                ((long) (rawData[3] & 0xFF));
        result.version = rawData[4] & 0xFF;

        // 2. Payload 从第6个字节开始 (索引 5)
        int payloadStartIndex = 5;
        int currentComidOffset = 0; // 记录当前 comid 在 payload 中的绝对字节偏移

        // 3. 遍历配置，按 Comid 组解析
        for (ComIdConfig comidConfig : configList) {
            // 为当前 comid 创建一个独立的 Map 存放其下属 signal
            Map<String, String> currentComidSignals = new LinkedHashMap<>();

            int maxBitsInComid = 0;

            for (SignalConfig signal : comidConfig.signals) {
                // 计算绝对偏移：基础偏移 + 当前comid在payload中的偏移 + 信号在comid内的字节偏移
                int absoluteByteOffset = payloadStartIndex + currentComidOffset + signal.byteOffset;

                // 提取数据
                long value = extractBits(rawData, absoluteByteOffset, signal.bitOffset, signal.lengthBits);
                currentComidSignals.put(signal.name, String.valueOf(value));

                // 计算当前 signal 占用的最高 bit 位，用于确定这个 comid 块的总长度
                int endBit = signal.byteOffset * 8 + signal.bitOffset + signal.lengthBits;
                if (endBit > maxBitsInComid) {
                    maxBitsInComid = endBit;
                }
            }

            // 将当前 comid 的解析结果放入总 Map 中
            result.comIdData.put(comidConfig.comId, currentComidSignals);

            // 计算当前 comid 占用的字节数，并累加偏移量，为下一个 comid 做准备
            int comidByteLength = (maxBitsInComid + 7) / 8;
            currentComidOffset += comidByteLength;
        }

        return result;
    }

    /**
     * 解析数据包并按 Comid 分组 (紧凑读取版)
     *
     * @param hexString  原始十六进制字符串
     * @param configList 动态配置列表
     * @return ParseResult 包含分层结果的对象
     */
    public static ParseResult parsePacketWithComId2(String hexString, List<ComIdConfig> configList) {
        byte[] rawData = hexStringToByteArray(hexString);
        ParseResult result = new ParseResult();

        // 1. 提取头部
        result.utcTimestamp = ((long) (rawData[0] & 0xFF) << 24) |
                ((long) (rawData[1] & 0xFF) << 16) |
                ((long) (rawData[2] & 0xFF) << 8) |
                ((long) (rawData[3] & 0xFF));
        result.version = rawData[4] & 0xFF;

        // 2. Payload 从第6个字节开始 (索引 5)
        int payloadStartIndex = 5;
        int currentBitOffset = 0; // 记录当前在整个 Payload 中的【绝对位偏移】

        // 3. 遍历配置，按 Comid 组解析
        for (ComIdConfig comidConfig : configList) {
            Map<String, String> currentComidSignals = new LinkedHashMap<>();

            for (SignalConfig signal : comidConfig.signals) {
                // 将当前的 bit 偏移转换为字节数组中的绝对位置
                int absoluteByteOffset = payloadStartIndex + (currentBitOffset / 8);
                int bitOffsetInByte = currentBitOffset % 8;

                // 紧凑提取数据
                long value = extractBits(rawData, absoluteByteOffset, bitOffsetInByte, signal.lengthBits);
                currentComidSignals.put(signal.name, String.valueOf(value));

                // 累加当前 signal 占用的 bit 位数，为下一个 signal 准备
                currentBitOffset += signal.lengthBits;
            }

            result.comIdData.put(comidConfig.comId, currentComidSignals);
        }

        return result;
    }


    /**
     * 核心：从 byte 数组中提取指定位长度、偏移量的无符号整数值
     */
    private static long extractBits(byte[] data, int byteOffset, int bitOffset, int lengthBits) {
        long value = 0;
        int bitsRead = 0;
        int currentBytePos = byteOffset;
        int currentBitPos = bitOffset;

        while (bitsRead < lengthBits) {
            if (currentBytePos >= data.length) break;

            int byteVal = data[currentBytePos] & 0xFF;
            int bitsToRead = Math.min(8 - currentBitPos, lengthBits - bitsRead);

            int mask = (1 << bitsToRead) - 1;
            int extractedBits = (byteVal >> (8 - currentBitPos - bitsToRead)) & mask;

            value = (value << bitsToRead) | extractedBits;

            bitsRead += bitsToRead;
            currentBitPos += bitsToRead;

            if (currentBitPos >= 8) {
                currentBitPos = 0;
                currentBytePos++;
            }
        }
        return value;
    }

    private static byte[] hexStringToByteArray(String s) {
        s = s.replaceAll("\\s", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void main(String[] args) {
        String hex = "6a6998fb019fe282aab285a7c79b6fc71eae";
        List<ComIdConfig> configList = JSONObject.parseArray(JSONObject.toJSONString(PacketParser.getConfigList()), ComIdConfig.class);
        ParseResult parseResult = parsePacketWithComId2(hex, configList);
        System.out.println(JSONObject.toJSONString(parseResult));
    }
}

