# H2O 透传协议

## 设计原则

1. 简单, 让嵌入式设备也能执行
2. 不追求速度, 对设备的内存要求要低

## 设计选择

初版仅支持 http + mqtt 搭配的协议

1. 对浏览器来说, 使用http协议, 外面可以套一层https
2. 对设备来说, 只有mqtt协议, 可以考虑加一层mqtts

## 通信设计

### 浏览器 <--> H2O服务

使用http协议, 可能只支持GET/POST/HEAD之类的简单请求

### 设备 <--> H2O 服务

使用mqtt协议

topic 使用
1. 设备 --> H2O服务 h2o/$devid/uplink
2. H2O服务 --> 设备 h2o/$devid/downlink

## 消息定义

### 基本属性
1. version, 当前值为1
2. action,肯定存在,字符串类型,代表具体的消息类型

### 登录消息(上行)
```json
{
    "action": "conn",
    "conn": {
        "files" : {
            "index.html" : {
                "sha1" : "1234567891230",
                "size" : 1234
            },
            "app.js" : {
                "sha1" : "45678910564564",
                "size" : 12343
            }
        }
    }
}
```

### 登录回复(下行)
```json
{
    "action": "conack",
    // 服务器端会对比静态文件缓存,如果有差异,会要求客户端上传新的
    // 若全部正常, static_files 就不会下发
    "conack": {
        "files" : {
            "index.html" : {
                "upload" : true
            },
            "app.js" : {
                "upload" : true
            }
        }
    }
}
```

### 文件上传(上行)

```json
{
    "action": "upload",
    "upload" : { 
        "name" : "index.html",
        // 若文件小于等于2048个字节,可单次直接上报
        "body" : "xxx", // 需要base64编码
        // 否则需要分段上传,每次分段1024字节.
        "frag" : 2,
        // 是否为最后一段
        "end"  : false,
        // 总文件大小是多少
        "size" : 10240,
    }
}
```

### http请求转发(下行)

下行数据发送后, 最多等待30秒, 超时将http 500

```json
{
    "action" : "req",
    "req" : {
        "id"     : "12343425234534", // 该请求的id,上报resp时需要带上
        "method" : "GET", // 服务器强制会转大写再下发
        "uri"    : "/api/netled",

        // 下面的所有字段,都是可选的, 不一定下发
        "headers" : {
            // 部分请求头, 不会全部下发到设备去
        },
        // 对应body, 会有不同的情况
        // 如果content-type是json
        "json" : {
            // body解析为json字符串再下发, 会提前校验值
        },
        // 如果有请求参数,会放在这个字段里
        "form" : {
            // 如果没有参数, 这个form会不存在
        },
        // 不认识的数据, base64编码后下发, 原始数据不超过2048字节
        "body"   : "xxxxxx" // 请求的body
    }
}
```

### http响应(上行)

```json
{
    "action" : "resp",
    "resp" : {
        "id"     : "12343425234534", // 该请求的id,上报resp时需要带上
        // 可选,响应码, 若不带这个值,默认200
        "code" : 200,
        // 可选, 需要添加的请求头
        "headers" : {
            // 部分请求头, 不会全部下发到设备去
        },
        // 可选, base64编码后上报, 原始数据不超过2048字节. 后续可能放宽
        "body"   : "xxxxxx" // 请求的body
    }
}
```

### 心跳Ping(上行)

与mqtt不同, 这里的属于业务心跳, 若无数据交互, 应该在90秒最少上报一次,否则会视为下线

```json
{
    "action" : "ping"
}
```

### 心跳Pong(下行)
```json
{
    "action" : "pong"
}
```
