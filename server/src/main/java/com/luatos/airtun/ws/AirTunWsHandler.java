package com.luatos.airtun.ws;

import org.nutz.json.Json;
import org.nutz.lang.Strings;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.plugins.mvc.websocket.handler.AbstractWsHandler;

import com.luatos.airtun.AppCore;
import com.luatos.airtun.bean.LinkMessage;

public class AirTunWsHandler extends AbstractWsHandler {
	
	protected static final Log log = Logs.get();

	protected String clientId;
	protected AppCore app;

	public AirTunWsHandler(String clientId, AppCore app) {
		super("");
		this.clientId = clientId;
		this.app = app;
	}

	// 发送数据到单个或全部客户端
//	public void sendc(NutMap req) {
//		LinkMessage msg = new LinkMessage("ws");
//		msg.ws = new LinkWs();
//		msg.ws.data = req;
//		app.publish2client(clientId, msg);
//	}

	@Override
	public void onMessage(String message) {
		if (Strings.isBlank(message))
			return;
		message = message.trim();
		if (message.startsWith("{") && message.endsWith("}")) {
			try {
				LinkMessage msg = Json.fromJson(LinkMessage.class, message);
				app.messageArrived(clientId, msg);
			}
			catch (Exception e) {
				log.info("bad message", e);
			}
		}
	}
}
