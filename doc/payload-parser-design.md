# Payload 解析设计说明

> 本文用于学习和实现“单个 MQTT Payload 的 Hex 解析”。当前阶段只关注一个 Payload 的解析，不涉及 MQTT 会话、状态机、跨 Payload 状态管理。

## 1. 目标

输入一个 MQTT 消息中的 Payload（Hex 字符串），按照当前真实 `payload.txt` 样本和协议定义逐字段读取，最终得到结构化的 Payload 解析结果。

参考实现位于 `src/main/java/com/cpw/topic/download/phm/`，但它仅作为协议理解和实现思路的参考；新实现不直接复用旧类。

## 2. 当前边界

### 本阶段处理

- Hex 字符串清洗与基本合法性校验
- Hex -> byte 的转换
- Payload 固定/可变字段的顺序读取
- 外层 Header、时间、长度、压缩标志解析
- zlib/DEFLATE 数据解压
- 设备相关字段解析
- CAN 数据帧解析
- CAN Frame 的 Type / Length / Data 解析
- 解析结果封装
- 非法数据、长度不足等输入错误的明确处理

### 本阶段暂不处理

- MQTT 连接
- MQTT 消息订阅
- MQTT Session
- 下载任务状态机
- 多个 Payload 之间的数据关联
- 跨 Payload 的差分数据缓存
- 业务层数据库写入

特别是 Type 2/3 这类需要历史完整 CAN 数据才能还原的逻辑，本阶段只负责**解析出协议层面的差分信息**；跨 Payload 的状态应该由后续独立组件管理。

## 3. 根据真实 payload 样本确认的外层结构

当前样本中出现了稳定的片段，例如：

```text
ccdd .... .... .... 000023d8 01 789c ...
```

以及多处同结构数据：

```text
ccdd .... .... .... 000023cd 01 789c ...
ccdd .... .... .... 000023d1 01 789c ...
```

结合参考实现，可以按以下 byte 结构解析一个 Payload：

```text
Offset       Length       Field
------------------------------------------------
0            2 byte       Header
2            4 byte       Start Time (sec)
6            2 byte       Start Time (msec)
8            4 byte       Packet Length
12           1 byte       Compress Flag
13           N byte       Data（N = Packet Length）
13 + N       1 byte       CRC
```

其中当前真实样本可以观察到：

```text
Header       = ccdd
CompressFlag = 01
Data 开头    = 78 9c ...
```

`78 9c` 是压缩数据本身的 zlib 头；外层 `01` 是压缩标志，因此新实现读取 `CompressFlag` 后，将剩余 `Packet Length` 个 byte 作为压缩数据交给 zlib/DEFLATE 解压。

> `Packet Length` 在新实现中按 **byte 数**处理，而不是 Hex 字符数量。

## 4. 解压后的结构

解压后的数据从 `Device Type` 开始：

```text
Device Type       2 byte
Device ID         2 byte
Device Area       1 byte
Reserved1         1 byte
Reserved2         2 byte
Sample Duration   2 byte
Counter           2 byte
CAN Payload       remaining
```

CAN Payload 再按 Frame 循环解析：

```text
AABB              2 byte
CAN ID            4 byte
Type              1 byte
Length            1 byte（Type=4 时没有）
Data              Length byte（Type=4 时没有）
```

## 5. 类结构

```text
com.cpw.topic.download.payload
│
├── PayloadParser
│   └── 单个 Payload 的总入口
│
├── HexReader
│   └── 顺序读取 Hex/byte 数据，负责 cursor、边界检查、整数读取
│
├── PayloadParseResult
│   └── 一次 Payload 解析后的完整结果
│
├── PayloadHeader
│   └── Payload 前置协议字段
│
├── DeviceInfo
│   └── 设备标识及设备相关字段
│
├── CanFrame
│   └── 一个 CAN 数据帧的结构化表示
│
├── CanFrameParser
│   └── 从 Payload 剩余数据中逐帧读取 CAN Frame
│
└── PayloadParseException
    └── 协议格式错误、长度不足等可预期解析异常
```

## 6. 类的职责

### 6.1 PayloadParser

**职责：单个 Payload 的解析编排器。**

调用方只需要：

```java
PayloadParseResult result = parser.parse(payloadHex);
```

它负责：

```text
Payload
 ↓
Header
 ↓
Start Time
 ↓
Packet Length
 ↓
Compress Flag
 ↓
Data / 解压
 ↓
DeviceInfo
 ↓
CAN Payload
 ↓
PayloadParseResult
```

不负责 MQTT，也不保存上一次 Payload 的数据。

### 6.2 HexReader

**职责：隐藏 Hex 游标和 byte 读取细节。**

例如：

```java
reader.readUInt8();
reader.readUInt16();
reader.readUInt32();
reader.readBytes(length);
reader.readHex(length);
reader.remaining();
```

offset 统一以 byte 为单位。

### 6.3 PayloadHeader

保存：

- Header
- Start Time Sec
- Start Time Msec
- Packet Length
- Compress Flag

### 6.4 DeviceInfo

保存：

- Device Type
- Device ID
- Device Area
- Reserved1
- Reserved2
- Sample Duration
- Counter

### 6.5 CanFrame

保存一个 CAN Frame 的协议层数据：

```text
CAN ID
Type
Data Length
Raw Data
Offset Bytes（Type 2/3）
```

### 6.6 CanFrameParser

只解析当前 Payload 中的 CAN Frame，不维护跨 Payload 的 Map。

### 6.7 PayloadParseResult

作为 Parser 的对外结果：

```text
PayloadParseResult
├── PayloadHeader
├── DeviceInfo
├── List<CanFrame>
└── CRC
```

### 6.8 PayloadParseException

用于表达输入数据不符合当前协议结构，例如：

- Hex 非法
- Header 错误
- Packet Length 超过实际数据
- zlib 解压失败
- DeviceInfo 数据不足
- CAN Frame 数据不足
- CAN Frame Header 不是 AABB

## 7. 调用链路

```text
业务调用方
    │
    │ parse(payloadHex)
    ▼
PayloadParser
    │
    ├── HexReader
    │
    ├── PayloadHeader
    │
    ├── zlib/DEFLATE 解压
    │
    ├── DeviceInfo
    │
    └── CanFrameParser
             │
             ├── CanFrame #1
             ├── CanFrame #2
             ├── CanFrame #3
             └── ...
    │
    ▼
PayloadParseResult
```

## 8. Type 处理原则

当前新 Parser 只做**协议层解析**：

```text
Type 1 -> 完整 Data
Type 2 -> 差分 Data
Type 3 -> 差分 Data
Type 4 -> 与之前数据一致的语义
```

Type 2/3/4 的历史数据还原属于后续状态/业务层，不在单 Payload Parser 内保存状态。

## 9. 测试策略

第一阶段测试重点是：

```text
真实 payload
    ↓
PayloadParser
    ↓
成功完成外层解析
    ↓
成功完成解压
    ↓
成功读取 DeviceInfo
    ↓
成功读取全部 CAN Frame
```

然后再对关键字段增加断言。

## 10. 学习要求

每次代码提交都应配套说明：

- 新增/修改了什么类
- 为什么需要这个修改
- 每个方法做什么
- Hex 游标如何移动
- 当前字段占几个 byte
- 为什么使用这个字节序
- 当前代码对应 Payload 中哪一段
- 当前代码与旧参考代码有什么区别
- 测试验证了什么

最终目标：能够独立完成：

```text
Hex
 ↓
按 byte 定位
 ↓
按协议解释
 ↓
解压
 ↓
解析 CAN Frame
 ↓
结构化对象
```
