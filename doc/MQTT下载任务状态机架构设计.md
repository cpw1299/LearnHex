# 《SpringBoot + MQTT 下载任务架构设计（完整版）》

## 第一章 项目背景

- 1.1 项目背景
- 1.2 download_data 协议说明
- 1.3 为什么不能直接在 MQTT Listener 写业务
- 1.4 架构目标
  - 高并发
  - 高内聚低耦合
  - 易维护
  - 易扩展

----

## 第二章 协议分析

根据你上传的协议分析整个生命周期。

包括：

```textile
download_data

↓

MQTT_RESPONSE

↓

download/data

↓

download/info

↓

download/data

↓

download/info

↓

......

↓

MQTT_RESPONSE

↓

FINISHED
```

分析：

- request_msg_id
- download_task_id
- MQTT_RESPONSE
- MQTT_DOWNLOAD
- Topic职责
- 一个Task生命周期

----

## 第三章 总体架构设计

完整架构图

```textile
                   MQTT

                     │

                     ▼

             MQTT Listener

                     │

                     ▼

              MessageDispatcher

                     │

                     ▼

              MessageParser

                     │

                     ▼

         ApplicationEventPublisher

                     │

    ┌────────┬──────────┬─────────┐

    ▼        ▼          ▼         ▼

Session   Storage    Notify     Log

    │

    ▼

SessionManager

    │

    ▼

ConcurrentHashMap
```

说明：

为什么这样拆层。

每层职责。

----

## 第四章 包结构设计

例如

```textile
mqtt-download

├── config
│
├── listener
│
├── dispatcher
│
├── parser
│
├── event
│
├── service
│
├── session
│
├── repository
│
├── model
│
├── constant
│
├── util
│
└── exception
```

解释每个包为什么存在。

----

## 第五章 时序图

例如

```textile
平台发送

↓

创建Session

↓

收到Response

↓

收到Data

↓

收到Info

↓

收到Finish

Mermaid

SequenceDiagram
```

全部画出来。

----

## 第六章 类图

所有类之间关系。

例如

```textile
DownloadDispatcher

↓

DownloadService

↓

DownloadSessionManager

↓

DownloadSession
```

UML全部画。

----

# 第七章 Session设计

DownloadSession

DownloadStatus

Session生命周期

Session状态图

例如

```textile
INIT

↓

RUNNING

↓

RECEIVING

↓

VERIFYING

↓

FINISHED

↓

FAILED
```

说明什么时候进入哪个状态。

----

# 第八章 Event设计

所有Event

```textile
DownloadStartEvent

DownloadDataEvent

DownloadInfoEvent

DownloadFinishEvent

DownloadFailEvent
```

说明：

为什么拆这么多Event。

每个Event里面放什么。

为什么不要放byte[]。

----

# 第九章 Dispatcher设计

为什么Dispatcher只负责：

```textile
Topic

↓

Parser

↓

PublishEvent
```

而不能写业务。

包括：

职责分析。

SOLID原则。

----

# 第十章 Parser设计

JSON解析。

ObjectMapper统一配置。

异常处理。

DTO转换。

----

# 第十一章 DownloadService设计

真正业务入口。

例如

```textile
start()

receiveData()

receiveInfo()

finish()

fail()
```

说明：

为什么所有业务都应该经过Service。

----

# 第十二章 SessionManager设计

接口

```textile
create()

get()

remove()

exists()

timeout()

clean()
```

以后如何切Redis。

----

# 第十三章 Listener设计

例如：

```textile
SessionListener

StorageListener

NotifyListener

LogListener

MetricListener
```

为什么一个Listener只干一件事。

----

# 第十四章 Storage设计

这里先TODO。

以后：

```textile
File

DB

OSS

Kafka
```

如何扩展。

----

# 第十五章 异常处理

例如

```textile
JSON解析失败

Session不存在

重复Finish

重复Info

重复Data

MQTT断开

超时

Task不存在
```

如何处理。

----

# 第十六章 超时机制

例如

Session

30分钟没有收到数据。

自动：

```textile
FAILED

↓

remove()
```

Scheduled实现。

----

# 第十七章 并发设计

ConcurrentHashMap

为什么不用HashMap。

为什么不用synchronized。

ConcurrentHashMap有没有问题。

AtomicInteger是否需要。

----

# 第十八章 可扩展设计

以后增加

```textile
FTP

TCP

HTTP

WebSocket
```

不用改业务。

只改Dispatcher。

----

# 第十九章 Demo源码

真正可运行。

预计20多个类。

包括：

```textile
DownloadSession.java

DownloadStatus.java

DownloadDispatcher.java

DownloadMessageParser.java

DownloadService.java

DefaultDownloadService.java

DownloadSessionManager.java

MemoryDownloadSessionManager.java

DownloadStartEvent.java

DownloadDataEvent.java

DownloadInfoEvent.java

DownloadFinishEvent.java

SessionListener.java

StorageListener.java

NotifyListener.java

LogListener.java

DownloadInfo.java

MqttResponse.java

DownloadDataPacket.java

Application.java
```

全部可以运行。

----

# 第二十章 最佳实践

例如

为什么不要：

```textile
MQTTListener

↓

写数据库
```

为什么不要：

```textile
MQTTListener

↓

解析

↓

业务

↓

日志

↓

通知

↓

Session

↓

数据库
```

而应该：

```textile
MQTT

↓

Dispatcher

↓

Service

↓

Event

↓

Session

↓

Storage
```

----

最终Demo框架

```textile
mqtt-download-demo
│
├── pom.xml
├── README.md
│
└── src
    ├── main
    │
    ├── java
    │   └── com.demo.mqtt
    │
    │      ├── Application.java
    │      │
    │      ├── config
    │      │      MqttConfig.java
    │      │
    │      ├── listener
    │      │      MqttMessageListener.java
    │      │
    │      ├── dispatcher
    │      │      DownloadDispatcher.java
    │      │
    │      ├── parser
    │      │      DownloadMessageParser.java
    │      │
    │      ├── service
    │      │      DownloadService.java
    │      │      DefaultDownloadService.java
    │      │
    │      ├── session
    │      │      DownloadSession.java
    │      │      DownloadSessionManager.java
    │      │      MemoryDownloadSessionManager.java
    │      │      DownloadStatus.java
    │      │
    │      ├── event
    │      │      DownloadStartEvent.java
    │      │      DownloadDataEvent.java
    │      │      DownloadInfoEvent.java
    │      │      DownloadFinishEvent.java
    │      │
    │      ├── event.listener
    │      │      SessionListener.java
    │      │      StorageListener.java
    │      │      NotifyListener.java
    │      │      LogListener.java
    │      │
    │      ├── model
    │      │      MqttResponse.java
    │      │      DownloadInfo.java
    │      │      DownloadDataPacket.java
    │      │
    │      └── util
    │             JsonUtil.java
    │
    └── resources
           application.yml
```

----

## 最终文档规模

预计包括：

- **约 20~25 个 Java Demo 类**（可直接运行）
- **约 10 张 Mermaid 图**（类图、时序图、状态图、流程图）
- **约 40~60 页 Markdown 文档**
- **约 1500~2500 行 Java 示例代码**
- **完整目录结构**
- **完整 SpringBoot 分层架构**
- **可直接迁移到你的 MQTT 项目**

**严格结合上传的《MQTT指令交互方案设计》中的 download_data 协议**进行设计
