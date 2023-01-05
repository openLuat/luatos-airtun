# AirTun 内网穿透协议

专为嵌入式设备设计的内网穿透协议

1. 无缝访问设备的API
2. 使用浏览器就可以访问设备的页面,透传服务提供缓存服务,最大限度减少流量消耗
3. 提供友好的默认配置, 即开即用

## 已适配的设备

LuatOS系列:

1. [Air780E](https://wiki.luatos.com/chips/air780e/index.html) https://air780e.cn
2. [Air105](https://wiki.luatos.com/chips/air105/index.html),需配合W5500
3. [Air724UG](https://air724ug.cn)
4. [Air820UG](https://air820ug.cn)
5. [ESP32C3](https://wiki.luatos.com/chips/esp32c3/index.html)
6. [ESP32C2](https://wiki.luatos.com/chips/esp32c3/index.html)

## 客户端须知

* 已合成好的固件可以在[发行版](https://gitee.com/openLuat/luatos-airtun/releases)页面下载
* 如需要翻阅客户端源码,可打开[client](client)目录
* 刷机教程 https://space.bilibili.com/532832

## 服务器端

### 公共测试服务器

仓库内的代码使用的是公共测试服务器,信息如下
1. mqtt服务 域名 `mqtt.air32.cn` 端口 `1883` 非加密模式
2. 搭配的泛域名 `*.airtun.air32.cn`,其中`*`指设备识别号,例如IMEI, 支持http和https

## 自建服务器

请查阅文档 [自建服务器指南](server/deploy.md)

## 应用合集

* [4G遥控小车](turnkey/4g_minicar/)

## 授权协议

[MIT License](LICENSE)

