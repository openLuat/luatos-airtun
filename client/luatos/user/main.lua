
PROJECT = "h2o"
VERSION = "1.0.0"

-- sys库是标配
_G.sys = require("sys")
_G.sysplus = require("sysplus")

-- 根据自己的服务器修改以下参数
local mqtt_host = "broker-cn.emqx.io"
local mqtt_port = 1883
local mqtt_isssl = false

local mqtt_client_id = mcu.unique_id()
local mqtt_username = mcu.unique_id()
local mqtt_password = "1234567890"
local device_id = mcu.unique_id()

local mqttc = nil
local LED = nil
local topic_down = nil
local topic_up = nil

function h2o_resp(id, code, headers, body)
    if mqttc and mqttc:ready() then
        local msg = {action="resp"}
        if body then
            if type(body) == "table" then
                body = json.encode(body)
            elseif type(body) ~= "string" then
                body = tostring(body)
            end
            body = body:toBase64()
        end
        msg["resp"] = {id=id, code=code, headers=headers, body=body}
        local payload = json.encode(msg)
        log.debug("h2o", "resp", payload)
        if payload then
            mqttc:publish(topic_up, payload, 1)
        end
    end
end


function h2o_resp_json(id, code, headers, body)
    headers = headers or {}
    headers["Content-Type"] = "appilcation/json"
    body = json.encode(body)
    h2o_resp(id, code, headers, body)
end

function h2o_ws(body)
    if mqttc and mqttc:ready() then
        mqttc:publish(topic_up, (json.encode({action="ws", ws=body})), 1)
    end
end

function h2o_handle(linkmsg)
    --log.info("h2o", "action", linkmsg.action)
    if linkmsg.action == "conack" then
        if linkmsg.conack and linkmsg.conack.files then
            for k, _ in pairs(linkmsg.conack.files) do
                local path = "/luadb/" .. k
                local fd = io.open(path)
                if fd then
                    local index = 0
                    local msg = {action="upload", upload={name=k, size=io.fileSize(path)}}
                    while 1 do
                        local data = fd:read(1024)
                        if not data or #data == 0 then break end
                        msg.upload.body = data:toBase64()
                        msg.upload["index"] = index
                        if #data < 1024 then
                            msg.upload["end"] = true
                        end
                        mqttc:publish(topic_up, (json.encode(msg)), 1)
                        if #data < 1024 then
                            break
                        end
                        index = index + 1
                    end
                end
            end
        end
    elseif linkmsg.action == "pong" then
        -- nop
    elseif linkmsg.action == "req" then
        -- Http请求,需要响应, 这部分属于用户自定义了
        if linkmsg.req == nil or linkmsg.req.uri == nil then
            return
        end
        log.info("h2o", "req", "id", linkmsg.req.id)
        if linkmsg.req.uri == "/api/netled/on" then
            if LED then LED(1) end
            return h2o_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/off" then
            if LED then LED(0) end
            return h2o_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/adc/vbat" then
            adc.open(11)
            local val = adc.get(11)
            adc.close(11)
            return h2o_resp_json(linkmsg.req.id, 200, nil, {vbat=val})
        elseif linkmsg.req.uri == "/api/adc/temp" then
            adc.open(10)
            local val = adc.get(10)
            adc.close(10)
            return h2o_resp_json(linkmsg.req.id, 200, nil, {temp=val})
        -- TODO sht30, ds18b20, tts语音输出?
        -- TODO 其他更好玩的东西
        end
        return h2o_resp(404)
    elseif linkmsg.action == "ws" then
        -- 通过WebSocket下发的数据,这个客户自行定义了
        if linkmsg.ws and linkmsg.ws.hi then
            return h2o_ws({hi=linkmsg.ws.hi})
        end
    end
end

sys.taskInit(function()
    if rtos.bsp() == "ESP32C3" then
        local ssid = "uiot"
        local password = "1234567890"
        LED = gpio.setup(12, 0, gpio.PULLUP)
        wlan.init()
        wlan.setMode(wlan.STATION)
        wlan.connect(ssid, password, 1)
        local result, data = sys.waitUntil("IP_READY")
        log.info("wlan", "IP_READY", result, data)
        device_id = wlan.getMac()
    elseif rtos.bsp() == "AIR105" then
        w5500.init(spi.HSPI_0, 24000000, pin.PC14, pin.PC01, pin.PC00)
        w5500.config() --默认是DHCP模式
        w5500.bind(socket.ETH0)
        LED = gpio.setup(62, 0, gpio.PULLUP)
        sys.wait(1000) 
    elseif rtos.bsp() == "EC618" then
        --mobile.simid(2)
        LED = gpio.setup(27, 0, gpio.PULLUP)
        device_id = mobile.imei()
    end

    topic_up = "$h2o/" .. device_id .. "/up"
    topic_down = "$h2o/" .. device_id .. "/down"

    log.info("h2o", "up", topic_up)
    log.info("h2o", "down", topic_down)

    mqttc = mqtt.create(nil, mqtt_host, mqtt_port, mqtt_isssl)
    mqttc:auth(mqtt_client_id, mqtt_username, mqtt_password)
    mqttc:keepalive(240) -- 默认值240s
    mqttc:autoreconn(true, 3000) -- 自动重连机制
    mqttc:on(
        function(mqtt_client, event, data, payload)
            if event == "conack" then
                sys.publish("mqtt_conack")
                log.info("mqtt", "mqtt已连接")
                mqtt_client:subscribe(topic_down, 1)
                -- TODO 上报conn信息
                local msg = {action="conn"}
                msg.conn = {files={}}
                local _, files = io.lsdir("/luadb/")
                for _, v in pairs(files) do
                    if v and v["type"] == 0 then
                        local name = v["name"]
                        if name:endsWith(".luac") or name:startsWith(".airm2m") then
                            -- nop
                        else
                            local sha1 = crypto.md_file("SHA1", "/luadb/" .. name)
                            local size = io.fileSize("/luadb/" .. name)
                            msg.conn.files[name] = {size=size, sha1=sha1}
                        end
                    end
                end
                mqtt_client:publish(topic_up, (json.encode(msg)), 1)
            elseif event == "recv" then
                log.info("mqtt", "收到消息", data, payload)
                local jdata = json.decode(payload)
                if jdata and jdata.action then
                    h2o_handle(jdata)
                else
                    log.info("h2o", "未知mqtt下发消息,忽略")
                end
            end
        end
    )
    mqttc:connect()
    sys.waitUntil("mqtt_conack", 15000)
    while true do
        local ret, topic, data, qos = sys.waitUntil("mqtt_pub", 30000)    
        if ret then
            if topic == "close" then
                break
            end
            if mqttc:ready() then
                mqttc:publish(topic, data, qos)
            end
        else
            if mqttc:ready() then
                mqttc:publish(topic_up, (json.encode({action="ping"})))
            end
        end
    end
    mqttc:close()
    mqttc = nil
end)

-- 用户代码已结束---------------------------------------------
-- 结尾总是这一句
sys.run()
-- sys.run()之后后面不要加任何语句!!!!!
