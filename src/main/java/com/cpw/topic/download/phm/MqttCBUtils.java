package com.cpw.topic.download.phm;

import cn.hutool.core.util.HexUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MqttCBUtils {
    /**
     * 解析MQTT数据包 - 返回完整的数据包对象
     */
    public static MQTTData parseMQTTPacket(String encodeHexStr) {
        MQTTData packet = new MQTTData();
        packet.setRawData(encodeHexStr);
        //  CRC校验
        boolean checkedCRC = checkCRC(encodeHexStr);
        packet.setCrcValid(checkedCRC);
        if (!checkedCRC) {
            System.out.println("CRC校验失败！");
            return null;
        }

        int pointerPos = 0; // 原始数据指针（字符位置）

        // 1. 解析头部字段
        // header (2字节 = 4字符)
        if (pointerPos + 4 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取header");
        }
        String header = encodeHexStr.substring(pointerPos, pointerPos + 4);
        packet.setHeader(header);
        pointerPos += 4;

        // 2. start time(sec) (4字节 = 8字符)
        if (pointerPos + 8 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取start time(sec)");
        }
        String startSecHex = encodeHexStr.substring(pointerPos, pointerPos + 8);
        Long startSec = Long.valueOf(startSecHex, 16);
        packet.setStartTimeSec(startSec);
        pointerPos += 8;

        // 3.start time(msec) (2字节 = 4字符)
        if (pointerPos + 4 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取start time(msec)");
        }
        String startMsecHex = encodeHexStr.substring(pointerPos, pointerPos + 4);
        Long startMsec = Long.valueOf(startMsecHex, 16);
        packet.setStartTimeMsec(startMsec);
        pointerPos += 4;

        // 4.packet len (4字节 = 8字符)
        if (pointerPos + 8 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取packet len");
        }
        String packetLenHex = encodeHexStr.substring(pointerPos, pointerPos + 8);
        Integer packetLen = Integer.valueOf(packetLenHex, 16);
        packet.setPacketLen(packetLen);
        pointerPos += 8;

        // 5.compress flag (1字节 = 2字符)
        if (pointerPos + 2 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取compress flag");
        }
        String compressFlagHex = encodeHexStr.substring(pointerPos, pointerPos + 2);
        Integer compressFlag = Integer.valueOf(compressFlagHex, 16);
        packet.setCompressFlag(compressFlag);
        pointerPos += 2;

        //解压缩
        String decompressedDataHex; //截取deviceType到payload数据
        if (compressFlag != 0) {//压缩区域 需要解压后截取
            //pointerPos  目前坐标是在device Type前
            if (encodeHexStr.length() - 2 <= pointerPos) {
                throw new IllegalArgumentException("数据不足，deviceType等无数据");
            }
            String dataForDecompress = encodeHexStr.substring(pointerPos, encodeHexStr.length() - 2);
            decompressedDataHex = Common.zlibUnCompress(compressFlagHex + dataForDecompress);

            if (decompressedDataHex == null) {
                throw new IllegalArgumentException("解压失败！");
                // decompressedDataHex = compressedDataHex;
            }
        } else {//未压缩
            int compressedDataHexChars = packetLen * 2;
            if (pointerPos + compressedDataHexChars > encodeHexStr.length()) {
                throw new IllegalArgumentException("数据不足，无法读取压缩区间");
            }

            decompressedDataHex = encodeHexStr.substring(pointerPos, pointerPos + compressedDataHexChars);
        }

        //解压后的数据 ，从deviceType到payload
        packet.setDecompressedData(decompressedDataHex);

        // ========== 从解压后的数据解析字段 ==========
        int decompressedPos = 0;
        int decompressedDataLen = decompressedDataHex.length();

        //6. device Type (2字节 = 4字符)
        if (decompressedPos + 4 > decompressedDataLen) {
            throw new IllegalArgumentException("解压后数据不足，无法读取device Type");
        }
        String deviceTypeHex = decompressedDataHex.substring(decompressedPos, decompressedPos + 4);
        Integer deviceType = Integer.valueOf(deviceTypeHex, 16);
        packet.setDeviceType(deviceType);
        decompressedPos += 4;

        //7. device ID (2字节 = 4字符)
        if (decompressedPos + 4 > decompressedDataLen) {
            throw new IllegalArgumentException("解压后数据不足，无法读取device ID");
        }
        String deviceIdHex = decompressedDataHex.substring(decompressedPos, decompressedPos + 4);
        Integer deviceId = Integer.valueOf(deviceIdHex, 16);
        packet.setDeviceId(deviceId);
        decompressedPos += 4;

        //默认老协议
        boolean isNewProtocol = true;

        // 检查跳过8字节（16字符）后是否是aabb
        if (decompressedPos + 16 <= decompressedDataLen) {
            String afterSkipHeader = decompressedDataHex.substring(decompressedPos + 16);
            if (afterSkipHeader.length() >= 4 && "aabb".equalsIgnoreCase(afterSkipHeader.substring(0, 4))) {
                isNewProtocol = false;

            }
        }

        //---------新增字段-------------------

        if (isNewProtocol) {
            //老协议不解析
            return null;
        }
        //新增1、device area 设备所在区域编号
        String deviceArea = decompressedDataHex.substring(decompressedPos, decompressedPos + 2);
        Integer diggingsId = Integer.valueOf(deviceArea, 16);
        packet.setDiggingsId(diggingsId);
        decompressedPos += 2;
        System.out.println("新协议解析。。。。 矿区编码：" + diggingsId + " 车辆：" + deviceId);
        //新增2、reserved1 预留字段1
        String reserved1Str = decompressedDataHex.substring(decompressedPos, decompressedPos + 2);
        Integer reserved1 = Integer.valueOf(reserved1Str, 16);
        packet.setReserved1(reserved1);
        decompressedPos += 2;

        //新增3、reserved2 预留字段1
        String reserved2Str = decompressedDataHex.substring(decompressedPos, decompressedPos + 4);
        Integer reserved2 = Integer.valueOf(reserved2Str, 16);
        packet.setReserved2(reserved2);
        decompressedPos += 4;
        //---------新增字段结束-------------------

        //8。 sample duration (2字节 = 4字符)
        if (decompressedPos + 4 > decompressedDataLen) {
            throw new IllegalArgumentException("解压后数据不足，无法读取sample duration");
        }
        String sampleDurationHex = decompressedDataHex.substring(decompressedPos, decompressedPos + 4);
        Integer sampleDuration = Integer.valueOf(sampleDurationHex, 16);
        packet.setSampleDuration(sampleDuration);
        decompressedPos += 4;

        //9. counter (2字节 = 4字符)
        if (decompressedPos + 4 > decompressedDataLen) {
            throw new IllegalArgumentException("解压后数据不足，无法读取counter");
        }
        String counterHex = decompressedDataHex.substring(decompressedPos, decompressedPos + 4);
        Integer counter = Integer.valueOf(counterHex, 16);
        packet.setCounter(counter);
        decompressedPos += 4;

        //10. payload (剩余部分)
        int payloadHexChars = decompressedDataLen - decompressedPos;
        if (payloadHexChars <= 0) {
            throw new IllegalArgumentException("解压后数据没有payload");
        }

        String payloadHex = decompressedDataHex.substring(decompressedPos);
        packet.setPayloadHex(payloadHex);

        // 解析payload中的CAN数据包
        List<MqttDataPayload> canPackets = parseCANPayloadOptimized(payloadHex, packet);

        packet.setCanPackets(canPackets);

        // crc校验和 (1字节 = 2字符)
        if (pointerPos + 2 > encodeHexStr.length()) {
            throw new IllegalArgumentException("数据不足，无法读取crc校验和");
        }

        String crcHex = encodeHexStr.substring(pointerPos, pointerPos + 2);
        packet.setCrcValue(crcHex);

        // 设置总包数
        packet.setTotalPackets(canPackets.size());

        return packet;
    }

    /**
     * 统计每个CAN ID在payload中的总包数
     */
    private static Map<String, Integer> countCanIdPackets(String payloadHex) {
        Map<String, Integer> countMap = new HashMap<>();
        int pointer = 0;
        int length = payloadHex.length();

        while (pointer < length) {
            try {
                // 跳过header (4字符)
                if (pointer + 4 > length) break;
                pointer += 4;

                // 读取CAN ID (8字符)
                if (pointer + 8 > length) break;
                String canIdHex = payloadHex.substring(pointer, pointer + 8).toLowerCase();
                pointer += 8;

                // 统计
                countMap.put(canIdHex, countMap.getOrDefault(canIdHex, 0) + 1);

                // 读取type (2字符)
                if (pointer + 2 > length) break;
                int type = Integer.parseInt(payloadHex.substring(pointer, pointer + 2), 16);
                pointer += 2;

                if (type == 4) {
                    continue;
                }

                // 读取size (2字符)
                if (pointer + 2 > length) break;
                int size = Integer.parseInt(payloadHex.substring(pointer, pointer + 2), 16);
                pointer += 2;

                // 跳过data
                pointer += size * 2;

            } catch (Exception e) {
                break;
            }
        }

        return countMap;
    }

    /**
     * 计算CAN包的时间戳
     * 公式：第m包的时间 = baseTimestamp + m * sampleDuration / totalCount
     * 其中m从0开始计数（当前包在当前CAN ID中的出现顺序）
     */
    private static void calculateTimestamp(MqttDataPayload record, String canIdHex,
                                           Map<String, Integer> counter,
                                           Map<String, Integer> totalCountMap,
                                           Long baseTimestamp, Integer sampleDuration) {
        // 获取当前CAN ID已出现的次数（作为包序号）
        int currentIndex = counter.getOrDefault(canIdHex, 0);
        counter.put(canIdHex, currentIndex + 1);

        // 获取当前CAN ID的总包数
        Integer totalCount = totalCountMap.getOrDefault(canIdHex, 1);

        // 计算时间戳
        if (sampleDuration != null && sampleDuration > 0) {
            // 第m包的时间偏移量 = m * sample_duration / totalCount
            long offsetMillis = (long) currentIndex * sampleDuration / totalCount;
            record.setTsMillis(baseTimestamp + offsetMillis);
        } else {
            // 如果没有采样时长，使用基础时间戳
            record.setTsMillis(baseTimestamp);
        }
    }

    /**
     * 优化后的CAN payload解析方法
     */
    private static List<MqttDataPayload> parseCANPayloadOptimized(String payloadHex, MQTTData packet) {
        List<MqttDataPayload> canPackets = new ArrayList<>();
        Map<Integer, char[]> fullDataMap = new HashMap<>();

        if (payloadHex == null || payloadHex.isEmpty()) {
            System.out.println("payload为空");
            return canPackets;
        }

        //采样时长
        Integer sampleDuration = packet.getSampleDuration();
        Long baseTimestamp = packet.getTsMillis();//初始时间

        int payloadHexChars = payloadHex.length();
        int payloadPointer = 0;
        int canPacketNum = 1;

        // 第一步：先统计每个CAN ID的总包数
        Map<String, Integer> canIdTotalCount = countCanIdPackets(payloadHex);
        // key: canIdHex, value: 该CAN ID出现的次数统计
        Map<String, Integer> canIdCounter = new HashMap<>();

        while (payloadPointer < payloadHexChars) {
            MqttDataPayload record = new MqttDataPayload();
            record.setPacketNumber(canPacketNum);
            record.setParseStatus("成功");

            try {
                // 记录当前包的原始数据开始位置
                int packetStartPos = payloadPointer;

                // 1. header (2字节 = 4字符) - 必须为AABB
                if (payloadPointer + 4 > payloadHexChars) {
                    throw new IllegalArgumentException("payload数据不足，无法读取CAN包头");
                }

                String canHeader = payloadHex.substring(payloadPointer, payloadPointer + 4);
                record.setHeader(canHeader);

                if (!"aabb".equalsIgnoreCase(canHeader)) {
                    throw new IllegalArgumentException("CAN包头错误，期望aabb，实际: " + canHeader);
                }
                payloadPointer += 4;

                // 2. CanID (4字节 = 8字符)
                if (payloadPointer + 8 > payloadHexChars) {
                    throw new IllegalArgumentException("payload数据不足，无法读取CanID");
                }

                String canIdHex = payloadHex.substring(payloadPointer, payloadPointer + 8);
                Integer canId = Integer.valueOf(canIdHex, 16);//转换成十进制
                record.setCanId(canId);
                record.setCanIdHex(canIdHex.toLowerCase());
                payloadPointer += 8;

                // 3. type (1字节 = 2字符)
                if (payloadPointer + 2 > payloadHexChars) {
                    throw new IllegalArgumentException("payload数据不足，无法读取type");
                }

                String typeHex = payloadHex.substring(payloadPointer, payloadPointer + 2);
                Integer type = Integer.valueOf(typeHex, 16);
                record.setType(type);
                record.setTypeDesc(getTypeDescription(type));
                payloadPointer += 2;

                // 4. 计算时间戳
                calculateTimestamp(record, canIdHex, canIdCounter, canIdTotalCount,
                        baseTimestamp, sampleDuration);

                if (type == 4) {
                    // type=4: 与全量数据一致，无data区
                    char[] fullData = fullDataMap.get(canId);
                    if (fullData != null) {
                        String dataStr = new String(fullData);
                        record.setDataHex(dataStr);
                    }
                    canPackets.add(record);
                    canPacketNum++;
                    continue;
                }

                // type=1,2,3: 需要读取size和data
                if (payloadPointer + 2 > payloadHexChars) {
                    throw new IllegalArgumentException("payload数据不足，无法读取size");
                }

                // 4. size (1字节 = 2字符)
                String sizeHex = payloadHex.substring(payloadPointer, payloadPointer + 2);
                Integer size = Integer.valueOf(sizeHex, 16);
                record.setSizeHex(sizeHex);
                record.setSize(size);
                payloadPointer += 2;

                // 设置偏移字节数
                if (type == 2) {
                    record.setOffsetBytes(1);
                } else if (type == 3) {
                    record.setOffsetBytes(2);
                }

                // 5. data (size字节)
                int dataHexChars = size * 2;
                if (payloadPointer + dataHexChars > payloadHexChars) {
                    throw new IllegalArgumentException("payload数据不足，无法读取data");
                }

                String dataHex = payloadHex.substring(payloadPointer, payloadPointer + dataHexChars);
                record.setDataHex(dataHex);
                payloadPointer += dataHexChars;

                // 记录整个CAN包的原始数据
                int packetEndPos = payloadPointer;
                record.setRawDataHex(payloadHex.substring(packetStartPos, packetEndPos));

                // 根据type进行不同的处理
                if (type == 1) {
                    processFullByteData(canId, dataHex, fullDataMap);
                    record.setDataHex(dataHex);
                } else if (type == 2 || type == 3) {
                    processDiffData(canId, type, dataHex, fullDataMap, record);
                }

                canPackets.add(record);
                canPacketNum++;

            } catch (Exception e) {
                e.printStackTrace();
                record.setParseStatus("失败: " + e.getMessage());
                canPackets.add(record);
                System.out.println("解析CAN包出错: " + e.getMessage());
                break;
            }
        }

        return canPackets;
    }

    /**
     * 处理全字节数据 (type=1)
     */
    private static void processFullByteData(Integer canId, String dataHex,
                                            Map<Integer, char[]> fullDataMap) {
        char[] dataChars = dataHex.toCharArray();
        fullDataMap.put(canId, dataChars.clone());
    }

    /**
     * 处理差分数据 (type=2或3)
     */
    private static void processDiffData(Integer canId, Integer type, String dataHex,
                                        Map<Integer, char[]> fullDataMap,
                                        MqttDataPayload record) {
        char[] currentData = fullDataMap.get(canId);
        if (currentData == null) {
            record.setDataHex("[无基准数据]");
            return;
        }

        char[] modifiedData = currentData.clone();
        int offsetBytes = (type == 2) ? 1 : 2;
        int dataHexChars = dataHex.length();
        int dataPointer = 0;

        while (dataPointer < dataHexChars) {
            if (dataPointer + offsetBytes * 2 > dataHexChars) break;

            String offsetHex = dataHex.substring(dataPointer, dataPointer + offsetBytes * 2);
            int offset = Integer.valueOf(offsetHex, 16);
            dataPointer += offsetBytes * 2;

            if (dataPointer + 2 > dataHexChars) break;

            String valueHex = dataHex.substring(dataPointer, dataPointer + 2);
            dataPointer += 2;

            int charOffset = offset * 2;
            if (charOffset < modifiedData.length && charOffset + 1 < modifiedData.length) {
                modifiedData[charOffset] = valueHex.charAt(0);
                modifiedData[charOffset + 1] = valueHex.charAt(1);
            }
        }

        String finalData = new String(modifiedData);
        record.setDataHex(finalData);
    }



    /**
     * 十六进制字符串转字节数组
     */
    private static byte[] hexStringToByteArray(String hexStr) {
        if (hexStr == null || hexStr.length() % 2 != 0) {
            throw new IllegalArgumentException("无效的十六进制字符串: " + hexStr);
        }

        int len = hexStr.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            // 处理每个十六进制字符对
            int high = Character.digit(hexStr.charAt(i), 16);
            int low = Character.digit(hexStr.charAt(i + 1), 16);

            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("无效的十六进制字符: " + hexStr.charAt(i) + hexStr.charAt(i + 1));
            }

            data[i / 2] = (byte) ((high << 4) + low);
        }

        return data;
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 将字节转换为两位十六进制，不足两位补0
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toLowerCase();
    }

    /**
     * 获取type描述
     */
    private static String getTypeDescription(int type) {
        switch (type) {
            case 1:
                return "全字节";
            case 2:
                return "差异数据(offset=1字节)";
            case 3:
                return "差异数据(offset=2字节)";
            case 4:
                return "与全量数据一致";
            default:
                return "未知类型";
        }
    }


    /**
     * CRC8校验算法（根据协议文档）
     *
     * @param dataHex 完整的十六进制字符串（包含CRC）
     * @return true表示CRC校验通过，false表示失败
     */
    private static boolean checkCRC(String dataHex) {
        if (dataHex == null || dataHex.length() < 2) {
            return false;
        }

        // 1. 分离数据和CRC
        String dataPart = dataHex.substring(0, dataHex.length() - 2);  // 去掉最后2个字符（CRC）
        String receivedCRCHex = dataHex.substring(dataHex.length() - 2);  // 最后2个字符是CRC

        // 2. 将数据部分转换为字节数组
        byte[] dataBytes = HexUtil.decodeHex(dataPart);
        // 3. 计算CRC
        int calculatedCRC = calcCRC8(dataBytes, dataBytes.length);

        // 4. 获取接收到的CRC
        int receivedCRC = Integer.parseInt(receivedCRCHex, 16);

        // 5. 比较
        return calculatedCRC == receivedCRC;
    }

    /**
     * CRC8计算算法
     */
    private static int calcCRC8(byte[] data, int length) {
        int crc = 0xFFFF;

        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);

            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        return crc & 0xFF;
    }


}