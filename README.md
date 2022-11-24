# AirTun 内网穿透协议

专为嵌入式设备设计的内网穿透协议

1. 无缝访问设备的API
2. 使用浏览器就可以访问设备的页面,透传服务提供缓存服务,最大限度减少流量消耗
3. 提供友好的默认配置, 即开即用

## 适配的设备

1. [Air780E](https://wiki.luatos.com/chips/air780e/index.html) https://air780e.cn
2. [ESP32C3](https://wiki.luatos.com/chips/esp32c3/index.html)
3. [Air105](https://wiki.luatos.com/chips/air105/index.html),配合W5500
4. [Air724UG](https://air724ug.cn) 待定

所需要固件可以在release页面下载

如需要翻阅客户端源码,可打开[client](client)目录

刷机教程 https://space.bilibili.com/532832

## 服务器端

1. 公共测试服务器 `mqtt.air32.cn` 及 搭配的域名为 `*.airtun.air32.cn`,其中`*`指设备识别号,例如IMEI
2. [自建服务器指南](doc/server.md)

## 授权协议

[MIT License](LICENSE)

