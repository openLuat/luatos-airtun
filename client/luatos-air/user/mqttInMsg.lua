--- 模块功能：MQTT客户端数据接收处理
-- @author openLuat
-- @module mqtt.mqttInMsg
-- @license MIT
-- @copyright openLuat
-- @release 2018.03.28

module(...,package.seeall)

require "socket"
require "pins"

local mqttC = nil
local LED = pins.setup(pio.P0_1,0)

function airtun_resp(id, code, headers, body)
    if mqttC then
        local msg = {action="resp"}
        if body then
            if type(body) == "table" then
                body = json.encode(body)
            elseif type(body) ~= "string" then
                body = tostring(body)
            end
            body = crypto.base64_encode(body, #body)
        end
        msg["resp"] = {id=id, code=code, headers=headers, body=body}
        local payload = json.encode(msg)
        log.debug("airtun", "resp", payload)
        if payload then
            mqttC:publish(_G.topic_up, payload, 1)
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
    if mqttC and socket:ready() then
        mqttC:publish(_G.topic_up, (json.encode({action="ws", ws=body})), 1)
    end
end

function airtun_handle(linkmsg)
    log.info("airtun", linkmsg.action)
    if linkmsg.action == "conack" then
        if linkmsg.conack and linkmsg.conack.files then
            for k, _ in pairs(linkmsg.conack.files) do
                local path = "/lua/" .. k
                local fd = io.open(path)
                if fd then
                    local index = 0
                    local msg = {action="upload", upload={name=k, size=io.fileSize(path)}}
                    while 1 do
                        local data = fd:read(1024)
                        if not data or #data == 0 then break end
                        msg.upload.body = crypto.base64_encode(data, #data)
                        msg.upload["index"] = index
                        if #data < 1024 then
                            msg.upload["end"] = true
                        end
                        mqttC:publish(_G.topic_up, (json.encode(msg)), 1)
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
        log.info("airtun", "req", "id", linkmsg.req.id)
        if linkmsg.req.uri == "/api/netled/on" then
            if LED then log.info("打开Net_Status 指示灯") LED(1) end
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
        elseif linkmsg.req.uri == "/api/netled/off" then
            if LED then log.info("关闭Net_Status 指示灯") LED(0) end
            return airtun_resp_json(linkmsg.req.id, 200, nil, {ok=true})
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

--- MQTT客户端数据接收处理
-- @param mqttClient，MQTT客户端对象
-- @return 处理成功返回true，处理出错返回false
-- @usage mqttInMsg.proc(mqttClient)
function proc(mqttClient)
    local result,data
    while true do
        mqttC = mqttClient
        result,data = mqttClient:receive(60000, "APP_SOCKET_SEND_DATA")
        --接收到数据
        if result then
            log.info("mqtt收到消息",data.topic,data.payload)
            local jdata = json.decode(data.payload)
            if jdata and jdata.action then
                airtun_handle(jdata)
            else
                log.info("airtun", "未知mqtt下发消息,忽略")
            end
            --TODO：根据需求自行处理data.payload
        else
            break
        end
    end
	
    return result or data=="timeout" or data=="APP_SOCKET_SEND_DATA"
end
