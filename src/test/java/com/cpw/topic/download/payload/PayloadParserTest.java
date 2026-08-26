package com.cpw.topic.download.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadParserTest {

    /**
     * 这是一个人为构造的最小正常 Payload，用来先验证“游标 + 字段长度 + CAN Frame”这条链路。
     * 真实 payload.txt 到位后，再增加真实样本测试。
     */
    @Test
    void shouldParseMinimalPayload() {
        String payload =
                "1234" +                 // outer header: 2 byte
                "00000001" +             // startTimeSec: 4 byte
                "0002" +                 // startTimeMsec: 2 byte
                "00000014" +             // packetLength: 4 byte（本测试只保留协议字段）
                "00" +                   // compressFlag
                "0001" +                 // deviceType
                "0002" +                 // deviceId
                "03" +                   // deviceArea
                "04" +                   // reserved1
                "0005" +                 // reserved2
                "0006" +                 // sampleDuration
                "0007" +                 // counter
                "aabb" +                 // CAN header
                "00000123" +             // CAN ID
                "01" +                   // type
                "02" +                   // length
                "1122" +                 // data
                "ff";                    // CRC（当前只保存，不校验）

        PayloadParseResult result = new PayloadParser().parse(payload);

        assertEquals("1234", result.getHeader().getHeader());
        assertEquals(1, result.getDeviceInfo().getDeviceType());
        assertEquals(2, result.getDeviceInfo().getDeviceId());
        assertEquals(1, result.getCanFrames().size());
        assertEquals(0x123, result.getCanFrames().get(0).getCanId());
        assertEquals(1, result.getCanFrames().get(0).getType());
        assertEquals("1122", result.getCanFrames().get(0).getDataHex());
        assertEquals("ff", result.getCrcHex());
    }
}
