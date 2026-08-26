package com.cpw.topic.download.phm;

import lombok.Data;

/**
 * payload解包数据
 */
@Data
public class MqttDataPayload {

    private int packetNumber;    // 包序号
    private int canId;           // msgId
    private String canIdHex;     // 新增：十六进制CAN ID
    private int type;            // 数据类型 1：全量数据  2.差异数据 4。与全量数据一致
    private String typeDesc;     // 类型描述
    private String sizeHex;      // size原始十六进制值
    private Integer size;        // size十进制值
    private String dataHex;      // 数据原始十六进制字符串

    // 新增字段
    private String header;       // CAN包头 (0xAABB)
    private Integer offsetBytes; // 偏移字节数 (type=2时为1，type=3时为2)
    private String rawDataHex;   // 整个CAN包的原始十六进制数据
    private String parseStatus;  // 解析状态
    private Long tsMillis; // 计算后时间

    /**
     * 获取CanID的十六进制表示
     */
    public String getCanIdHex() {
        return String.format("%08X", canId);
    }


}