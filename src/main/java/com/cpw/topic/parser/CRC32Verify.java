package com.cpw.topic.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class CRC32Verify {

    private static final Logger log = LoggerFactory.getLogger(CRC32Verify.class);

    // CRC32 多项式
    private static final int POLY = 0xEDB88320;
    // 帧头
    public static final String FRAME_HEADER = "AABB";


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
        int crc32Value = calculateCrc32(cleaned.getBytes(StandardCharsets.UTF_8));
        // 转十六进制
        return String.format("%08X", crc32Value);
    }

    /*
        C++ 版本crc校验
        uint32_t crc32(const uint8_t *data, size_t length) {
            uint32_t crc = 0xFFFFFFFF;
            for (size_t i = 0; i < length; i++) {
                crc ^= data[i];
                for (int j = 0; j < 8; j++) {
                    if (crc & 1) {
                        crc = (crc >> 1) ^ POLY;
                    } else {
                        crc >>= 1;
                    }
                }
            }
            return crc ^ 0xFFFFFFFF;
        }
    */

    /**
     * CRC32 校验算法 (Java版复刻C++)
     */
    public static int calculateCrc32(byte[] data) {
        int crc = 0xFFFFFFFF; // 初始值
        for (byte b : data) {
            // Java的byte是有符号的，转成int时需要 & 0xFF 保证无符号处理
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    // 必须使用无符号右移 >>>
                    crc = (crc >>> 1) ^ POLY;
                } else {
                    crc >>>= 1;
                }
            }
        }
//        return crc ^ 0xFFFFFFFF; // 最终取反
        return ~crc; // 最终取反
    }
}
