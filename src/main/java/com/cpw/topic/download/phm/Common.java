package com.cpw.topic.download.phm;

import cn.hutool.core.util.StrUtil;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Common {

    private static final Logger log = LoggerFactory.getLogger(Common.class);

    public static final int[] bitMask = new int[32];

    public final static String[] SpELReservedWordList = {"#EQ", "#GE", "#GT", "#LE", "#LT"};
    public final static String[] SpELReplaceWordList = {"#_EQ", "#_GE", "#_GT", "#_LE", "#_LT"};

    static {
        bitMask[0] = 1; // 2^0
        for (int i = 1; i < bitMask.length; i++) {
            bitMask[i] = bitMask[i - 1] * 2;
        }
    }

    /**
     * 将 'A'、'B' ... 'AA'、'AB' ... 'EF' 转为 0 开始的序号。
     *
     * @param position
     * @return
     */
    public static int excelColNameToNumber(String position) {
        if (position.equalsIgnoreCase("_EQ"))    // 回避SpEL关键字的补丁
            position = "EQ";

        position = position.toUpperCase(Locale.ROOT);
        int index = 0;
        int[] digits = new int[position.length()];
        for (int i = 0; i < position.length(); i++) {
            digits[i] = position.charAt(position.length() - i - 1) - 'A' + 1;
        }
        int power = 1;
        for (int i = 0; i < digits.length; i++) {
            index += (digits[i]) * power;
            power *= 26;
        }

        return --index;
    }

    /**
     * 将数字（从1开始）转为Excel列标
     *
     * @param number
     * @return
     */
    public static String numberToExcelColName(int number) {
        StringBuilder columnLabel = new StringBuilder();

        while (number > 0) {
            int remainder = (number - 1) % 26;
            char digitValue = (char) ('A' + remainder);
            columnLabel.insert(0, digitValue);
            number = (number - 1) / 26;
        }

        return columnLabel.toString();
    }

    /**
     * 给定一个整数，从中提取出从第几位开始的几位数。
     *
     * @param num  原始数据
     * @param from 从第几位开始（0开始计数）
     * @param len  取几位数
     * @return
     */
    public static Integer getBitsValue(Integer num, Integer from, Integer len) {
        Integer mask = 0;
        num = num >>> from;
        for (int i = 0; i < len; i++) {
            mask = mask | bitMask[i];
        }
        return num & mask;
    }

    public static String zlibUnCompress(String compressedString) {
        int compressStringLen = compressedString.length();
        if (compressStringLen % 2 != 0) {
            return null;
        }

        if (compressedString.startsWith("00")) {
            // 未压缩，直接返回从第2个字节开始的数据。
            return compressedString.substring(2, compressStringLen);
        }

        // 去掉第一个字节的压缩标志
        compressedString = compressedString.substring(2, compressStringLen);
        compressStringLen = compressedString.length();

        byte[] compressedBuffer = new byte[compressStringLen / 2];
        int index = 0;
        for (int i = 0; i < compressStringLen; i += 2) {
            compressedBuffer[index++] = Integer
                    .valueOf(compressedString.substring(i, i + 2), 16)
                    .byteValue();
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(compressedBuffer);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             CompressorInputStream compressorInputStream = new CompressorStreamFactory()
                     .createCompressorInputStream(CompressorStreamFactory.DEFLATE, inputStream)) {
            IOUtils.copy(compressorInputStream, outputStream);
            byte[] outputByteArray = outputStream.toByteArray();
            StringBuilder hexString = new StringBuilder();
            for (byte bit : outputByteArray) {
                String hex = Integer.toHexString(bit & 0xFF);
                if (hex.length() == 1)
                    hexString.append(0);
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析以分号或换行(\n)分隔的枚举定义字符串。其中枚举键值对以冒号分隔。
     *
     * @param enumString 枚举定义字符串
     * @return 枚举定义Map
     */
    public static Map<Integer, String> parseEnumDefine(String enumString) {
        if (enumString == null || enumString.isEmpty()) {
            return null;
        }


        Map<Integer, String> enumMap = new HashMap<>();
        String[] enumPairs = enumString.split("[;；\n]");
        for (String pair : enumPairs) {
            String[] enumDef = pair.split("[:：]");
            if (StrUtil.isBlank(pair)) continue;
            if (enumDef.length == 2) {
                String key = enumDef[0].trim();
                String value = enumDef[1].trim();
                if (key.toLowerCase().startsWith("0b")) {  // 二进制
                    enumMap.put(Integer.parseInt(key.substring(2), 2), value);
                } else if (key.toLowerCase().endsWith("h")) {   // 十六进制
                    enumMap.put(Integer.parseInt(key.substring(0, key.length() - 1), 16), value);
                } else if (key.toLowerCase().startsWith("0x")) {   // 十六进制
                    enumMap.put(Integer.decode(key), value);
                } else if (key.toLowerCase().startsWith("bit")) {
                    enumMap.put(Integer.parseInt(key.substring(3)), value);
                } else {
                    enumMap.put(Integer.parseInt(key), value);
                }
            } else {
                throw new RuntimeException("错误的枚举定义：" + enumString);
            }
        }
        return enumMap;
    }

    public static int parseBitLen(String bitLenStr) {
        Pattern pattern = Pattern.compile("[0-9]*$");
        Matcher matcher = pattern.matcher(bitLenStr);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        throw new RuntimeException("无法解析位长度：" + bitLenStr);
    }
}
