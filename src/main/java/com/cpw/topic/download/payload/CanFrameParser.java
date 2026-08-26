package com.cpw.topic.download.payload;

import java.util.ArrayList;
import java.util.List;

/** 解析解压后的 CAN Payload 区域。不会保存跨 Payload 状态。 */
public final class CanFrameParser {

    private static final int HEADER = 0xAABB;

    public List<CanFrame> parse(HexReader reader) {
        List<CanFrame> frames = new ArrayList<>();

        while (reader.hasRemaining()) {
            int frameOffset = reader.position();
            int headerValue = reader.readUInt16();
            if (headerValue != HEADER) {
                throw new PayloadParseException(
                        String.format("CAN Frame Header 错误，期望 AABB，实际 %04X", headerValue),
                        frameOffset);
            }

            long canId = reader.readUInt32();
            int type = reader.readUInt8();

            // Type=4 按旧参考实现的协议语义：没有 length/data，表示与全量数据一致。
            if (type == 4) {
                frames.add(new CanFrame("aabb", canId, type, 0, new byte[0], null));
                continue;
            }

            int length = reader.readUInt8();
            byte[] data = reader.readBytes(length);

            Integer offsetBytes = null;
            if (type == 2) {
                offsetBytes = 1;
            } else if (type == 3) {
                offsetBytes = 2;
            }

            frames.add(new CanFrame("aabb", canId, type, length, data, offsetBytes));
        }

        return frames;
    }
}
