package com.cpw.topic.strategy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cpw.entity.VehicleBeidouRelationEntity;
import com.cpw.service.IVehicleBeidouRelationService;
import com.cpw.topic.MqttBusinessStrategy;
import com.cpw.topic.parser.CRC32Verify;
import com.cpw.topic.parser.DynamicPacketParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 北斗设备MQTT业务策略
 * <p>
 * 处理北斗设备上报的MQTT消息，解析后分发到对应处理器
 * </p>
 *
 * @author up
 */
@Slf4j
@Component
public class BeidoDataStrategy implements MqttBusinessStrategy {

    @Resource
    private IVehicleBeidouRelationService vehicleBeidouRelationService;

    private final static Long DEFAULT_VEHICLE_ID = 1L;

    /**
     * 返回当前策略监听的Topic名称
     *
     * @return Topic名称
     */
    @Override
    public String getTopicName() {
        return "beidou";
    }

    /**
     * 处理MQTT消息
     * <p>
     * JSON格式：
     * "beidou_data" 代表获取北斗的信息
     * "latitude" 代表地面北斗维度
     * "longitude" 代表地面北斗精度
     * "utc_time" 代表时间
     * "utc_date" 代表日期
     * 字段名     类型      说明
     * latitude    float   纬度，单位为度（°），正值为北纬，负值为南纬
     * longitude   float   经度，单位为度（°），正值为东经，负值为西纬
     * utc_time    string  UTC时间，格式为 HHMMSS SS（时分秒和秒的小数部分），示例 "082840 00" 表示 08:28:40.00
     * utc_date    string  UTC日期，格式为 YYMMDD，示例 "190526" 表示 2019年5月26日
     * valid   boolean 定位数据有效性，true 表示有效，false 表示无效
     * 实例：
     * {
     *     "beidou_data":2041e86a2041e86a2041e86a2041e86a2041e86a,
     *     "latitude": 36.2881,
     *     "longitude": 120.4,
     *     "utc_time": "082840 00",
     *     "utc_date": "190526",
     *     "valid": true
     * }
     * 数据解析：
     * 1. 前4个字节是UTC时间戳：6a2a8a4e -> 1781172722
     * 2. 时间戳后面的1个字节是代表配置版本信息 0x01
     * 3. 后面跟着的就是每个signal的值（如果最后不足1个字节会补0），根据配置短报文发过来的配置按顺序来的，可以参考下面的解析出来的值：
     * JSON Data:
     * {
     *     "CCUlifeSignal": ["0"],
     *     "Loco_Num": ["52719"],
     *     "CCU_HB": ["52"],
     *     "Year": ["86"],
     *     "Month": ["120"],
     *     "Day":  ["154"],
     *     "Hour": ["170"],
     *     "Minute": ["52"],
     *     "Second": ["86"],
     *     "ThrPos": ["120"],
     *     "OpMode": ["154"],
     *     "LocoPwrMode": ["170"]
     * }
     * 根据上述 JSON Data 示例补充额外说明，下发的配置短报文如下，
     * {
     *     "device_id": 1,
     *     "device_type": 901,
     *     "device_area": 1,
     *     "request_msg_id": 1779791247075,
     *     "command": "BD_config",
     *     "data": {
     *         "bd_signals": [{
     *             "comid": 4099,
     *             "data": {
     *                 "signal": ["CCUlifeSignal", "Loco_Num"],
     *                 "byteoft": [0, 2],
     *                 "bitoft": [0, 0],
     *                 "len": [8, 16],
     *                 "type": ["UNSIGNED8", "UNSIGNED16"]
     *             }
     *         }, {
     *             "comid": 4128,
     *             "data": {
     *                 "signal": ["CCU_HB", "Year", "Month", "Day", "Hour", "Minute", "Second", "ThrPos", "OpMode", "LocoPwrMode"],
     *                 "byteoft": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
     *                 "bitoft": [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
     *                 "len": [8, 8, 8, 8, 8, 8, 8, 8, 8, 8],
     *                 "type": ["UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8", "UNSIGNED8"]
     *             }
     *         }],
     *         "version": 1
     *     }
     * }
     * @param payload MQTT消息体
     */
    @Override
    public void processMessage(String topic, byte[] payload) {
        String msg = new String(payload);
        log.info("[北斗数据包MQTT策略] 收到消息: {}", msg);
        try {
            // 1. 解析MQTT消息JSON
            JSONObject msgJson = JSONObject.parseObject(msg);
            String beidouDataSourceHex = msgJson.getString("beidou_data");
            if (beidouDataSourceHex == null || beidouDataSourceHex.isEmpty()) {
                log.warn("[北斗数据包MQTT策略] 消息中无 beidou_data 字段");
                return;
            }

            // 2. 从数据包中提取版本ID（第5个字节）
            if (beidouDataSourceHex.length() < 10) {
                log.warn("[北斗数据包MQTT策略] 数据包长度不足，无法提取版本信息: hexLength={}", beidouDataSourceHex.length());
                return;
            }

            // 3. 校验数据包的CRC，并返回纯净的业务数据十六进制字符串
            String beidouDataHex = validateAndExtractBusinessHex(beidouDataSourceHex);
            if (beidouDataHex == null) {
                log.warn("[北斗数据包MQTT策略] CRC 32 校验失败");
                return;
            }
            // 此时：第一个字符是 0 或 1，0代表心跳 1代表真实的车辆数据
            // 如果是0，则跳过
            String oneChar = beidouDataHex.substring(0, 1);
            if ("0".equals(oneChar)) {
                log.warn("[北斗数据包MQTT策略] 跳过心跳数据[{}]", beidouDataHex);
//                return;
            }
//            else {
//                beidouDataHex = beidouDataHex.substring(1);
//            }
            beidouDataHex = beidouDataHex.substring(1);

            String versionHex = beidouDataHex.substring(8, 10);
            Long versionId = (long) Integer.parseInt(versionHex, 16);
            log.debug("[北斗数据包MQTT策略] 提取版本信息: versionHex={}, versionId={}", versionHex, versionId);

            // 3. 获取设备绑定关系中的报文配置
            VehicleBeidouRelationEntity relation = vehicleBeidouRelationService.getByVehicleIdAndVersionId(DEFAULT_VEHICLE_ID, 1L);
            if (relation == null || relation.getBdConfig() == null) {
                log.warn("[北斗数据包MQTT策略] 未找到设备绑定关系或配置为空");
                return;
            }

            // 4. 将 bdConfig 转为 DynamicPacketParser.ComIdConfig 列表
            JSONObject configJson = JSONObject.parseObject(relation.getBdConfig());
            JSONArray bdSignals = configJson.getJSONObject("data").getJSONArray("bd_signals");
            List<DynamicPacketParser.ComIdConfig> configList = new ArrayList<>();

            for (int i = 0; i < bdSignals.size(); i++) {
                JSONObject signalGroup = bdSignals.getJSONObject(i);
                int comId = signalGroup.getIntValue("comid");
                JSONObject data = signalGroup.getJSONObject("data");

                JSONArray signalNames = data.getJSONArray("signal");
                JSONArray byteOffsets = data.getJSONArray("byteoft");
                JSONArray bitOffsets = data.getJSONArray("bitoft");
                JSONArray lengths = data.getJSONArray("len");

                List<DynamicPacketParser.SignalConfig> signals = new ArrayList<>();
                for (int j = 0; j < signalNames.size(); j++) {
                    signals.add(new DynamicPacketParser.SignalConfig(
                            signalNames.getString(j),
                            byteOffsets.getIntValue(j),
                            bitOffsets.getIntValue(j),
                            lengths.getIntValue(j)
                    ));
                }
                configList.add(new DynamicPacketParser.ComIdConfig(comId, signals));
            }

            // 5. 使用 DynamicPacketParser.parsePacketWithComId2 解析北斗报文
            DynamicPacketParser.ParseResult parseResult =
                    DynamicPacketParser.parsePacketWithComId2(beidouDataHex, configList);
            log.warn("[北斗数据包MQTT策略] Hex：[{}]， 数据解析：{}", beidouDataHex, JSONObject.toJSONString(parseResult));

        } catch (Exception e) {
            log.error("[北斗数据包MQTT策略] 处理消息异常: {}", e.getMessage(), e);
        }
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
            log.warn("[北斗数据包MQTT策略] 数据包长度不合法");
            return null;
        }

        // 1. 去除空格并转大写，统一格式
        hex = hex.replaceAll("\\s", "").toUpperCase();

        // 2. 校验帧头
        if (!hex.startsWith(CRC32Verify.FRAME_HEADER)) {
            log.warn("[北斗数据包MQTT策略] 帧头错误，未找到 AABB");
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
            log.warn("[北斗数据包MQTT策略] CRC校验失败！ 接收到的CRC={}，计算出的CRC={}", receivedCrcHex, calculatedCrcHex);
            return null;
        }
        // 去掉帧头 AABB
        return hex.substring(4, totalLen - 8);
    }

}
