# Payload 解析设计说明

> 本文用于学习和实现“单个 MQTT Payload 的 Hex 解析”。当前阶段只关注一个 Payload 的解析，不涉及 MQTT 会话、状态机、跨 Payload 状态管理。

## 1. 目标

输入一个 MQTT 消息中的 Payload（Hex 字符串），按照 `doc/MQTT数据协议.xlsx` 定义的协议结构逐字段读取，最终得到结构化的 Payload 解析结果。

参考实现位于 `src/main/java/com/cpw/topic/download/phm/`，但它仅作为协议理解和实现思路的参考；新实现不直接复用旧类。

## 2. 当前边界

### 本阶段处理

- Hex 字符串清洗与基本合法性校验
- Hex -> byte 的转换
- Payload 固定/可变字段的顺序读取
- 协议头解析
- 设备相关字段解析
- CAN 数据帧解析
- CAN Frame 的 Type / Length / Data 解析
- 解析结果封装
- 非法数据、长度不足等输入错误的明确处理
- 使用真实 `doc/payload.txt` 编写单元测试

### 本阶段暂不处理

- MQTT 连接
- MQTT 消息订阅
- MQTT Session
- 下载任务状态机
- 多个 Payload 之间的数据关联
- 跨 Payload 的差分数据缓存
- 业务层数据库写入

特别是 Type 2/3 这类需要历史完整 CAN 数据才能还原的逻辑，本阶段只负责**解析出协议层面的差分信息**；跨 Payload 的状态应该由后续独立组件管理。

## 3. 建议的类结构

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

> 实际类名和包名会在读取 Excel 协议、`payload.txt` 样本并核对旧实现后最终确定。本文先定义职责，不提前把协议字段硬编码成未经验证的结论。

## 4. 类的职责

### 4.1 PayloadParser

**职责：单个 Payload 的解析编排器。**

调用方只需要：

```java
PayloadParseResult result = parser.parse(payloadHex);
```

它负责按照协议顺序调用各解析步骤，但不负责 MQTT，也不保存上一次 Payload 的数据。

核心原则：

```text
输入 payload
   ↓
校验/创建读取器
   ↓
解析 Payload Header
   ↓
解析设备/元数据
   ↓
定位 CAN Payload
   ↓
逐个解析 CAN Frame
   ↓
封装 PayloadParseResult
```

### 4.2 HexReader

**职责：隐藏 Hex 游标和字节读取细节。**

例如：

```java
reader.readUInt8();
reader.readUInt16();
reader.readUInt32();
reader.readBytes(length);
reader.readHex(length);
reader.remaining();
```

这样协议解析代码表达的是“读一个 4 字节字段”，而不是到处出现 `substring(pos, pos + 8)`。

同时由 `HexReader` 统一负责：

- 游标推进
- 数据不足检查
- Hex 字符合法性
- 奇数长度检查
- 数值读取时的字节序

### 4.3 PayloadHeader

**职责：保存 Payload 最外层协议头解析结果。**

它只保存数据，不负责解析。

例如可能包含：

- Header / Magic
- 时间
- Packet Length
- Compression Flag
- 其他协议头字段

具体字段必须以 Excel 最终核对结果为准。

### 4.4 DeviceInfo

**职责：保存设备相关字段。**

例如旧实现中出现的：

- Device Type
- Device ID
- Device Area
- Reserved
- Sample Duration
- Counter

这些字段的长度、顺序和字节序以协议文档为准。

### 4.5 CanFrame

**职责：表示一个已经完成协议层解析的 CAN Frame。**

建议至少包含：

```text
CAN ID
Type
Data Length
Raw Data
```

对于 Type 2/3，可以进一步保存协议中的差分记录，而不是在这里访问上一帧/上一 Payload 的状态。

### 4.6 CanFrameParser

**职责：解析 CAN Payload 区域。**

基本循环：

```text
remaining > 0
    ↓
读取 Frame Header
    ↓
读取 CAN ID
    ↓
读取 Type
    ↓
读取 Length
    ↓
根据 Length 读取 Data
    ↓
创建 CanFrame
    ↓
继续下一帧
```

它不负责 MQTT Session，也不维护跨 Payload 的 `Map<CAN ID, fullData>`。

### 4.7 PayloadParseResult

**职责：作为 Parser 对外返回的结果对象。**

建议组织成：

```text
PayloadParseResult
├── PayloadHeader
├── DeviceInfo
├── List<CanFrame>
└── 原始/诊断信息（按实际需要）
```

这样调用方不需要知道 Parser 内部如何移动游标。

### 4.8 PayloadParseException

**职责：表达“输入 Payload 不符合协议”的解析错误。**

例如：

- Hex 字符非法
- Hex 长度为奇数
- Header 不正确
- 声明长度超过实际剩余数据
- CAN Frame 数据不完整
- 不支持的协议字段/Type

错误信息应该带上当前位置和上下文，方便学习和定位，例如：

```text
Cannot read CAN data: need 8 bytes, remaining 5 bytes, offset=123
```

## 5. 调用链路

```text
业务调用方
    │
    │ parse(payloadHex)
    ▼
PayloadParser
    │
    ├── HexReader 创建/初始化
    │
    ├── PayloadHeader 解析
    │
    ├── DeviceInfo 解析
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

更详细的运行顺序：

```text
payloadHex
   │
   ▼
[1] 输入校验
   │
   ▼
[2] HexReader 建立 cursor
   │
   ▼
[3] 读取协议 Header
   │
   ▼
[4] 根据 Header 得到后续区域位置/长度
   │
   ▼
[5] 读取设备与元数据
   │
   ▼
[6] 进入 CAN Payload
   │
   ▼
[7] CanFrameParser 循环读取
   │
   ├── Frame Header
   ├── CAN ID
   ├── Type
   ├── Length
   └── Data
   │
   ▼
[8] 组装 PayloadParseResult
```

## 6. 为什么把 HexReader 单独抽出来

初学 Hex 解析时，最容易出现的问题是把“协议含义”和“字符串下标”混在一起：

```java
int id = Integer.parseInt(hex.substring(pos, pos + 8), 16);
pos += 8;
```

如果协议字段长度、字节序或者边界判断发生变化，代码很快会变得难以维护。

使用 `HexReader` 后：

```java
long canId = reader.readUInt32();
int type = reader.readUInt8();
int length = reader.readUInt8();
byte[] data = reader.readBytes(length);
```

代码更接近协议文档本身，也更适合学习。

## 7. Type 处理原则

旧代码中存在 Type 1/2/3/4 的语义，并且 Type 2/3 涉及差分数据还原。

新 Parser 第一阶段采用以下边界：

```text
协议解析层：
    Type 1 -> 解析完整 Data
    Type 2 -> 解析差分描述
    Type 3 -> 解析差分描述
    Type 4 -> 解析一致/引用语义

状态/业务层：
    根据历史 Payload 还原最终完整 CAN Data
```

这样可以保证：

```text
PayloadParser.parse(payload1)
PayloadParser.parse(payload2)
```

两次调用之间没有隐藏状态。

## 8. 测试策略

### 单元测试

至少覆盖：

1. 正常 Payload 能完整解析
2. 空 Payload
3. 非法 Hex
4. 奇数长度 Hex
5. Header 错误
6. 声明长度大于实际数据
7. 单个 CAN Frame
8. 多个 CAN Frame
9. Type 1
10. Type 2
11. Type 3
12. Type 4

### 真实样本测试

`doc/payload.txt` 是第一份真实协议样本。测试应该直接读取它，执行：

```text
payload.txt
    ↓
PayloadParser
    ↓
PayloadParseResult
    ↓
断言关键字段
```

测试不仅验证“代码不报错”，还应该验证解析出的字段与 Excel 协议定义一致。

## 9. 实现顺序

为了便于学习，代码实现按下面顺序进行，而不是一次写完：

1. `HexReader`：先学会安全地读取 Hex
2. `PayloadHeader`：实现最外层协议字段
3. `DeviceInfo`：实现设备字段
4. `CanFrame`：定义 CAN Frame 数据模型
5. `CanFrameParser`：实现逐帧读取
6. `PayloadParseResult`：组合最终结果
7. `PayloadParser`：串起完整调用链
8. `PayloadParserTest`：用真实 Payload 验证
9. 根据测试结果修正协议字段、长度和字节序

## 10. 学习要求

每次代码提交都应配套说明：

- 新增了什么类
- 为什么需要这个类
- 每个方法做什么
- Hex 游标如何移动
- 当前字段占几个 byte
- 为什么使用这个字节序
- 当前代码对应 Excel 中哪一项
- 当前代码如何对应真实 `payload.txt`
- 测试验证了什么
- 如果参考旧代码，旧代码与新实现有什么区别

最终目标不是复制旧实现，而是能够根据协议文档独立完成：

```text
Hex
 ↓
按 byte 定位
 ↓
按协议解释
 ↓
结构化对象
 ↓
验证结果
```
