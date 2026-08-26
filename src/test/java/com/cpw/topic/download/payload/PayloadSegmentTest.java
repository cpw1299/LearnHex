package com.cpw.topic.download.payload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Payload 分段测试。
 *
 * <p>规则：以 ccdd 作为 Payload 起始标记，从当前 ccdd 开始，
 * 一直截取到下一个 ccdd 之前。</p>
 *
 * <p>本测试不参与 MQTT 状态机，也不负责 Payload 内部字段解析，
 * 只验证最外层的 Payload 分段规则。</p>
 */
public class PayloadSegmentTest {

    private static final String HEADER = "ccdd";

    public static void main(String[] args) throws Exception {
        testSplitByHeader();
        testPayload01File();
        System.out.println("PayloadSegmentTest: ALL TESTS PASSED");
    }

    /**
     * 使用简单数据验证：ccdd -> 下一个 ccdd 之前。
     */
    private static void testSplitByHeader() {
        String input = "0011 ccdd 0102 0304 ccdd aabb ccdd 1122";

        List<String> payloads = splitPayloads(input);

        assertEquals(3, payloads.size(), "Payload 数量");
        assertEquals("ccdd01020304", payloads.get(0), "第 1 个 Payload");
        assertEquals("ccddaabb", payloads.get(1), "第 2 个 Payload");
        assertEquals("ccdd1122", payloads.get(2), "第 3 个 Payload");
    }

    /**
     * 验证 File Library 中的 payload_01.txt：
     * 该文件本身就是从一个 ccdd 开始，到下一个 ccdd 之前截取出的单个 Payload。
     *
     * <p>运行方式：</p>
     * <pre>
     * java ... PayloadSegmentTest /path/to/payload_01.txt
     * </pre>
     *
     * <p>如果没有传参数，则默认读取项目根目录下的 payload_01.txt。</p>
     */
    private static void testPayload01File() throws IOException {
        Path file = Path.of(System.getProperty("payload.file", "payload_01.txt"));

        if (!Files.exists(file)) {
            System.out.println("SKIP: 未找到 " + file.toAbsolutePath());
            System.out.println("       可使用 -Dpayload.file=/path/to/payload_01.txt 指定文件。");
            return;
        }

        String input = Files.readString(file);
        String normalized = normalizeHex(input);

        List<String> payloads = splitPayloads(normalized);

        assertEquals(1, payloads.size(), "payload_01.txt 应只包含 1 个 Payload");
        assertTrue(payloads.get(0).startsWith(HEADER), "payload_01.txt 必须以 ccdd 开头");
        assertTrue(payloads.get(0).length() > HEADER.length(), "Payload 不能只有 ccdd");

        System.out.println("payload_01.txt Payload 长度: "
                + (payloads.get(0).length() / 2) + " byte");
        System.out.println("payload_01.txt 前 32 byte: "
                + preview(payloads.get(0), 32));
    }

    /**
     * 按 ccdd 分割 Payload。
     *
     * <p>注意：这里先找第一个 ccdd，因此 ccdd 之前的内容会被忽略。
     * 对于当前协议，这是为了允许输入数据前面存在无关字节。</p>
     */
    static List<String> splitPayloads(String hex) {
        String normalized = normalizeHex(hex);
        List<String> result = new ArrayList<>();

        int start = normalized.toLowerCase(Locale.ROOT).indexOf(HEADER);
        while (start >= 0) {
            int next = normalized.toLowerCase(Locale.ROOT).indexOf(HEADER, start + HEADER.length());
            if (next < 0) {
                result.add(normalized.substring(start));
                break;
            }

            result.add(normalized.substring(start, next));
            start = next;
        }

        return result;
    }

    /**
     * 去掉十六进制文本中的空格、换行等格式字符，并统一成小写。
     */
    static String normalizeHex(String input) {
        return input.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String preview(String hex, int byteCount) {
        int end = Math.min(hex.length(), byteCount * 2);
        return hex.substring(0, end);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
