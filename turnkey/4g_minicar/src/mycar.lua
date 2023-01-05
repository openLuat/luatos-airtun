

local mycar = {}

-- sys库是标配
_G.sys = require("sys")
-- _G.sysplus = require("sysplus")

-- 根据自己的服务器修改以下参数
-- local mqtt_host = "broker-cn.emqx.io"
local mqtt_host = "mqtt.air32.cn"
local mqtt_port = 1883
local mqtt_isssl = false

local mqtt_client_id = mcu.unique_id():toHex()
local mqtt_username = mcu.unique_id():toHex()
local mqtt_password = "1234567890"
local device_id = mcu.unique_id():toHex()

local mqttc = nil
local LED = nil
local LEDA = nil
local topic_down = nil
local topic_up = nil

function airtun_resp(id, code, headers, body)
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
        log.debug("airtun", "resp", payload)
        if payload then
            mqttc:publish(topic_up, payload, 1)
        end
    end
end


function airtun_resp_json(id, code, headers, body)
    headers = headers or {}
    headers["Content-Type"] = "appilcation/json"
    body = json.encode(body)
    airtun_resp(id, code, headers, body)
end

function airtun_ws(body)
    if mqttc and mqttc:ready() then
        mqttc:publish(topic_up, (json.encode({action="ws", ws=body})), 1)
    end
end

function airtun_handle(linkmsg)
    --log.info("airtun", "action", linkmsg.action)
    if linkmsg.action == "conack" then
        if linkmsg.conack and linkmsg.conack.files then
            for k, _ in pairs(linkmsg.conack.files) do
                local path = "/luadb/" .. k
                local fd = io.open(path)      --LUA读取文件并异步传输
                if fd then
                    local index = 0
                    local msg = {action="upload", upload={name=k, size=io.fileSize(path)}}
                    while 1 do
                        local data = fd:read(256)
                        if not data or #data == 0 then break end
                        msg.upload.body = data:toBase64()
                        msg.upload["index"] = index
                        if #data < 256 then
                            msg.upload["end"] = true
                        end
                        mqttc:publish(topic_up, (json.encode(msg)), 1)
                        if #data < 256 then
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
        log.info("airtun", "req", "id", linkmsg.req.id)
        if linkmsg.req.uri == "/api/netled/up1" then
            if LED then
                G18(1)
                G19(0)
                G1(1)
                G24(0)


            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/up0" then
            if LED then
                G18(0)
                G19(0)
                G1(0)
                G24(0)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})

-------------车
        elseif linkmsg.req.uri == "/api/netled/left1" then
            if LED then
                G18(1)
                G19(0)
                G1(1)
                G24(1)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/left0" then
            if LED then
                G18(0)
                G19(0)
                G1(0)
                G24(0)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/right1" then
            if LED then
                G18(1)
                G19(1)
                G1(1)
                G24(0)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})


        elseif linkmsg.req.uri == "/api/netled/right0" then
            if LED then
                R0()
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/down1" then
            if LED then
                G18(0)
                G19(1)
                G1(0)
                G24(1)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/down0" then
            if LED then
                G18(0)
                G19(0)
                G1(0)
                G24(0)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})

        elseif linkmsg.req.uri == "/api/netled/stop1" then
            if LED then
                G18(1)
                G19(1)
                G1(1)
                G24(1)
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})

        elseif linkmsg.req.uri == "/api/netled/stop0" then    --松开刹车
            if LED then
            R0()
            end
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})

        elseif linkmsg.req.uri == "/api/netled/auto1" then
            G18(1)
            G19(0)
            G1(1)
            G24(0)
            log.info("小车自动巡航")
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})


        elseif linkmsg.req.uri == "/api/netled/auto0" then
            R0()
            log.info("小车停止巡航")
            LOG()
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})

-------------

        elseif linkmsg.req.uri == "/api/adc/vbat" then
            adc.open(11)
            local val = adc.get(11)
            adc.close(11)
            return airtun_resp_json(linkmsg.req.id, 200, nil, {vbat=val})
        elseif linkmsg.req.uri == "/api/adc/temp" then
            adc.open(10)
            local val = adc.get(10)
            adc.close(10)
            return airtun_resp_json(linkmsg.req.id, 200, nil, {temp=val})
        -- TODO sht30, ds18b20, tts语音输出?
        -- TODO 其他更好玩的东西
        end
        return airtun_resp(404)
    elseif linkmsg.action == "ws" then
        -- 通过WebSocket下发的数据,这个客户自行定义了
        if linkmsg.ws and linkmsg.ws.hi then
            return airtun_ws({hi=linkmsg.ws.hi})
        end
    end
end

sys.taskInit(function()
    if rtos.bsp():startsWith("ESP32") then
        local ssid = "uiot123"
        local password = "12348888"
        log.info("wifi", ssid, password)
        -- TODO 改成esptouch配网
        LED = gpio.setup(12, 0, gpio.PULLUP)

        wlan.init()
        wlan.setMode(wlan.STATION)
        wlan.connect(ssid, password, 1)
        local result, data = sys.waitUntil("IP_READY", 30000)
        log.info("wlan", "IP_READY", result, data)
        device_id = wlan.getMac()
    elseif rtos.bsp() == "AIR105" then
        w5500.init(spi.HSPI_0, 24000000, pin.PC14, pin.PC01, pin.PC00)
        w5500.config() --默认是DHCP模式
        w5500.bind(socket.ETH0)
        LED = gpio.setup(62, 0, gpio.PULLUP)
        sys.wait(1000)
        -- TODO 获取mac地址作为device_id
    elseif rtos.bsp() == "EC618" then
        --mobile.simid(2)


        --电机A是gpio8,10  电机B是gpio9,11

        LED = gpio.setup(27, 0, gpio.PULLUP)        --板载绿灯
        G18 = gpio.setup(18, 0, gpio.PULLUP)        --PWM软件通道14
        G19 = gpio.setup(19, 0, gpio.PULLUP)
        G1 = gpio.setup(1, 0, gpio.PULLUP)          --PWM软件通道10
        G24 = gpio.setup(24, 0, gpio.PULLUP)
    function   R0() --松开时的函数，关闭所有信号
        G18(0)
        G19(0)
        G1(0)
        G24(0)
    end

    function   LOG() --每次按键松键输出信息
        log.info("当前gpio18电平是", gpio.get(18))
        log.info("当前gpio19电平是", gpio.get(19))
        log.info("当前gpio1电平是", gpio.get(1))
        log.info("当前gpio24电平是", gpio.get(24))
    end

        device_id = mobile.imei()
        sys.waitUntil("IP_READY", 30000)
sys.wait(10000)
    end

    device_id = device_id:lower()
    topic_up = "$airtun/" .. device_id .. "/up"
    topic_down = "$airtun/" .. device_id .. "/down"

    log.info("airtun", "topic up", topic_up)
    log.info("airtun", "topci down", topic_down)
    log.info("airtun", "===========================================")
    log.info("airtun", "Pls open https://" .. device_id .. ".airtun.air32.cn")
    log.info("airtun", "===========================================")

    mqttc = mqtt.create(nil, mqtt_host, mqtt_port, mqtt_isssl)
    mqttc:auth(mqtt_client_id, mqtt_username, mqtt_password)
    mqttc:keepalive(240) -- 默认值240s
    mqttc:autoreconn(true, 3000) -- 自动重连机制
    mqttc:on(
        function(mqtt_client, event, data, payload)
            if event == "conack" then
                sys.publish("mqtt_conack")
                log.info("mqtt", "mqtt已连接")
                LED(1)
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
                    airtun_handle(jdata)
                else
                    log.info("airtun", "未知mqtt下发消息,忽略")
                end
            end
        end
    )
    mqttc:connect()
    sys.waitUntil("mqtt_conack", 15000)
    while true do
        local ret, topic, data, qos = sys.waitUntil("mqtt_pub", 60000)
        if ret then
            if topic == "close" then
                break
            end
            if mqttc:ready() then
                mqttc:publish(topic, data, qos)
            end
        else
            if mqttc:ready() then
                local data = (json.encode({action="ping"}))
                --log.info("airtun", "ping Go", topic_up, data)
                mqttc:publish(topic_up, data, 1)
            end
        end
    end
    mqttc:close()
    mqttc = nil
end)

return mycar
