call mvn clean package nutzboot:shade
docker build -t registry.cn-beijing.aliyuncs.com/wendal/airtun .
