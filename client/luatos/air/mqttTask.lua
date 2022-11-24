--- 模块功能：MQTT客户端处理框架
-- @author openLuat
-- @module mqtt.mqttTask
-- @license MIT
-- @copyright openLuat
-- @release 2018.03.28

module(...,package.seeall)

require"misc"
require"mqtt"
require"mqttOutMsg"
require"mqttInMsg"

-- 根据自己的服务器修改以下参数
-- local mqtt_host = "broker-cn.emqx.io"
local mqtt_host = "mqtt.air32.cn"
local mqtt_port = 1883
local mqtt_isssl = false

local mqtt_client_id
local mqtt_username
local mqtt_password = "1234567890"
local device_id

-- airtun上传的文件
airtun_files = {
    "index.html",
    "gpio.html"
}

local ready = false


-- TODO 上报conn信息
local function airtun_conn(mqttClient)
    local msg = {action="conn"}
    msg.conn = {files={}}
    for i=1,#airtun_files do
        if io.exists("/lua/"..airtun_files[i]) then
            local filePath = "/lua/"..airtun_files[i]
            local sha1 = crypto.sha1(io.readFile(filePath), #io.readFile(filePath))       -- 使用sha1算法计算文件hash值
            msg.conn.files[airtun_files[i]] =  {sha1=sha1, size=io.fileSize(filePath)}
        end
    end
    
    log.info("msg", json.encode(msg))
    log.info("public result", mqttClient:publish(_G.topic_up, json.encode(msg), 1))
end

--- MQTT连接是否处于激活状态
-- @return 激活状态返回true，非激活状态返回false
-- @usage mqttTask.isReady()
function isReady()
    return ready
end

--启动MQTT客户端任务
sys.taskInit(
    function()
        sys.wait(3000)
        mqtt_client_id = misc.getImei():toHex()
        mqtt_username = misc.getImei():toHex()
        device_id = misc.getImei()
        log.info("IMEI", misc.getImei(), mqtt_client_id, mqtt_username)

        device_id = device_id:lower()
        _G.topic_up = "$airtun/" .. device_id .. "/up"
        _G.topic_down = "$airtun/" .. device_id .. "/down"

        log.info("airtun", "topic up", _G.topic_up)
        log.info("airtun", "topci down", _G.topic_down)
        log.info("airtun", "===========================================")
        log.info("airtun", "Pls open https://" .. device_id .. ".airtun.air32.cn")
        log.info("airtun", "===========================================")

        local retryConnectCnt = 0
        while true do
            if not socket.isReady() then
                retryConnectCnt = 0
                --等待网络环境准备就绪，超时时间是5分钟
                sys.waitUntil("IP_READY_IND",300000)
            end
            
            if socket.isReady() then
                 --创建一个MQTT客户端
                local mqttClient = mqtt.client(mqtt_client_id,240,mqtt_username,mqtt_password)
                --阻塞执行MQTT CONNECT动作，直至成功
                if mqttClient:connect(mqtt_host,mqtt_port,"tcp") then
                    log.info("mqtt", "mqtt已连接")
                    retryConnectCnt = 0
                    ready = true
                    
                    --订阅主题
                    if mqttClient:subscribe(_G.topic_down,1) then
                        log.info("subscribe result success")
                        -- TODO 上报conn信息
                        airtun_conn(mqttClient)
    
                        --循环处理接收和发送的数据
                        while true do
                            if not mqttInMsg.proc(mqttClient) then log.error("mqttTask.mqttInMsg.proc error") break end
                            if not mqttOutMsg.proc(mqttClient) then log.error("mqttTask.mqttOutMsg proc error") break end
                        end
                    end
                    ready = false
                else
                    retryConnectCnt = retryConnectCnt+1
                end
                --断开MQTT连接
                mqttClient:disconnect()
                if retryConnectCnt>=5 then link.shut() retryConnectCnt=0 end
                sys.wait(5000)
            else
                --进入飞行模式，20秒之后，退出飞行模式
                net.switchFly(true)
                sys.wait(20000)
                net.switchFly(false)
            end
        end
    end
)
