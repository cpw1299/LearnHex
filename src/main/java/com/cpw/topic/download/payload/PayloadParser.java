package com.cpw.topic.download.payload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 单个 MQTT Payload 的解析入口。
 *
 * <p>当前实现只负责一个 Payload，不保存任何跨 Payload 状态。</p>
 *
 * <p>根据当前提供的真实 payload 样本，外层结构为：</p>
 * <pre>
 * Header             2 byte
 * StartTimeSec       4 byte
 * StartTimeMsec      2 byte
 * PacketLength       4 byte
 * CompressFlag       1 byte
 * Data               PacketLength byte
 * CRC                1 byte
 * </pre>
 *
 * <p>样本中可以看到类似：
 * {@code ccdd ... 000023d8 01 789c ...}。
 * 其中 {@code 01} 表示后面的 Data 使用 zlib/DEFLATE 压缩，
 * {@code 789c} 是实际压缩数据的 zlib 头。</p>
 */
public final class PayloadParser {

    private static final String EXPECTED_HEADER = "ccdd";
    private static final int HEADER_BYTES = 2;
    private static final int START_TIME_SEC_BYTES = 4;
    private static final int START_TIME_MSEC_BYTES = 2;
    private static final int PACKET_LENGTH_BYTES = 4;
    private static final int COMPRESS_FLAG_BYTES = 1;
    private static final int CRC_BYTES = 1;

    private final CanFrameParser canFrameParser = new CanFrameParser();

    /**
     * 解析一个完整 Payload。
     */
    public PayloadParseResult parse(String payloadHex) {
        HexReader reader = new HexReader(payloadHex);

        // 1. 读取最外层固定头部。
        reader.require(HEADER_BYTES
                + START_TIME_SEC_BYTES
                + START_TIME_MSEC_BYTES
                + PACKET_LENGTH_BYTES
                + COMPRESS_FLAG_BYTES
                + CRC_BYTES);

        String headerHex = reader.readHex(HEADER_BYTES);
        if (!EXPECTED_HEADER.equalsIgnoreCase(headerHex)) {
            throw new PayloadParseException(
                    "Payload Header 错误，期望 " + EXPECTED_HEADER + "，实际 " + headerHex,
                    0);
        }

        // 2. Start Time：秒 4 byte + 毫秒 2 byte。
        long startTimeSec = reader.readUInt32();
        int startTimeMsec = reader.readUInt16();

        // 3. Packet Length：单位为 byte，表示 CompressFlag 后面的 Data 长度。
        long packetLengthLong = reader.readUInt32();
        if (packetLengthLong > Integer.MAX_VALUE) {
            throw new PayloadParseException("Packet Length 超出 Java int 范围", reader.position());
        }
        int packetLength = (int) packetLengthLong;

        // 4. Compression Flag。
        int compressFlag = reader.readUInt8();

        PayloadHeader header = new PayloadHeader(
                headerHex,
                startTimeSec,
                startTimeMsec,
                packetLengthLong,
                compressFlag);

        // Packet Length 明确决定 Data 的边界，不能再简单地把“剩余所有内容”都当 Data。
        reader.require(packetLength + CRC_BYTES);

        // 5. 读取 Data，并紧接着读取 CRC。
        byte[] encodedData = reader.readBytes(packetLength);
        String crcHex = reader.readHex(CRC_BYTES);

        // 如果一个调用只应该解析一个 Payload，那么这里必须已经读完。
        if (reader.hasRemaining()) {
            throw new PayloadParseException(
                    "单个 Payload 解析后仍有剩余数据：" + reader.remaining() + " byte",
                    reader.position());
        }

        // 6. 根据 Compression Flag 得到真正的 deviceType -> CAN Payload 数据。
        byte[] decompressed = compressFlag == 0
                ? encodedData
                : inflate(encodedData);

        HexReader bodyReader = new HexReader(toHex(decompressed));

        // 7. Device Type：2 byte。
        int deviceType = bodyReader.readUInt16();

        // 8. Device ID：2 byte。
        int deviceId = bodyReader.readUInt16();

        // 9. 新协议固定字段：area 1 + reserved1 1 + reserved2 2。
        int deviceArea = bodyReader.readUInt8();
        int reserved1 = bodyReader.readUInt8();
        int reserved2 = bodyReader.readUInt16();

        // 10. Sample Duration：2 byte。
        int sampleDuration = bodyReader.readUInt16();

        // 11. Counter：2 byte。
        int counter = bodyReader.readUInt16();

        DeviceInfo deviceInfo = new DeviceInfo(
                deviceType,
                deviceId,
                deviceArea,
                reserved1,
                reserved2,
                sampleDuration,
                counter);

        // 12. 剩余内容就是 CAN Payload。
        if (!bodyReader.hasRemaining()) {
            throw new PayloadParseException(
                    "设备信息之后没有 CAN Payload",
                    bodyReader.position());
        }

        return new PayloadParseResult(
                header,
                deviceInfo,
                canFrameParser.parse(bodyReader),
                crcHex);
    }

    /**
     * 解压真实样本中的 zlib/DEFLATE 数据。
     *
     * <p>注意：外层的 CompressFlag 已经被读取，因此传入这里的数组从
     * {@code 78 9c ...} 开始，而不是从 {@code 01 78 9c ...} 开始。</p>
     */
    private byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater(false);
        inflater.setInput(compressed);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);

                if (count > 0) {
                    output.write(buffer, 0, count);
                    continue;
                }

                if (inflater.needsDictionary()) {
                    throw new PayloadParseException("DEFLATE 数据需要预设字典", 0);
                }
                if (inflater.needsInput()) {
                    throw new PayloadParseException("DEFLATE 数据不完整", 0);
                }

                // 没有输出、也不需要输入/字典，说明 Inflater 已经无法继续推进。
                throw new PayloadParseException("DEFLATE 解压无法继续", 0);
            }

            return output.toByteArray();
        } catch (DataFormatException | IOException e) {
            throw new PayloadParseException("DEFLATE 解压失败", 0, e);
        } finally {
            inflater.end();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xFF));
        }
        return result.toString();
    }
}
