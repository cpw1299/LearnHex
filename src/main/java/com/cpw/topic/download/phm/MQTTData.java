package com.cpw.topic.download.phm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT数据包完整结构
 */
@Data
public class MQTTData {

    // ========== 协议头部字段 ==========
    private String header;            // 帧头：0xCCDD (2字节)
    private Long startTimeSec;        // 采集起始时间的秒数 (4字节)
    private Long startTimeMsec;       // 采集起始时间的毫秒数 (2字节)
    private Integer packetLen;        // 长度（字节数） (4字节)
    private Integer compressFlag;     // 是否压缩标志位 (1字节)

    // ========== 解压后的数据字段 ==========
    private Integer diggingsId;       // 矿区编号 (2字节)
    private Integer reserved1;       // 预留字段1 (1字节)
    private Integer reserved2;       // 预留字段2 (2字节)
    private Integer deviceType;       // 设备型号 (2字节)
    private Integer deviceId;         // 设备编号 (2字节)
    private Integer sampleDuration;   // 采样时长，单位：ms (2字节)
    private Integer counter;          // 所有车对地数据的唯一序号 (2字节)

    // ========== 原始数据和状态 ==========
    private String rawData;           // 原始完整十六进制数据
    private String decompressedData;  // 解压后的数据（十六进制字符串）
    private Boolean crcValid;         // CRC校验结果
    private String crcValue;          // CRC校验值 (1字节)

    // ========== payload解析结果 ==========
    private String payloadHex;        // payload原始十六进制数据
    private List<MqttDataPayload> canPackets; // CAN包列表

    // ========== 辅助字段 ==========
    private Integer totalPackets;     // 总包数
    private Boolean parseSuccess;     // 解析是否成功
    private String parseError;        // 解析错误信息
    private String subTableName;        // 指标表名

    public String getSubTableName() {
        return "can_base_data_" + deviceType + "_" + diggingsId + "_" + deviceId;
    }

    private Long tsMillis; // 毫秒数（0-999）

    public long getTsMillis() {
        if (startTimeSec != null) {
            this.tsMillis = startTimeSec * 1000L;  // 秒转毫秒
            if (startTimeMsec != null) {
                this.tsMillis += startTimeMsec;  // 加上毫秒部分
            }
        }
        return this.tsMillis;
    }

    /**
     * 获取格式化后的payload数据
     */
    public String getFormattedPayload() {
        if (payloadHex == null || payloadHex.isEmpty()) {
            return "[无payload数据]";
        }

        StringBuilder formatted = new StringBuilder();
        formatted.append("Payload数据（长度: ").append(payloadHex.length() / 2).append("字节）:\n");

        int lineLength = 64; // 每行32个字节（64个字符）
        for (int i = 0; i < payloadHex.length(); i += lineLength) {
            int end = Math.min(i + lineLength, payloadHex.length());
            String line = payloadHex.substring(i, end);
            formatted.append(String.format("  %04d: %s\n", i / lineLength, line));
        }

        return formatted.toString();
    }

    /**
     * 构造函数
     */
    public MQTTData() {
        this.canPackets = new ArrayList<>();
        this.parseSuccess = true;
    }

    /**
     * 添加CAN包记录
     */
    public void addCANPacket(MqttDataPayload packet) {
        this.canPackets.add(packet);
    }

    /**
     * 获取CAN包数量
     */
    public int getCANPacketCount() {
        return canPackets != null ? canPackets.size() : 0;
    }

    /**
     * 获取完整时间戳（秒 + 毫秒）
     */
    public double getFullTimestamp() {
        if (startTimeSec != null && startTimeMsec != null) {
            return startTimeSec + startTimeMsec / 1000.0;
        }
        return 0;
    }

    /**
     * 获取时间戳字符串
     */
    public String getTimestampString() {
        return String.format("%d.%03d", startTimeSec, startTimeMsec);
    }

    /**
     * 获取设备信息字符串
     */
    public String getDeviceInfo() {
        return String.format("设备型号: %d, 设备编号: %d", deviceType, deviceId);
    }

    /**
     * 打印数据包摘要信息
     */
    public void printSummary() {
        System.out.println("========== MQTT数据包摘要 ==========");
        System.out.println("帧头: " + header + " (0x" + header + ")");
        System.out.println("时间戳: " + getTimestampString() + "s");
        System.out.println("包长度: " + packetLen + " 字节");
        System.out.println("压缩标志: " + compressFlag + (compressFlag == 1 ? " (压缩)" : " (未压缩)"));
        System.out.println("设备信息: " + getDeviceInfo());
        System.out.println("采样时长: " + sampleDuration + "ms");
        System.out.println("计数器: " + counter);
        System.out.println("CRC校验: " + (crcValid ? "通过" : "失败"));
        System.out.println("CAN包数量: " + getCANPacketCount());
        System.out.println("解析状态: " + (parseSuccess ? "成功" : "失败"));
        if (parseError != null) {
            System.out.println("错误信息: " + parseError);
        }
        System.out.println("====================================");
    }
}