package com.cpw.topic.download.payload;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 单个 Payload 的总入口。
 *
 * <p>这个类没有任何 Session 字段，因此每次 parse 都是一次独立解析。</p>
 */
public final class PayloadParser {

    private static final int OUTER_HEADER_BYTES = 2;
    private static final int CRC_BYTES = 1;
    private static final int CAN_HEADER = 0xAABB;

    private final CanFrameParser canFrameParser = new CanFrameParser();

    public PayloadParseResult parse(String payloadHex) {
        HexReader reader = new HexReader(payloadHex);
        reader.require(2 + 4 + 2 + 4 + 1 + CRC_BYTES);

        // 1. 最外层 Header：2 byte
        String headerHex = reader.readHex(OUTER_HEADER_BYTES);

        // 2. 开始时间：秒 4 byte + 毫秒 2 byte
        long startTimeSec = reader.readUInt32();
        int startTimeMsec = reader.readUInt16();

        // 3. packet length：4 byte
        long packetLength = reader.readUInt32();

        // 4. compression flag：1 byte
        int compressFlag = reader.readUInt8();

        PayloadHeader header = new PayloadHeader(
                headerHex, startTimeSec, startTimeMsec, packetLength, compressFlag);

        // 最后 1 byte 按旧协议作为 CRC 保留；当前阶段先解析并返回，不验证算法。
        if (reader.remaining() < CRC_BYTES) {
            throw new PayloadParseException("Payload 缺少 CRC 字节", reader.position());
        }

        int bodyLength = reader.remaining() - CRC_BYTES;
        byte[] body = reader.readBytes(bodyLength);
        String crcHex = reader.readHex(CRC_BYTES);

        // 5. compression flag=0：body 本身就是 deviceType 开始的数据。
        //    compression flag!=0：body 是 [压缩数据]，按参考实现使用 DEFLATE 解压。
        byte[] decompressed = compressFlag == 0 ? body : inflate(body);
        HexReader bodyReader = new HexReader(toHex(decompressed));

        // 6. deviceType：2 byte
        int deviceType = bodyReader.readUInt16();
        // 7. deviceId：2 byte
        int deviceId = bodyReader.readUInt16();

        // 8. 新协议字段：area 1 + reserved1 1 + reserved2 2
        int deviceArea = bodyReader.readUInt8();
        int reserved1 = bodyReader.readUInt8();
        int reserved2 = bodyReader.readUInt16();

        // 9. sampleDuration：2 byte
        int sampleDuration = bodyReader.readUInt16();
        // 10. counter：2 byte
        int counter = bodyReader.readUInt16();

        DeviceInfo deviceInfo = new DeviceInfo(
                deviceType, deviceId, deviceArea, reserved1, reserved2,
                sampleDuration, counter);

        // 11. 剩余区域全部交给 CAN Frame Parser。
        if (!bodyReader.hasRemaining()) {
            throw new PayloadParseException("设备信息之后没有 CAN Payload", bodyReader.position());
        }

        if (bodyReader.remaining() < 2) {
            throw new PayloadParseException("CAN Payload 长度不足以读取 Header", bodyReader.position());
        }

        return new PayloadParseResult(
                header,
                deviceInfo,
                canFrameParser.parse(bodyReader),
                crcHex);
    }

    /**
     * 参考旧实现的压缩方式：压缩标志已经在外层读过，因此这里只处理 DEFLATE 数据。
     */
    private byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new PayloadParseException("DEFLATE 数据不完整或无法解压", 0);
                    }
                } else {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        } catch (DataFormatException e) {
            throw new PayloadParseException("DEFLATE 解压失败", 0, e);
        } catch (java.io.IOException e) {
            throw new PayloadParseException("创建解压输出失败", 0, e);
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
