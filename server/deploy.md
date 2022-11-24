# 自建服务器指南

## 部署前的准备

1. 一台有外网ip的服务器
2. 若为国内的服务器,请先为域名进行备案
3. 安装好docker和docker-compose
4. 服务器对外开放 `1883` 端口作为mqtt服务, 及 `80` 和 `443` 端口作为http服务

## 部署步骤

1. 获取部署文件, 可直接clone本代码库, 取server目录里面的文件进行部署
2. 新建文件夹 `/opt/airtun`
3. 将 `docker-compose.yml` `mosquitto.conf` `application.properties` 拷贝到 `/opt/airtun`
4. 执行下列命令即可启动

```bash
/opt/airtun
docker-compose up -d
```

如需查看日志

```
docker-compose logs -f
```

## 自行编译服务器端

服务器端是java写的, 若需修改, 需具备一定的Java基础和docker基础

### 先安装mvn,在server目录执行

```
mvn clean package nutzboot:shade
```

### 然后打包成docker镜像

```
docker build -t mydocker/airtun .
```

### 最后修改`docker-compose.yml`里面的image进行部署即可
