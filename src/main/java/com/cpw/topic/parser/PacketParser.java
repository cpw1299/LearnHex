package com.cpw.topic.parser;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

public class PacketParser {

    @Getter
    @Setter
    // 定义信号配置的数据结构
    static class SignalConfig {
        String name;
        int byteOffset;
        int bitOffset;
        int lengthBits;
        String type;

        public SignalConfig(String name, int byteOffset, int bitOffset, int lengthBits, String type) {
            this.name = name;
            this.byteOffset = byteOffset;
            this.bitOffset = bitOffset;
            this.lengthBits = lengthBits;
            this.type = type;
        }
    }

    @Getter
    @Setter
    // 定义COMID配置的数据结构
    static class ComidConfig {
        int comid;
        List<SignalConfig> signals;

        public ComidConfig(int comid, List<SignalConfig> signals) {
            this.comid = comid;
            this.signals = signals;
        }
    }

    /**
     * 解析整个数据包
     *
     * @param hexString  原始十六进制字符串
     * @param configList 协议配置列表
     * @return 解析结果Map
     */
    public static Map<String, Object> parsePacket(String hexString, List<ComidConfig> configList) {
        byte[] rawData = hexStringToByteArray(hexString);
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 解析前4个字节为UTC时间戳 (大端序)
        long utcTimestamp = ((long) (rawData[0] & 0xFF) << 24) |
                ((long) (rawData[1] & 0xFF) << 16) |
                ((long) (rawData[2] & 0xFF) << 8) |
                ((long) (rawData[3] & 0xFF));
        result.put("UTC_Time", utcTimestamp);

        // 2. 解析第5个字节为配置版本号
        int version = rawData[4] & 0xFF;
        result.put("Version", version);

        // 3. 从第6个字节开始是Payload (偏移量为5)
        int payloadStartIndex = 5;
        int currentComidOffset = 0; // 记录当前comid在payload中的字节起始偏移

        // 4. 遍历配置进行解析
        for (ComidConfig comidConfig : configList) {
            for (SignalConfig signal : comidConfig.signals) {
                // 计算在原始数据包中的绝对字节偏移
                int absoluteByteOffset = payloadStartIndex + currentComidOffset + signal.byteOffset;

                // 提取数据
                long value = extractBits(rawData, absoluteByteOffset, signal.bitOffset, signal.lengthBits);

                // 将结果放入Map
                result.put(signal.name, String.valueOf(value));
            }

            // 假设每个comid的数据是紧凑排列的，计算当前comid占用的总字节数
            // 计算方式：找出该comid内最大的 (byteoffset * 8 + bitoffset + len) 然后转换为字节数
            int maxBits = 0;
            for (SignalConfig signal : comidConfig.signals) {
                int endBit = signal.byteOffset * 8 + signal.bitOffset + signal.lengthBits;
                if (endBit > maxBits) {
                    maxBits = endBit;
                }
            }
            // 向上取整计算字节数，并累加到 currentComidOffset
            currentComidOffset += (maxBits + 7) / 8;
        }

        return result;
    }

    /**
     * 解析整个数据包
     *
     * @param hexString  原始十六进制字符串
     * @param configList 协议配置列表
     * @return 解析结果Map
     */
    public static Map<String, Object> parsePacket2(String hexString, List<ComidConfig> configList) {
        byte[] rawData = hexStringToByteArray(hexString);
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 解析前4个字节为UTC时间戳 (大端序)
        long utcTimestamp = ((long) (rawData[0] & 0xFF) << 24) |
                ((long) (rawData[1] & 0xFF) << 16) |
                ((long) (rawData[2] & 0xFF) << 8) |
                ((long) (rawData[3] & 0xFF));
        result.put("UTC_Time", utcTimestamp);

        // 2. 解析第5个字节为配置版本号
        int version = rawData[4] & 0xFF;
        result.put("Version", version);

        // 3. 从第6个字节开始是Payload (偏移量为5)
        int payloadStartIndex = 5;
        int currentComidOffset = 0; // 记录当前comid在payload中的字节起始偏移
        int currentBitOffset = 0; // 记录当前在整个 Payload 中的【绝对位偏移】

        // 4. 遍历配置进行解析
        for (ComidConfig comidConfig : configList) {
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

            result.put(String.valueOf(comidConfig.comid), JSONObject.toJSONString(currentComidSignals));
        }


        return result;
    }


    /**
     * 核心方法：从byte数组中提取指定位长度、偏移量的无符号整数值
     *
     * @param data       原始字节数组
     * @param byteOffset 字节偏移
     * @param bitOffset  字节内的位偏移 (0-7)
     * @param lengthBits 读取的位长度
     * @return 提取出的长整型数值
     */
    private static long extractBits(byte[] data, int byteOffset, int bitOffset, int lengthBits) {
        long value = 0;
        int bitsRead = 0;
        int currentBytePos = byteOffset;
        int currentBitPos = bitOffset;

        while (bitsRead < lengthBits) {
            // 确保不越界
            if (currentBytePos >= data.length) {
                break;
            }

            // 获取当前字节，并转为无符号
            byte b = data[currentBytePos];
            int byteVal = b & 0xFF;

            // 计算当前字节中能读取的位数
            int bitsToRead = Math.min(8 - currentBitPos, lengthBits - bitsRead);

            // 移位操作提取需要的位
            // 比如bitOffset=0, 读取8位： (byteVal >> 0) & 0xFF
            int mask = (1 << bitsToRead) - 1;
            int extractedBits = (byteVal >> (8 - currentBitPos - bitsToRead)) & mask;

            // 将提取的位拼接到结果中 (大端序拼接)
            value = (value << bitsToRead) | extractedBits;

            bitsRead += bitsToRead;
            currentBitPos += bitsToRead;

            // 如果当前字节读取完毕，移动到下一个字节
            if (currentBitPos >= 8) {
                currentBitPos = 0;
                currentBytePos++;
            }
        }
        return value;
    }




    /**
     * 校验数据包的CRC，并返回纯净的业务数据十六进制字符串
     * <p>
     * 第1字符：0 心跳数据
     * 2-5	：00C5 日期天数=197→ 2026-07-17
     * 6-13：0000CC01 时间秒数=52225→ 14:30:25
     *
     * @param hex 完整数据包 (帧头 + 业务数据 + CRC)
     * @return 校验通过返回业务数据的十六进制字符串；失败返回 null
     */
    public static String validateAndExtractBusinessHex(String hex) {
        if (hex == null || hex.length() < 12) { // 至少 AABB(4字符) + CRC(8字符)
            System.out.println("数据包长度不合法");
            return null;
        }

        // 1. 去除空格并转大写，统一格式
        hex = hex.replaceAll("\\s", "").toUpperCase();

        // 2. 校验帧头
        if (!hex.startsWith(CRC32Verify.FRAME_HEADER)) {
            System.out.println("帧头错误，未找到 AABB");
            return null;
        }

        // 3. 提取业务数据和待校验的CRC (假设CRC是最后4个字节，即8个十六进制字符)
        int totalLen = hex.length();
        String receivedCrcHex = hex.substring(totalLen - 8);
        // 去掉待校验的CRC。
        String businessHex = hex.substring(0, totalLen - 8);

        // 4. 计算业务数据的 CRC
        String calculatedCrcHex = CRC32Verify.crc32HexFromHex(businessHex);

        // 5. 比对校验码
        if (!receivedCrcHex.equals(calculatedCrcHex)) {
            System.out.println("CRC校验失败！ 接收到的CRC=" + receivedCrcHex + "，计算出的CRC=" + calculatedCrcHex);
            return null;
        }

        System.out.println("CRC校验通过！");
        return hex.substring(4 + 1, totalLen - 8);
    }


    /**
     * 十六进制字符串转 byte 数组
     */
    private static byte[] hexStringToByteArray(String s) {
        s = s.replaceAll("\\s", ""); // 去除空格
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 把十六进制字符串转换成字节数组
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        // 清理可能存在的空格等字符
        String cleaned = hex.replaceAll("[^0-9A-Fa-f]", "");
        if ((cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数: " + cleaned);
        }
        byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(cleaned.charAt(i * 2), 16);
            int low = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    // 模拟前端网站常见的奇数位十六进制解析漏洞：最后一位单独解析为字节
    public static byte[] hexToBytesWithJSPitfall(String hex) {
        int len = hex.length();
        // 如果是奇数，分配的字节数组长度是 len/2 + 1
        byte[] bytes = new byte[(len + 1) / 2];

        for (int i = 0; i < bytes.length; i++) {
            int start = i * 2;
            int end = Math.min(start + 2, len);
            // 比如最后一次循环 start=40, end=41, substring(40,41) 得到 "E"
            // Integer.parseInt("E", 16) 会得到 14，即 0x0E
            bytes[i] = (byte) Integer.parseInt(hex.substring(start, end), 16);
        }
        return bytes;
    }

    /**
     * 对字节数组计算 CRC32，返回固定 8 位大写十六进制字符串
     */
    public static String crc32Hex(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return String.format("%08X", crc32.getValue());
    }

    /**
     * 对十六进制字符串计算 CRC-32，返回 8 位大写十六进制字符串。
     * <p>
     * 将清洗后的 hex 按 UTF-8 文本逐字节参与 CRC（与在线 CRC 工具、Guava {@code hashString} 一致），
     * </p>
     *
     * @param hex 十六进制字符串（可含空格）
     * @return CRC-32 结果，如 {@code 2AA15BF8}
     */
    public static String crc32HexFromHex(String hex) {
        String cleaned = hex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("hex 不能为空");
        }
        return String.format("%08X", CRC32Verify.calculateCrc32(cleaned.getBytes(StandardCharsets.UTF_8)));
    }

    public static List<ComidConfig> getConfigList() {
        // 根据配置构建配置列表
        List<ComidConfig> configList = new ArrayList<>();

        // comid 4099
        List<SignalConfig> sigs1 = new ArrayList<>();
        sigs1.add(new SignalConfig("CCUlifeSignal", 0, 0, 8, "UNSIGNED8"));
        sigs1.add(new SignalConfig("Loco_Num", 2, 0, 16, "UNSIGNED16"));
        configList.add(new ComidConfig(4099, sigs1));

        // comid 4128
        List<SignalConfig> sigs2 = new ArrayList<>();
        sigs2.add(new SignalConfig("CCU_HB", 0, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Year", 1, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Month", 2, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Day", 3, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Hour", 4, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Minute", 5, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("Second", 6, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("ThrPos", 7, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("OpMode", 8, 0, 8, "UNSIGNED8"));
        sigs2.add(new SignalConfig("LocoPwrMode", 9, 0, 8, "UNSIGNED8"));
        configList.add(new ComidConfig(4128, sigs2));
        return configList;
    }

    // --- 测试入口 ---
    public static void main(String[] args) {

        String hex = "AABB16A69B707019FE282AAB285A7C79B6FC71EAE2122355D";

        /*
             数据包：AABB16A59EC40019FE282AAB285A7C79B6FC71EAE2AA15BF8
             帧头：AABB
             心跳：1
             数据：6A59EC40019FE282AAB285A7C79B6FC71EAE
             CRC：2AA15BF8
             计算“数据”的CRC结果：1831743063
        */
        String fullPacket = hex;
        System.out.println("模拟的完整数据包: " + fullPacket);


        // ==========================================
        // 1. CRC检验，不合格就停止
        // ==========================================
        String businessHex = validateAndExtractBusinessHex(fullPacket);
        if (businessHex == null) {
            System.out.println("数据包校验未通过，流程终止！");
//            return; // 不合格直接停止
        }
        System.out.println("businessHex: " + businessHex);


        // 执行解析
//        Map<String, Object> result = parsePacket(packet, configList);

        // 打印结果
//        for (Map.Entry<String, Object> entry : result.entrySet()) {
//            System.out.println(entry.getKey() + ": " + entry.getValue());
//        }

        // 执行解析
        Map<String, Object> result2 = parsePacket2(businessHex, getConfigList());

        // 打印结果
        for (Map.Entry<String, Object> entry : result2.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        // 将纯净的业务数据丢给解析函数
        Map<String, Object> result3 = parsePacket2(businessHex, getConfigList());
        for (Map.Entry<String, Object> entry : result3.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}
